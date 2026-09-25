package com.coparently.app.domain.changerequests

import com.coparently.app.domain.custody.DayOverride
import com.coparently.app.domain.custody.DayOverrideStatus
import com.coparently.app.domain.events.EventAcceptance
import com.coparently.app.domain.model.Event
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import kotlin.test.assertEquals

/**
 * The one count Home's "Awaiting your answer" row and the calendar's change-request banner print
 * for the same inbox. The UI tour showed 6 on one and 2 on the other: the banner left out pending
 * events and counted swaps per day.
 */
class AwaitingAnswersTest {

    private val today = LocalDate.of(2026, 9, 25)
    private val me = "uid-alice"
    private val coParent = "uid-bob"

    private fun swap(requestedBy: String = coParent, groupId: String? = null) = DayOverride(
        toParent = "dad",
        requestedBy = requestedBy,
        requestedAt = "2026-09-20T10:00:00",
        status = DayOverrideStatus.PENDING,
        groupId = groupId
    )

    private fun event(id: String, createdBy: String, acceptance: EventAcceptance) =
        LocalDateTime.of(2026, 9, 28, 17, 30).let { at ->
            Event(
                id = id,
                title = id,
                startDateTime = at,
                eventType = "school",
                parentOwner = "mom",
                createdAt = at,
                updatedAt = at,
                createdByFirebaseUid = createdBy,
                acceptance = acceptance
            )
        }

    @Test
    fun `a swap offered for several days counts once, as the inbox shows one card`() {
        val week = (1..3).associate { day ->
            LocalDate.of(2026, 10, day).toString() to swap(groupId = "g1")
        }

        assertEquals(1, AwaitingAnswers.swapOffers(week, me, today).size)
        assertEquals(1, AwaitingAnswers.count(0, week, emptyList(), me, today))
    }

    @Test
    fun `only events the co-parent created and still waiting count`() {
        val events = listOf(
            event("theirs-pending", coParent, EventAcceptance.PENDING),
            event("mine-pending", me, EventAcceptance.PENDING),
            event("theirs-plain", coParent, EventAcceptance.NOT_REQUIRED)
        )

        assertEquals(1, AwaitingAnswers.eventCount(events, me))
    }

    @Test
    fun `the count is requests, swap offers and pending events together`() {
        val overrides = mapOf(
            "2026-10-04" to swap(),
            // Offered by me: the co-parent answers it, not me.
            "2026-10-11" to swap(requestedBy = me),
            // Already lived: history, not inbox.
            "2026-09-20" to swap()
        )
        val events = (1..4).map { event("e$it", coParent, EventAcceptance.PENDING) }

        assertEquals(6, AwaitingAnswers.count(1, overrides, events, me, today))
    }

    @Test
    fun `nothing is counted before the signed-in uid is known`() {
        val events = listOf(event("theirs-pending", coParent, EventAcceptance.PENDING))

        assertEquals(0, AwaitingAnswers.count(0, mapOf("2026-10-04" to swap()), events, "", today))
    }
}
