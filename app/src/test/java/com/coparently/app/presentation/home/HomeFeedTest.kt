package com.coparently.app.presentation.home

import com.coparently.app.domain.changerequests.HistoryEntry
import com.coparently.app.domain.changerequests.RequestOutcome
import com.coparently.app.domain.changerequests.RequestSubject
import com.coparently.app.domain.model.ChangeRequest
import com.coparently.app.domain.model.Event
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The co-parent's feed on Home (phase 2, spec §6.1): what they changed and asked, and now how
 * they answered this parent's requests — one row per answer, never also the event write the
 * answer caused.
 */
class HomeFeedTest {

    private val me = "u1"
    private val partner = "u2"
    private val t0 = LocalDateTime.of(2026, 10, 10, 9, 0)

    private fun event(
        id: String,
        createdAt: LocalDateTime = t0.minusDays(1),
        updatedAt: LocalDateTime = t0,
        lastModifiedBy: String = partner
    ) = Event(
        id = id,
        title = id,
        startDateTime = t0.plusDays(3),
        eventType = "other",
        parentOwner = "mom",
        createdAt = createdAt,
        updatedAt = updatedAt,
        lastModifiedBy = lastModifiedBy
    )

    private fun request(id: String, from: String, to: String, createdAt: LocalDateTime) = ChangeRequest(
        id = id,
        eventId = "e-$id",
        eventTitle = "Swimming",
        requestedBy = from,
        requestedTo = to,
        currentStartDateTime = t0.plusDays(1),
        proposedStartDateTime = t0.plusDays(2),
        createdAt = createdAt
    )

    @Suppress("LongParameterList") // one history entry's fields
    private fun answer(
        key: String,
        subject: RequestSubject,
        outcome: RequestOutcome,
        decidedAt: LocalDateTime,
        askedBy: String = me,
        decidedBy: String = partner
    ) = HistoryEntry(key, subject, outcome, askedBy, decidedBy, decidedAt)

    @Test
    fun `the co-parent's answers to my requests appear with their outcome`() {
        val accepted = answer(
            "cr_r1",
            RequestSubject.NewTime("e1", "Swimming", t0.plusDays(2)),
            RequestOutcome.ACCEPTED,
            decidedAt = t0.plusHours(1)
        )
        val declined = answer(
            "swap_g1",
            RequestSubject.DaySwap(LocalDate.of(2026, 10, 20), 2),
            RequestOutcome.DECLINED,
            decidedAt = t0.plusHours(2)
        )

        val items = HomeFeed.items(emptyList(), emptyList(), listOf(accepted, declined), partner, maxItems = 5)

        assertEquals(listOf("ans_swap_g1", "ans_cr_r1"), items.map { it.id })
        assertEquals(listOf(declined, accepted), items.map { it.answer })
        assertTrue(items.all { it.kind == ActivityKind.ANSWER })
        assertEquals(listOf("", "e1"), items.map { it.eventId })
        assertEquals(listOf(t0.plusHours(2), t0.plusHours(1)), items.map { it.timestamp })
    }

    @Test
    fun `an accepted event is one Accepted row, not also an Updated one`() {
        // Accepting an event stamps updatedAt with acceptedAt, by the parent who accepted.
        val acceptedAt = t0.plusHours(5)
        val accepted = event("e1", updatedAt = acceptedAt)
        val answer = answer(
            "ev_e1",
            RequestSubject.NewEvent("e1", "Dentist", t0.plusDays(3)),
            RequestOutcome.ACCEPTED,
            decidedAt = acceptedAt
        )

        assertEquals(
            listOf("ans_ev_e1"),
            HomeFeed.items(listOf(accepted), emptyList(), listOf(answer), partner, maxItems = 5).map { it.id }
        )
    }

    @Test
    fun `an accepted new time is one answer row, not also the event update it caused`() {
        // Accepting a change request moves the event a moment before the request is marked.
        val respondedAt = t0.plusHours(5)
        val moved = event("e1", updatedAt = respondedAt.minusSeconds(1))
        val answer = answer(
            "cr_r1",
            RequestSubject.NewTime("e1", "Swimming", t0.plusDays(1)),
            RequestOutcome.ACCEPTED,
            decidedAt = respondedAt
        )

        assertEquals(
            listOf("ans_cr_r1"),
            HomeFeed.items(listOf(moved), emptyList(), listOf(answer), partner, maxItems = 5).map { it.id }
        )

        // An edit well after the answer is a change of its own and stays.
        val editedLater = moved.copy(updatedAt = respondedAt.plusMinutes(5))
        assertEquals(
            listOf("ev_e1_${editedLater.updatedAt}", "ans_cr_r1"),
            HomeFeed.items(listOf(editedLater), emptyList(), listOf(answer), partner, maxItems = 5).map { it.id }
        )
    }

    @Test
    fun `answers share the cap with the rest, newest first`() {
        val events = (1..3).map { event("e$it", updatedAt = t0.plusHours(it.toLong())) }
        val requests = listOf(request("r1", from = partner, to = me, createdAt = t0.plusHours(4)))
        val answers = listOf(
            answer(
                "cr_mine",
                RequestSubject.NewTime("e9", "Football", t0),
                RequestOutcome.DECLINED,
                decidedAt = t0.plusHours(5)
            )
        )

        val items = HomeFeed.items(events, requests, answers, partner, maxItems = 3)

        assertEquals(listOf("ans_cr_mine", "cr_r1", "ev_e3_${t0.plusHours(3)}"), items.map { it.id })
    }

    @Test
    fun `my answers to the co-parent's requests are not in their feed`() {
        val myAnswer = answer(
            "cr_theirs",
            RequestSubject.NewTime("e1", "Swimming", t0),
            RequestOutcome.ACCEPTED,
            decidedAt = t0,
            askedBy = partner,
            decidedBy = me
        )
        val theirWithdrawal = answer(
            "cr_withdrawn",
            RequestSubject.NewTime("e2", "Dentist", t0),
            RequestOutcome.WITHDRAWN,
            decidedAt = t0,
            askedBy = partner,
            decidedBy = partner
        )
        val custody = answer("custody_x", RequestSubject.CustodySchedule, RequestOutcome.ACCEPTED, decidedAt = t0)

        assertEquals(
            emptyList(),
            HomeFeed.items(emptyList(), emptyList(), listOf(myAnswer, theirWithdrawal, custody), partner, maxItems = 5)
        )
    }
}
