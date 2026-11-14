package com.coparently.app.data.session

import com.coparently.app.data.local.CoPlanlyDatabase
import com.coparently.app.data.local.deleteEveryRow
import com.coparently.app.data.local.preferences.PreferenceKeys
import com.coparently.app.data.platform.SecurePreferences
import com.coparently.app.data.platform.logger
import com.coparently.app.data.remote.firebase.AuthSession
import com.coparently.app.data.remote.firebase.CallableFunctions
import com.coparently.app.data.remote.firebase.PushRegistration
import com.coparently.app.domain.notification.ReminderScheduler
import com.coparently.app.domain.notification.RequestClockScheduler
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.withContext

/**
 * Erases the signed-in account from the iPhone (MON-24 phase 3, part B): everything the server
 * holds, then everything this phone does. The iPhone's [AccountDeletion], as the app's
 * `AccountDeletionService` is Android's, step for step and in the same order; the App Store, like
 * Google Play, requires an app where an account can be created to offer its deletion in the app.
 *
 * **The server first, through the `deleteAccount` callable.** It erases the account's documents on
 * Admin credentials and deletes the Auth user last, so a partial failure leaves an account that can
 * still sign in and retry. Nothing here touches the server any other way.
 *
 * **The local half only after the server reports success.** Room survives sign-out so a returning
 * parent keeps their history, and the account switch wipes only when a *different* account signs
 * in, so a parent who deletes their account and never signs in again would otherwise leave the whole
 * database on the phone. Clearing first would destroy the data even on a run that failed and has to
 * be retried, so a failure deletes nothing here and is returned; a cancellation is rethrown, since a
 * call that was cancelled may still have succeeded on the server.
 *
 * The wipe is Android's: the push registration detached, every row, the event reminders and the
 * request clocks' jobs, the sealed preference store and both school connections' keys (which its
 * `clear()` keeps, CLAUDE.md item 35), and the account's files outside the database — the strong
 * [IosAccountTraces.clearAccount], the chat outbox included, as Android's `DeviceTraces.clearAccount`,
 * because the messages those files belong to are gone with the rows. Then, unlike Android, this
 * phone's Firebase Auth session is ended ([AuthSession.signOutCompletely]): the iOS SDK keeps the
 * deleted user in the Keychain until it is told otherwise, and the next screen is the sign-in.
 *
 * @property functions the callables, in `FUNCTIONS_REGION`
 * @property database the rows to clear
 * @property preferences the sealed store to clear
 * @property push the push registration to detach (nothing to detach until the iPhone holds a token)
 * @property reminders the event reminders to cancel
 * @property clocks the request clocks' jobs to cancel
 * @property traces the account's files outside the database
 * @property authSession the session to end once everything else is gone
 */
@Suppress("LongParameterList") // one collaborator per piece of the account this phone holds, as on Android
class IosAccountDeletion(
    private val functions: CallableFunctions,
    private val database: CoPlanlyDatabase,
    private val preferences: SecurePreferences,
    private val push: PushRegistration,
    private val reminders: ReminderScheduler,
    private val clocks: RequestClockScheduler,
    private val traces: IosAccountTraces,
    private val authSession: AuthSession,
) : AccountDeletion {

    /**
     * Deletes the account and wipes this phone.
     *
     * @return success once the server has erased the account and the local data is clear; the
     *   callable's failure otherwise, with nothing deleted here.
     */
    override suspend fun deleteAccount(): Result<Unit> = try {
        functions.call(CALLABLE, emptyMap())
        wipeLocalData()
        Result.success(Unit)
    } catch (e: CancellationException) {
        // Structured cancellation must keep unwinding: a cancelled call is not a failed deletion,
        // and reporting it as one would offer a retry for work that may already have succeeded.
        throw e
    } catch (
        @Suppress("TooGenericExceptionCaught") e: Exception
    ) {
        logger.e(TAG, "Account deletion failed", e)
        Result.failure(e)
    }

    private suspend fun wipeLocalData() {
        // The profile document is already gone, so only the local half of this can succeed.
        push.unregisterToken()
        // Room KMP has no `clearAllTables`; see `deleteEveryRow`.
        withContext(Dispatchers.IO) { database.deleteEveryRow() }
        // Nothing is left for a pending event reminder to announce, nor for a request clock's step.
        reminders.cancelAll()
        clocks.cancelAll()
        preferences.clear()
        // Kept by `clear()` on purpose (see `PreferenceKeys.SCHOOL_CONNECTION_PREFIX`); a deleted
        // account's school tokens and passwords have no business staying on the phone.
        preferences.removeWithPrefix(PreferenceKeys.SCHOOL_CONNECTION_PREFIX)
        preferences.removeWithPrefix(PreferenceKeys.EDUPAGE_CONNECTION_PREFIX)
        // The plain copies of the account's files, the chat outbox's included.
        traces.clearAccount()
        // The server deleted the Auth user; this phone still holds its session until told.
        authSession.signOutCompletely()
    }

    private companion object {
        const val TAG = "IosAccountDeletion"
        const val CALLABLE = "deleteAccount"
    }
}
