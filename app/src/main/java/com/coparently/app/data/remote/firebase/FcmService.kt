package com.coparently.app.data.remote.firebase

import com.coparently.app.data.local.preferences.EncryptedPreferences
import com.coparently.app.data.local.preferences.PreferenceKeys
import com.coparently.app.domain.family.FamilyKey
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.messaging.FirebaseMessaging
import com.google.gson.Gson
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Service for managing Firebase Cloud Messaging tokens and notifications.
 * Handles token registration, updates, and notification payload creation.
 */
@Singleton
class FcmService @Inject constructor(
    private val firebaseMessaging: FirebaseMessaging,
    private val firestore: FirebaseFirestore,
    private val firebaseAuthService: FirebaseAuthService,
    private val encryptedPreferences: EncryptedPreferences
) {
    private val gson = Gson()

    /** Whether this person has left push notifications on. On unless they switched it off. */
    fun isPushEnabled(): Boolean =
        encryptedPreferences.getString(PreferenceKeys.PUSH_ENABLED, null) != false.toString()

    /**
     * Records the Settings switch and makes it true on the server: off detaches this device's
     * token (see [unregisterToken]) so the co-parent's pushes stop arriving, on registers it
     * again. [updateUserToken] refuses to register while it is off, which is what keeps app start
     * and a token refresh from quietly undoing the choice.
     *
     * @return failure when turning it on could not register a token; turning it off is
     *   best-effort, like sign-out, and always succeeds locally.
     */
    suspend fun setPushEnabled(enabled: Boolean): Result<Unit> {
        encryptedPreferences.putString(PreferenceKeys.PUSH_ENABLED, enabled.toString())
        if (!enabled) {
            unregisterToken()
            return Result.success(Unit)
        }
        val token = getCurrentToken()
            ?: return Result.failure(IllegalStateException("No FCM token available"))
        return updateUserToken(token)
    }

    /**
     * Gets the current FCM token.
     *
     * @return The FCM token or null if unavailable
     */
    suspend fun getCurrentToken(): String? {
        return try {
            firebaseMessaging.token.await()
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Updates the FCM token for the current user in Firestore.
     *
     * @param token The FCM token to save
     */
    suspend fun updateUserToken(token: String): Result<Unit> {
        // Switched off in Settings: nothing re-attaches this device behind the person's back.
        if (!isPushEnabled()) return Result.success(Unit)
        return try {
            val currentUser = firebaseAuthService.getCurrentUser() ?: return Result.failure(
                IllegalStateException("User not authenticated")
            )

            // Use set with merge to create document if it doesn't exist
            firestore.collection("users")
                .document(currentUser.uid)
                .set(mapOf("fcmToken" to token), com.google.firebase.firestore.SetOptions.merge())
                .await()

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Subscribes the current user to a topic for receiving notifications.
     *
     * @param topic The topic to subscribe to
     */
    suspend fun subscribeToTopic(topic: String): Result<Unit> {
        return try {
            firebaseMessaging.subscribeToTopic(topic).await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Unsubscribes the current user from a topic.
     *
     * @param topic The topic to unsubscribe from
     */
    suspend fun unsubscribeFromTopic(topic: String): Result<Unit> {
        return try {
            firebaseMessaging.unsubscribeFromTopic(topic).await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * The payload for an event being created, updated or deleted.
     *
     * Facts, not sentences (SEC-3). This used to build `"New Event: $eventTitle"` and
     * `"$performedBy created an event"` here and hand them to the other phone to render
     * verbatim — English regardless of the reader's language, and forgeable, because nothing
     * between the two devices decided what a notification was allowed to say.
     * [CoPlanlyMessagingService] now writes the sentence from its own string resources.
     *
     * @param eventId The event, for the deep link
     * @param eventTitle The event's title, as the co-parent typed it
     * @param action `created`, `updated` or `deleted`
     * @param performedBy Display name of the parent who did it
     * @return The payload, or null when [action] is one nothing announces — see
     *   [PushPayload.eventType].
     */
    fun createEventNotificationPayload(
        eventId: String,
        eventTitle: String,
        action: String,
        performedBy: String
    ): Map<String, String>? {
        val type = PushPayload.eventType(action) ?: return null
        return mapOf(
            PushPayload.TYPE to type,
            PushPayload.EVENT_ID to eventId,
            PushPayload.SUBJECT to eventTitle,
            PushPayload.ACTOR to performedBy
        )
    }

    /**
     * The payload for a child's information being updated. Facts, not sentences — see
     * [createEventNotificationPayload].
     *
     * @param childInfoId The record, for the deep link
     * @param childName The child's name
     * @param updatedBy Display name of the parent who made the change
     */
    fun createChildInfoNotificationPayload(
        childInfoId: String,
        childName: String,
        updatedBy: String
    ): Map<String, String> = mapOf(
        PushPayload.TYPE to PushPayload.CHILD_INFO_UPDATED,
        PushPayload.CHILD_INFO_ID to childInfoId,
        PushPayload.SUBJECT to childName,
        PushPayload.ACTOR to updatedBy
    )

    // `createInvitationNotificationPayload` was deleted with SEC-3: it had no caller. An
    // invitation being accepted is announced by `acceptPairingInvitation` in
    // `functions/index.js`, which is where it has to be — the accepting device is not the one
    // that needs telling.

    /**
     * Sends notification data to Firestore for Cloud Functions to process.
     * This creates a document in a notifications queue that triggers a Cloud Function.
     *
     * Every push is stamped here with the family it belongs to (M-8), so the tap on the other
     * phone can switch to that family before it opens anything. Stamped in this one place rather
     * than by each of the payload builders because the answer never depends on the payload: a
     * push goes from this parent to one co-parent, and a pair *is* a family
     * ([FamilyKey.orNull]). A push to oneself names no family and carries no key, which is also
     * what an older build sends; `firestore.rules` accepts only a family both uids are in.
     *
     * @param targetUserId The Firebase UID of the user to notify
     * @param notificationData The notification payload
     */
    suspend fun queueNotificationForUser(
        targetUserId: String,
        notificationData: Map<String, String>
    ): Result<Unit> {
        return try {
            val familyId = FamilyKey.orNull(firebaseAuthService.getCurrentUser()?.uid, targetUserId)
            val stamped = if (familyId == null || PushPayload.FAMILY_ID in notificationData) {
                notificationData
            } else {
                notificationData + (PushPayload.FAMILY_ID to familyId)
            }
            val notificationDoc = mapOf(
                "targetUserId" to targetUserId,
                "data" to stamped,
                "createdAt" to System.currentTimeMillis(),
                "status" to "pending"
            )

            firestore.collection("notification_queue")
                .add(notificationDoc)
                .await()

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Detaches this device's token from the signed-in account, for sign-out and deletion.
     *
     * A token identifies a *device*, and nothing used to take it back: [updateUserToken] wrote
     * the same token under whichever uid was signed in, so after a sign-out the previous
     * account's document still named this phone, and every push addressed to that account — a
     * co-parent's chat, with its text — kept arriving on it, to be shown to whoever signed in
     * next. Both halves matter: the field on the document stops the queue addressing this
     * device, and deleting the token invalidates it wherever else it may still be stored.
     *
     * Best-effort, like every other network step of a sign-out: the account is leaving whether
     * or not the network is there, and `CoPlanlyMessagingService` refuses a push addressed to
     * anybody but the signed-in uid regardless.
     */
    suspend fun unregisterToken() {
        val uid = firebaseAuthService.getCurrentUser()?.uid ?: return
        runCatching {
            firestore.collection("users").document(uid)
                .update("fcmToken", com.google.firebase.firestore.FieldValue.delete())
                .await()
        }.onFailure { android.util.Log.w("FcmService", "Could not detach the FCM token", it) }
        runCatching { firebaseMessaging.deleteToken().await() }
            .onFailure { android.util.Log.w("FcmService", "Could not delete the FCM token", it) }
    }
}
