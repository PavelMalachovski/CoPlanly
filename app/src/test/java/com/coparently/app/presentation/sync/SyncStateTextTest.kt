package com.coparently.app.presentation.sync

import com.coparently.app.R
import com.coparently.app.data.sync.SyncFailure
import com.coparently.app.data.sync.SyncResult
import com.coparently.app.data.sync.SyncStage
import com.coparently.app.presentation.common.UiText
import org.junit.Test
import java.time.LocalDate
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

/**
 * How a Google Calendar import is worded (CQ-14).
 *
 * `CalendarSyncRepository` used to build these sentences itself, in English, and
 * `CalendarSyncRepositoryTest` compared them. It now reports facts; this pins the wording side:
 * which resource each fact selects, so a translation can change the words without changing what
 * the user is told.
 */
class SyncStateTextTest {

    private val from = LocalDate.of(2026, 8, 24)
    private val until = LocalDate.of(2027, 8, 24)

    @Test
    fun `a truncated import is not worded like a complete one`() {
        // CQ-7: the old import stopped at the 50th event and said what a complete one said.
        val cutShort = SyncResult.Success(synced = 50, from = from, until = until, truncated = true).toSyncState()
        val complete = SyncResult.Success(synced = 50, from = from, until = until, truncated = false).toSyncState()

        assertEquals(
            GoogleCalendarSyncState.Success(
                UiText.Plural(
                    R.plurals.sync_google_synced_truncated,
                    50,
                    listOf(50, UiText.Date(from), UiText.Date(until))
                )
            ),
            cutShort
        )
        assertNotEquals(cutShort, complete)
        assertEquals(
            R.plurals.sync_google_synced,
            ((complete as GoogleCalendarSyncState.Success).message as UiText.Plural).id
        )
    }

    @Test
    fun `the window read is passed as dates, formatted in the reader's locale`() {
        val state = SyncResult.Success(synced = 3, from = from, until = until, truncated = false).toSyncState()

        val text = (state as GoogleCalendarSyncState.Success).message as UiText.Plural
        assertEquals(listOf(3, UiText.Date(from), UiText.Date(until)), text.args)
    }

    @Test
    fun `progress names its stage, and the count it found`() {
        assertEquals(
            GoogleCalendarSyncState.Syncing(UiText.Res(R.string.sync_google_progress_fetching)),
            SyncResult.Progress(SyncStage.FETCHING).toSyncState()
        )
        assertEquals(
            GoogleCalendarSyncState.Syncing(UiText.Plural(R.plurals.sync_google_progress_found, 7)),
            SyncResult.Progress(SyncStage.FOUND, found = 7).toSyncState()
        )
    }

    @Test
    fun `every failure has its own wording, never the exception text`() {
        val ids = SyncFailure.entries.map { failure ->
            val state = SyncResult.Error(failure).toSyncState()
            ((state as GoogleCalendarSyncState.Error).message as UiText.Res).id
        }
        assertEquals(ids.size, ids.toSet().size, "two failures share one sentence")
    }

    @Test
    fun `status codes map to what the user can act on`() {
        assertEquals(SyncFailure.AUTHENTICATION, SyncFailure.forStatus(401))
        assertEquals(SyncFailure.ACCESS_DENIED, SyncFailure.forStatus(403))
        assertEquals(SyncFailure.CALENDAR_NOT_FOUND, SyncFailure.forStatus(404))
        assertEquals(SyncFailure.RATE_LIMITED, SyncFailure.forStatus(429))
        assertEquals(SyncFailure.SERVICE_UNAVAILABLE, SyncFailure.forStatus(503))
        assertEquals(SyncFailure.UNKNOWN, SyncFailure.forStatus(418))
    }
}
