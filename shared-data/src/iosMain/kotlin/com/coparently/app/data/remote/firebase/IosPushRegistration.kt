package com.coparently.app.data.remote.firebase

import com.coparently.app.data.local.preferences.PreferenceKeys
import com.coparently.app.data.platform.SecurePreferences
import com.coparently.app.data.platform.logger
import kotlinx.coroutines.CancellationException
import kotlin.concurrent.Volatile

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
 * - **a detach sticks until a new session** ([detachedUid]): sign-out and deletion detach first and
 *   end the session last, and Firebase Messaging may hand out a fresh token in between (a refresh,
 *   or the sync's read); [keepToken] refuses to write it for the uid it detached, which would put a
 *   push token back on the departing profile, or re-create a deleted one by its merge. The record
 *   is cleared when the signed-in uid changes ([sessionChanged], which `IosApp` follows, a
 *   sign-out's null included) and when the parent turns pushes on again;
 * - nothing is read or written while nobody is signed in.
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

    /** The uid whose token [unregisterToken] detached, until [sessionChanged] says it left. */
    @Volatile
    private var detachedUid: String? = null

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

    /**
     * The signed-in uid is now [uid] (`IosApp` calls it for every value `observeCurrentUid` emits):
     * a value other than the uid a detach was made for, a sign-out's null included, ends that
     * detach, so the next session registers again.
     */
    fun sessionChanged(uid: String?) {
        if (uid != detachedUid) detachedUid = null
    }

    /** Asks for the permission, then keeps the token; a refusal or no token is a failure. */
    private suspend fun register(): Result<Unit> {
        // The parent asked for pushes on this account again: a detach made for it no longer holds.
        detachedUid = null
        if (!permission.request()) return Result.failure(IllegalStateException("Notifications are not allowed"))
        val current = token.awaitCurrent() ?: return Result.failure(IllegalStateException("No push token available"))
        return keepToken(current)
    }

    /**
     * This device's token, or null without asking Firebase Messaging while it would not be kept:
     * nobody signed in, the switch off, the account's token just detached, or no permission.
     */
    override suspend fun currentToken(): String? {
        if (accountToKeep() == null) return null
        return token.awaitCurrent()
    }

    /**
     * Writes [token] as the signed-in account's `fcmToken` (merged), unless the switch is off or the
     * system does not let the app notify: a token for a phone that shows nothing would only have
     * the server push into silence. The permission is read afresh ([NotificationPermission.refresh]),
     * since the parent may have changed it in the phone's Settings. Quietly nothing, too, while
     * nobody is signed in or for the uid whose token was just detached ([detachedUid]).
     */
    override suspend fun keepToken(token: String): Result<Unit> {
        val uid = accountToKeep() ?: return Result.success(Unit)
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
        // First, so a token handed out while the field is deleted is not written back for [uid].
        detachedUid = uid
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

    /**
     * The signed-in uid when this device's token is to be kept on its profile now, else null: someone
     * is signed in, their token was not just detached ([detachedUid]), the switch is on and the
     * system lets the app notify (read afresh, [NotificationPermission.refresh]).
     */
    private suspend fun accountToKeep(): String? {
        val uid = signedInUser.getCurrentUid()?.takeIf { it != detachedUid } ?: return null
        return uid.takeIf { switchOn() && permission.refresh() }
    }

    private fun switchOn(): Boolean = preferences.getString(PreferenceKeys.PUSH_ENABLED, null) != false.toString()

    private companion object {
        const val TAG = "IosPushRegistration"
        const val USERS = "users"
        const val FCM_TOKEN = "fcmToken"
    }
}
