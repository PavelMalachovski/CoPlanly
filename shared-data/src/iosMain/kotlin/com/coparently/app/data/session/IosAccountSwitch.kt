package com.coparently.app.data.session

import com.coparently.app.data.local.CoPlanlyDatabase
import com.coparently.app.data.local.preferences.PreferenceKeys
import com.coparently.app.data.platform.SecurePreferences
import com.coparently.app.data.platform.logger
import com.coparently.app.data.remote.firebase.SignedInUser
import com.coparently.app.domain.notification.ReminderScheduler
import com.coparently.app.domain.notification.RequestClockScheduler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import platform.Foundation.NSUserDefaults

/**
 * Wipes what the previous account left on the iPhone when a *different* account signs in (MON-24
 * phase 3, Task 5): the iPhone's [AccountConsistency], as the app's `AccountSwitchGuard` is Android's,
 * step for step and in the same order.
 *
 * The database survives sign-out, so a returning parent's history is still there. What must not
 * survive is a switch: nothing in the schema scopes rows to an account, so after A signs out and B
 * signs in, B's screens would read A's rows, and the sync would upload A's unsynced rows under B's
 * uid. On a uid change the local data is cleared and the new account sees only what the cloud holds
 * for it; unsynced rows of the old account are knowingly lost (the owner's decision on Android).
 *
 * The last uid seen lives in [LastSignedInUid], outside the sealed preference store: it is not a
 * secret, it gates a wipe, and `SecurePreferences.clear()` runs on sign-out, which would erase the
 * very memory this check needs at the next sign-in.
 *
 * [ensureAccountConsistency] is called where Android calls it: `SessionProfileSynchronizer`'s
 * auth-state collector (before the profile is written) and the top of `SyncService.performFullSync`
 * (before anything uploads). Both are started on the iPhone with the sync (Task 6 of phase 3).
 *
 * @property database the rows to clear
 * @property auth who is signed in now
 * @property preferences the sealed store to clear, school connections included
 * @property reminders the event reminders to cancel
 * @property clocks the request clocks' jobs to cancel
 * @property traces the account's files outside the database
 * @property lastUid the last account this phone saw
 */
@Suppress("LongParameterList") // Android's guard takes six and reads the last uid through its Context
class IosAccountSwitch(
    private val database: CoPlanlyDatabase,
    private val auth: SignedInUser,
    private val preferences: SecurePreferences,
    private val reminders: ReminderScheduler,
    private val clocks: RequestClockScheduler,
    private val traces: IosAccountTraces,
    private val lastUid: LastSignedInUid,
) : AccountConsistency {
    private val mutex = Mutex()

    /**
     * Compares the signed-in uid with the last one seen on this phone and wipes when they differ.
     * Nothing to do while signed out (sign-out keeps the data), on the same account, or on the
     * first account the phone sees. The mutex keeps the two callers from wiping twice.
     */
    override suspend fun ensureAccountConsistency() {
        val uid = auth.getCurrentUid() ?: return
        mutex.withLock {
            val previous = lastUid.get()
            if (previous != null && previous != uid) {
                logger.i(TAG, "Different account signed in; clearing local data")
                withContext(Dispatchers.IO) { database.clearAllTables() }
                // Its event reminders, which would otherwise wake for rows that are gone.
                reminders.cancelAll()
                // Its request clocks' steps, so none acts for the next account.
                clocks.cancelAll()
                // The Google Calendar credential, the cached split and the sync cursors; the per-uid
                // parent-slot markers survive `clear()` by design.
                preferences.clear()
                // The school connections survive `clear()`, so they go explicitly.
                preferences.removeWithPrefix(PreferenceKeys.SCHOOL_CONNECTION_PREFIX)
                // And the plain copies of its files, the chat outbox's included.
                traces.clearAccount()
            }
            if (previous != uid) lastUid.set(uid)
        }
    }

    private companion object {
        const val TAG = "IosAccountSwitch"
    }
}

/**
 * The last account signed in on this phone, kept where sign-out does not reach and an uninstall
 * does: what [IosAccountSwitch] compares the next sign-in with.
 */
interface LastSignedInUid {

    /** The last uid seen, or null on a phone where nobody has signed in since the install. */
    fun get(): String?

    /** Remembers [uid] as the last one seen. */
    fun set(uid: String)
}

/**
 * [LastSignedInUid] in `NSUserDefaults` under [KEY], as Android keeps it in a plain preference
 * file: not secret, and deleted with the app (the Keychain, where the sealed store lives, is not).
 */
internal object UserDefaultsLastSignedInUid : LastSignedInUid {
    /** The `NSUserDefaults` key. */
    const val KEY = "coplanly.lastSignedInUid"

    override fun get(): String? = NSUserDefaults.standardUserDefaults.stringForKey(KEY)

    override fun set(uid: String) {
        NSUserDefaults.standardUserDefaults.setObject(uid, forKey = KEY)
    }
}
