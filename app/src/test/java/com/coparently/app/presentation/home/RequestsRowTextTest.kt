package com.coparently.app.presentation.home

import com.coparently.app.R
import com.coparently.app.domain.changerequests.HistoryEntry
import com.coparently.app.domain.changerequests.RequestOutcome
import com.coparently.app.domain.changerequests.RequestScope
import com.coparently.app.domain.changerequests.RequestSubject
import com.coparently.app.presentation.common.UiText
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Home's Requests row (phase 2, spec §6.1): a line made only of what has something to say — what
 * waits on each parent and the co-parent's latest answer to one of this parent's requests — and
 * "No requests yet" when nothing does.
 */
class RequestsRowTextTest {

    private val scope = RequestScope(myUid = "u1", coParentUid = "u2", familyId = "u1__u2", soleFamily = false)

    /** A Wednesday. */
    private val now = LocalDateTime.of(2026, 10, 14, 12, 0)
    private val today = now.toLocalDate()

    private fun answer(
        outcome: RequestOutcome,
        decidedAt: LocalDateTime,
        askedBy: String = "u1",
        decidedBy: String = "u2",
        subject: RequestSubject = RequestSubject.NewTime("e1", "Swimming", LocalDateTime.of(2026, 10, 16, 18, 30))
    ) = HistoryEntry(
        key = "cr_$decidedAt",
        subject = subject,
        outcome = outcome,
        askedBy = askedBy,
        decidedBy = decidedBy,
        decidedAt = decidedAt
    )

    @Test
    fun `nothing waiting and no recent answer reads no requests yet`() {
        assertEquals(
            listOf(UiText.Res(R.string.home_requests_none)),
            RequestsRowText.parts(RequestsRowState(), "Pavel", today)
        )
    }

    @Test
    fun `each part appears only when it has something to say, in order`() {
        val accepted = answer(RequestOutcome.ACCEPTED, now.minusDays(1))
        for (waitingOnMe in listOf(0, 2)) {
            for (waitingOnThem in listOf(0, 1)) {
                for (latest in listOf(accepted, null)) {
                    val expected = buildList {
                        if (waitingOnMe > 0) add(UiText.Plural(R.plurals.home_requests_waiting_on_you, 2))
                        if (waitingOnThem > 0) {
                            add(UiText.Plural(R.plurals.home_requests_waiting_on_them, 1, listOf(1, "Pavel")))
                        }
                        if (latest != null) {
                            add(
                                UiText.Res(
                                    R.string.home_requests_answer_accepted,
                                    listOf(UiText.Res(R.string.home_requests_day_yesterday), "Pavel")
                                )
                            )
                        }
                    }.ifEmpty { listOf(UiText.Res(R.string.home_requests_none)) }

                    assertEquals(
                        expected,
                        RequestsRowText.parts(RequestsRowState(waitingOnMe, waitingOnThem, latest), "Pavel", today),
                        "waiting on me $waitingOnMe, on them $waitingOnThem, answer ${latest != null}"
                    )
                }
            }
        }

        val declined = answer(RequestOutcome.DECLINED, now)
        assertEquals(
            listOf(
                UiText.Res(
                    R.string.home_requests_answer_declined,
                    listOf(UiText.Res(R.string.home_requests_day_today), "Pavel")
                )
            ),
            RequestsRowText.parts(RequestsRowState(latestAnswer = declined), "Pavel", today)
        )
    }

    @Test
    fun `an answer older than seven days is not mentioned`() {
        val edge = answer(RequestOutcome.DECLINED, now.minusDays(7))
        val older = answer(RequestOutcome.DECLINED, now.minusDays(7).minusMinutes(1))

        assertEquals(edge, RequestsRowText.latestAnswer(listOf(edge), scope, now))
        assertNull(RequestsRowText.latestAnswer(listOf(older), scope, now))
    }

    @Test
    fun `only the co-parent's answers to my requests count`() {
        val myAnswerToTheirs = answer(RequestOutcome.ACCEPTED, now.minusHours(1), askedBy = "u2", decidedBy = "u1")
        val withdrawal = answer(RequestOutcome.WITHDRAWN, now.minusHours(2))
        val custody = answer(RequestOutcome.ACCEPTED, now.minusHours(3), subject = RequestSubject.CustodySchedule)
        val theirAnswer = answer(RequestOutcome.DECLINED, now.minusDays(2))
        val olderAnswer = answer(RequestOutcome.ACCEPTED, now.minusDays(3))

        assertEquals(
            theirAnswer,
            RequestsRowText.latestAnswer(
                listOf(myAnswerToTheirs, withdrawal, custody, olderAnswer, theirAnswer),
                scope,
                now
            )
        )
        assertNull(RequestsRowText.latestAnswer(listOf(myAnswerToTheirs, withdrawal, custody), scope, now))
    }

    @Test
    fun `a request the co-parent never answered is not their answer`() {
        // The row would otherwise say they declined it (phase 3a: "No answer" is never "Declined").
        val unanswered = answer(RequestOutcome.NO_ANSWER, now.minusHours(1))
        val theirAnswer = answer(RequestOutcome.DECLINED, now.minusDays(2))

        assertEquals(theirAnswer, RequestsRowText.latestAnswer(listOf(unanswered, theirAnswer), scope, now))
        assertNull(RequestsRowText.latestAnswer(listOf(unanswered), scope, now))
    }

    @Test
    fun `relative days`() {
        assertEquals(UiText.Res(R.string.home_requests_day_today), RequestsRowText.relativeDay(today, today))
        assertEquals(
            UiText.Res(R.string.home_requests_day_yesterday),
            RequestsRowText.relativeDay(today.minusDays(1), today)
        )
        assertEquals(UiText.Weekday(DayOfWeek.SUNDAY), RequestsRowText.relativeDay(LocalDate.of(2026, 10, 11), today))
        assertEquals(UiText.Date(today.minusDays(7)), RequestsRowText.relativeDay(today.minusDays(7), today))
    }
}
