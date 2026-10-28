package com.coparently.app.data.sync

import android.util.Log
import com.coparently.app.data.local.preferences.EncryptedPreferences
import com.coparently.app.data.local.preferences.PreferenceKeys
import com.coparently.app.data.remote.google.CredentialManagerService
import com.coparently.app.data.remote.google.GoogleCalendarConnection
import kotlinx.coroutines.flow.lastOrNull
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Imports from Google Calendar on its own, from the background sync (owner request, October 2026:
 * a parent had to press "Sync from Google Calendar" to see their latest events).
 *
 * [SyncWorker] calls [importIfDue] after every run, so the import follows the app's own sync: when
 * the app starts, each time it comes back to the foreground, on the calendar's pull to refresh and
 * every fifteen minutes in the background. It runs only while the parent has turned sync on and
 * Google Calendar is connected, and at most once per [MIN_INTERVAL_MILLIS]. It is the same import
 * the button runs ([CalendarSyncRepository.syncFromGoogle]), so every event still arrives private
 * (Legal item 10) and a re-import keeps what the parent set in the app.
 */
@Singleton
class GoogleCalendarAutoImport @Inject constructor(
    private val calendarSyncRepository: CalendarSyncRepository,
    private val credentialManagerService: CredentialManagerService,
    private val encryptedPreferences: EncryptedPreferences
) {

    /**
     * Imports when [isDue] says so, and records the attempt.
     *
     * The attempt is recorded before the import, success or not: a failing import must not ask
     * Google again on every foreground. A failure stays in the log; the Settings row shows it the
     * next time the parent presses the button.
     *
     * @param nowMillis The current time, for the spacing.
     * @return The import's last result, or null when nothing ran.
     */
    suspend fun importIfDue(nowMillis: Long = System.currentTimeMillis()): SyncResult? {
        val due = isDue(
            enabled = encryptedPreferences.getBoolean(PreferenceKeys.GOOGLE_CALENDAR_SYNC_ENABLED, false),
            connection = credentialManagerService.connection(),
            lastRunMillis = encryptedPreferences.getString(PreferenceKeys.GOOGLE_CALENDAR_LAST_AUTO_IMPORT)
                ?.toLongOrNull(),
            nowMillis = nowMillis
        )
        if (!due) return null
        encryptedPreferences.putString(PreferenceKeys.GOOGLE_CALENDAR_LAST_AUTO_IMPORT, nowMillis.toString())
        val result = calendarSyncRepository.syncFromGoogle().lastOrNull()
        if (result is SyncResult.Error) {
            Log.w(TAG, "Automatic Google Calendar import failed: ${result.reason}")
        }
        return result
    }

    companion object {
        private const val TAG = "GoogleCalendarAutoImport"

        /** The least time between two automatic imports: five minutes. */
        const val MIN_INTERVAL_MILLIS = 5 * 60 * 1000L

        /**
         * Whether an automatic import should run now: sync is on, Google Calendar is connected
         * (one that needs a reconnect waits for the parent), and the last automatic import is at
         * least [MIN_INTERVAL_MILLIS] old. A last time in the future — a clock moved back — counts
         * as due, so the import does not stop until the clock catches up.
         */
        fun isDue(
            enabled: Boolean,
            connection: GoogleCalendarConnection,
            lastRunMillis: Long?,
            nowMillis: Long
        ): Boolean {
            if (!enabled || connection != GoogleCalendarConnection.CONNECTED) return false
            if (lastRunMillis == null || lastRunMillis > nowMillis) return true
            return nowMillis - lastRunMillis >= MIN_INTERVAL_MILLIS
        }
    }
}
