package com.coparently.app.data.remote.firebase

import com.coparently.app.domain.family.FamilyKey
import kotlinx.coroutines.CancellationException
import kotlin.time.Clock

/**
 * The iPhone's [PushDevice] (MON-24 phase 3, Task 6): the co-parent is told through the same
 * `notification_queue` the Cloud Function sends from, with the document the app's
 * `FcmService.queueNotificationForUser` writes, key for key and type for type:
 * - `targetUserId`, the addressee;
 * - `data`, the payload — a type and its facts, never a sentence (CLAUDE.md item 15) — stamped with
 *   the pair's family ([PushPayload.FAMILY_ID]) unless it names one already;
 * - `createdAt`, this phone's clock in epoch milliseconds, a `Long`;
 * - `status`, "pending";
 * - and nothing else.
 *
 * `firestore.rules` validates the create (the addressee is the writer or a live co-parent, a type
 * the client may produce, no `title` or `body`, every free-text key bounded, and the family one
 * both are in), so this writes no key of its own. A new id is made on the phone and the document
 * written whole, which is the create `collection.add()` makes on Android.
 *
 * The token is [tokens]' (iPhone phase 4 part C: `IosPushRegistration`, over Apple's service and
 * Firebase Messaging), which the sync reads ([getCurrentToken]) and keeps on the profile
 * ([updateUserToken]) as it does Android's. Without one, [getCurrentToken] answers null and
 * [updateUserToken] writes nothing: the sync then leaves the profile's `fcmToken` as it was.
 * Android keeps `FcmService`.
 *
 * @property firestore Where the queue document is written.
 * @property signedInUser Who sends it: the family is the sender's with the addressee.
 * @property tokens This device's push token, or null where there is none to keep.
 */
class FirestorePushQueue(
    private val firestore: FirestoreClient,
    private val signedInUser: SignedInUser,
    private val tokens: PushTokenKeeper? = null,
) : PushDevice {

    /**
     * Queues [notificationData] for [targetUserId], as `FcmService` does. Best-effort: a failure is
     * returned, never thrown, because the change it announces is already saved.
     */
    override suspend fun queueNotificationForUser(
        targetUserId: String,
        notificationData: Map<String, String>
    ): Result<Unit> = try {
        val familyId = FamilyKey.orNull(signedInUser.getCurrentUid(), targetUserId)
        val stamped = if (familyId == null || PushPayload.FAMILY_ID in notificationData) {
            notificationData
        } else {
            notificationData + (PushPayload.FAMILY_ID to familyId)
        }
        val document = mapOf(
            "targetUserId" to targetUserId,
            "data" to stamped,
            "createdAt" to Clock.System.now().toEpochMilliseconds(),
            "status" to "pending"
        )
        firestore.set(DocumentPath(COLLECTION, firestore.newDocumentId(COLLECTION)), document)
        Result.success(Unit)
    } catch (e: CancellationException) {
        throw e
    } catch (
        @Suppress("TooGenericExceptionCaught") e: Exception
    ) {
        Result.failure(e)
    }

    /** This device's push token from [tokens], or null without them. */
    override suspend fun getCurrentToken(): String? = tokens?.currentToken()

    /** Keeps [token] on the profile through [tokens]; without them nothing is written and nothing fails. */
    override suspend fun updateUserToken(token: String): Result<Unit> =
        tokens?.keepToken(token) ?: Result.success(Unit)

    private companion object {
        /** The queue the Cloud Function `sendNotification` reads. */
        const val COLLECTION = "notification_queue"
    }
}
