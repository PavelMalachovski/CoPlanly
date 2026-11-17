package com.coparently.app.data.remote.firebase

import com.coparently.app.data.local.preferences.PreferenceKeys
import com.coparently.app.data.platform.SecurePreferences
import com.coparently.app.data.platform.logger
import kotlinx.coroutines.CancellationException

/**
 * The iPhone's push registration (iPhone phase 4 part C, spec §7.2), in place of the stand-in
 * `NoPush`: the Settings switch ([PushRegistration]) and this device's token as the sync keeps it
 * on the profile ([PushTokenKeeper], through [FirestorePushQueue]), the two halves Android's
 * `FcmService` answers, with its semantics:
 * - the switch is on unless the parent switched it off, stored under the same key
 *   ([PreferenceKeys.PUSH_ENABLED]); on the iPhone it also needs the system's permission
 *   ([NotificationPermission.granted]), which Android's Settings asks of its screen instead;
 * - turning it on asks for the permission (the system asks only once; a "no" stays a no), reads the
 *   token and writes it as `users/{uid}.fcmToken`, merged, as `FcmService.updateUserToken` does;
 *   a refusal or no token is a failure, which Settings shows;
 * - the token is kept only while the switch is on and the permission given ([keepToken]), so the
 *   app's start and a token refresh never undo the parent's choice;
 * - turning it off, signing out and deleting the account detach the token ([unregisterToken]):
 *   the profile's field deleted, then the token itself at Firebase Messaging, each best-effort.
 *   One token per account (spec §2, decision 1): the phone that registered last is pushed.
 *
 * @property firestore Where the profile's `fcmToken` is written and deleted.
 * @property signedInUser Whose profile.
 * @property preferences Where the switch is stored, as on Android.
 * @property token The app's Firebase Messaging token (the Swift side's `PushTokenBridge`).
 * @property permission The system's notification permission.
 */
class IosPushRegistration(
    private val firestore: FirestoreClient,
    private val signedInUser: SignedInUser,
    private val preferences: SecurePreferences,
    private val token: IosPushToken,
    private val permission: NotificationPermission,
) : PushRegistration, PushTokenKeeper {

    /** The iPhone registers with Apple's service: the switch is offered. */
    override val available: Boolean = true

    /** On unless the parent switched it off, and only while the system lets the app notify. */
    override fun isPushEnabled(): Boolean = switchOn() && permission.granted

    override suspend fun setPushEnabled(enabled: Boolean): Result<Unit> {
        preferences.putString(PreferenceKeys.PUSH_ENABLED, enabled.toString())
        if (!enabled) {
            unregisterToken()
            return Result.success(Unit)
        }
        return register()
    }

    /** Asks for the permission, then keeps the token; a refusal or no token is a failure. */
    private suspend fun register(): Result<Unit> {
        if (!permission.request()) return Result.failure(IllegalStateException("Notifications are not allowed"))
        val current = token.awaitCurrent() ?: return Result.failure(IllegalStateException("No push token available"))
        return keepToken(current)
    }

    override suspend fun currentToken(): String? = token.awaitCurrent()

    /**
     * Writes [token] as the signed-in account's `fcmToken` (merged), unless the switch is off or the
     * system does not let the app notify: a token for a phone that shows nothing would only have
     * the server push into silence. The permission is read afresh ([NotificationPermission.refresh]),
     * since the parent may have changed it in the phone's Settings.
     */
    override suspend fun keepToken(token: String): Result<Unit> {
        if (!switchOn() || !permission.refresh()) return Result.success(Unit)
        val uid = signedInUser.getCurrentUid()
            ?: return Result.failure(IllegalStateException("User not authenticated"))
        return try {
            firestore.set(DocumentPath(USERS, uid), mapOf(FCM_TOKEN to token), SetMerge.Merge)
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (
            @Suppress("TooGenericExceptionCaught") e: Exception
        ) {
            Result.failure(e)
        }
    }

    /**
     * Deletes the signed-in account's `fcmToken`, then this device's token at Firebase Messaging,
     * as `FcmService.unregisterToken` does, and in its order; nothing while nobody is signed in.
     * Best-effort: never throws (but for a cancellation), and the step that failed is logged.
     */
    override suspend fun unregisterToken() {
        val uid = signedInUser.getCurrentUid() ?: return
        try {
            firestore.update(DocumentPath(USERS, uid), FCM_TOKEN, ServerValue.Delete)
        } catch (e: CancellationException) {
            throw e
        } catch (
            @Suppress("TooGenericExceptionCaught") e: Exception
        ) {
            logger.w(TAG, "Could not detach the push token", e)
        }
        token.awaitDelete()
    }

    private fun switchOn(): Boolean = preferences.getString(PreferenceKeys.PUSH_ENABLED, null) != false.toString()

    private companion object {
        const val TAG = "IosPushRegistration"
        const val USERS = "users"
        const val FCM_TOKEN = "fcmToken"
    }
}
