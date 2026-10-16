package com.coparently.app.presentation.changerequests

import com.coparently.app.R
import com.coparently.app.domain.changerequests.RequestClockOutcome
import com.coparently.app.domain.changerequests.RequestDeadline
import com.coparently.app.domain.changerequests.RequestKind
import com.coparently.app.presentation.common.UiText
import com.coparently.app.utils.answerByText
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneOffset

/**
 * [RequestClockText]: how long a request has left, in the largest unit that still reads well, and
 * the meta line of a "No answer" row in History (phase 3a, spec §6.2, §6.6).
 */
class RequestClockTextTest {

    @Test
    fun `time left reads days, then hours, then minutes`() {
        assertEquals(UiText.Plural(R.plurals.request_left_days, 3), RequestClockText.left(72 * HOUR))
        assertEquals(UiText.Plural(R.plurals.request_left_hours, 47), RequestClockText.left(47 * HOUR))
        assertEquals(UiText.Plural(R.plurals.request_left_minutes, 59), RequestClockText.left(59 * MINUTE))
        assertEquals(UiText.Plural(R.plurals.request_left_minutes, 1), RequestClockText.left(20_000L))
    }

    @Test
    fun `the history line names each part it knows, in order`() {
        val full = CLOCK.copy(
            deliveredAtMillis = SENT + HOUR,
            remindersPlanned = 3,
            reminderTimesMillis = listOf(SENT + 10 * HOUR, SENT + 20 * HOUR, SENT + 30 * HOUR),
            remindersShown = 2,
            seenAtMillis = SENT + 50 * HOUR,
            lastChanceUntilMillis = ANSWER_BY + 24 * HOUR,
            outcome = RequestClockOutcome.NO_ANSWER
        )
        assertEquals(
            listOf(
                UiText.Res(R.string.request_history_meta_deadline, listOf(UiText.Date(LocalDate.of(2026, 5, 16)))),
                UiText.Res(
                    R.string.request_history_meta_last_chance,
                    listOf(UiText.Raw(answerByText(ANSWER_BY + 24 * HOUR, ZoneOffset.UTC)))
                ),
                UiText.Res(R.string.request_history_meta_opened, listOf(UiText.Date(LocalDate.of(2026, 5, 15)))),
                UiText.Plural(R.plurals.request_history_meta_reminders, 2),
                UiText.Res(R.string.request_history_meta_unchanged)
            ),
            RequestClockText.historyMeta(full, ZoneOffset.UTC)
        )

        val neverDelivered = CLOCK.copy(outcome = RequestClockOutcome.NO_ANSWER)
        assertEquals(
            listOf(
                UiText.Res(R.string.request_history_meta_deadline, listOf(UiText.Date(LocalDate.of(2026, 5, 16)))),
                UiText.Res(R.string.request_history_meta_unchanged)
            ),
            RequestClockText.historyMeta(neverDelivered, ZoneOffset.UTC)
        )
    }

    private companion object {
        const val MINUTE = 60_000L
        const val HOUR = 60 * MINUTE

        /** Wednesday 13 May 2026, 10:00 UTC. */
        val SENT: Long = LocalDate.of(2026, 5, 13).atTime(10, 0).toInstant(ZoneOffset.UTC).toEpochMilli()

        /** Three days later: Saturday 16 May, 10:00 UTC. */
        val ANSWER_BY: Long = SENT + 72 * HOUR

        val CLOCK = RequestDeadline(
            id = "change_u1_r1",
            kind = RequestKind.CHANGE,
            ref = "r1",
            familyId = "u1__u2",
            proposerUid = "u1",
            responderUid = "u2",
            answerByMillis = ANSWER_BY,
            createdAtMillis = SENT
        )
    }
}
