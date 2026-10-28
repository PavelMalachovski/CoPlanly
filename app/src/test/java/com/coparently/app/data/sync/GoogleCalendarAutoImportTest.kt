package com.coparently.app.data.sync

import com.coparently.app.data.local.preferences.EncryptedPreferences
import com.coparently.app.data.local.preferences.PreferenceKeys
import com.coparently.app.data.remote.google.CredentialManagerService
import com.coparently.app.data.remote.google.GoogleCalendarConnection
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Test
import java.time.LocalDate
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** The automatic Google Calendar import: when it runs, and that it runs the button's import. */
class GoogleCalendarAutoImportTest {

    private val now = 1_800_000_000_000L
    private val interval = GoogleCalendarAutoImport.MIN_INTERVAL_MILLIS

    @Test
    fun `it runs only with sync on and Google Calendar connected`() {
        assertTrue(due(enabled = true, connection = GoogleCalendarConnection.CONNECTED))
        assertFalse(due(enabled = false, connection = GoogleCalendarConnection.CONNECTED))
        assertFalse(due(enabled = true, connection = GoogleCalendarConnection.NEEDS_RECONNECT))
        assertFalse(due(enabled = true, connection = GoogleCalendarConnection.NOT_CONNECTED))
    }

    @Test
    fun `it waits five minutes after the last automatic import`() {
        assertFalse(due(lastRun = now - interval + 1))
        assertTrue(due(lastRun = now - interval))
        assertTrue(due(lastRun = null))
    }

    @Test
    fun `a last run in the future does not stop the import`() {
        assertTrue(due(lastRun = now + interval))
    }

    @Test
    fun `a due import runs the button's import and records when`() = runTest {
        val repository: CalendarSyncRepository = mockk()
        val success = SyncResult.Success(3, LocalDate.of(2026, 10, 1), LocalDate.of(2027, 1, 1), false)
        coEvery { repository.syncFromGoogle(any(), any()) } returns
            flowOf(SyncResult.Progress(SyncStage.STARTING), success)
        val preferences = preferences(enabled = true, lastRun = null)
        val import = GoogleCalendarAutoImport(repository, connected(), preferences)

        assertEquals(success, import.importIfDue(now))
        verify { preferences.putString(PreferenceKeys.GOOGLE_CALENDAR_LAST_AUTO_IMPORT, now.toString()) }
    }

    @Test
    fun `an import that is not due asks Google nothing`() = runTest {
        val repository: CalendarSyncRepository = mockk()
        val preferences = preferences(enabled = true, lastRun = now - 1)
        val import = GoogleCalendarAutoImport(repository, connected(), preferences)

        assertNull(import.importIfDue(now))
        coVerify(exactly = 0) { repository.syncFromGoogle(any(), any()) }
        verify(exactly = 0) { preferences.putString(any(), any()) }
    }

    private fun due(
        enabled: Boolean = true,
        connection: GoogleCalendarConnection = GoogleCalendarConnection.CONNECTED,
        lastRun: Long? = null
    ) = GoogleCalendarAutoImport.isDue(enabled, connection, lastRun, now)

    private fun connected(): CredentialManagerService = mockk {
        every { connection() } returns GoogleCalendarConnection.CONNECTED
    }

    private fun preferences(enabled: Boolean, lastRun: Long?): EncryptedPreferences = mockk(relaxed = true) {
        every { getBoolean(PreferenceKeys.GOOGLE_CALENDAR_SYNC_ENABLED, false) } returns enabled
        every { getString(PreferenceKeys.GOOGLE_CALENDAR_LAST_AUTO_IMPORT, any()) } returns lastRun?.toString()
    }
}
