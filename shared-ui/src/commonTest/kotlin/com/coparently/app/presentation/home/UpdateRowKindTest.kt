package com.coparently.app.presentation.home

import com.coparently.app.domain.changerequests.HistoryEntry
import com.coparently.app.domain.changerequests.RequestOutcome
import com.coparently.app.domain.changerequests.RequestSubject
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlin.test.Test
import kotlin.test.assertEquals

/** The status disc each Updates row leads with (redesign, November 2026, variant A). */
class UpdateRowKindTest {

    private val at = LocalDateTime(2026, 11, 12, 9, 0)

    private fun item(kind: ActivityKind, answer: HistoryEntry? = null) = ActivityItem(
        id = "i",
        kind = kind,
        title = "Dentist",
        timestamp = at,
        eventId = "e1",
        isChangeRequest = kind == ActivityKind.CHANGE_REQUESTED,
        answer = answer
    )

    private fun answer(outcome: RequestOutcome) = HistoryEntry(
        key = "swap_g1",
        subject = RequestSubject.DaySwap(LocalDate(2026, 11, 20), 1),
        outcome = outcome,
        askedBy = "u1",
        decidedBy = "u2",
        decidedAt = at
    )

    @Test
    fun `an accepted answer is a check`() {
        assertEquals(UpdateMark.ACCEPTED, updateMark(item(ActivityKind.ANSWER, answer(RequestOutcome.ACCEPTED))))
    }

    @Test
    fun `a declined answer is a cross`() {
        assertEquals(UpdateMark.DECLINED, updateMark(item(ActivityKind.ANSWER, answer(RequestOutcome.DECLINED))))
    }

    @Test
    fun `a new event is added`() {
        assertEquals(UpdateMark.ADDED, updateMark(item(ActivityKind.EVENT_CREATED)))
    }

    @Test
    fun `a changed event is changed`() {
        assertEquals(UpdateMark.CHANGED, updateMark(item(ActivityKind.EVENT_UPDATED)))
    }

    @Test
    fun `a change request is asked`() {
        assertEquals(UpdateMark.ASKED, updateMark(item(ActivityKind.CHANGE_REQUESTED)))
    }

    @Test
    fun `a confirmed pickup is a good status`() {
        assertEquals(UpdateMark.ACCEPTED, updateMark(item(ActivityKind.PICKUP_CONFIRMED)))
    }
}
