package com.coparently.app.domain.changerequests

import com.coparently.app.domain.custody.DayOverride
import com.coparently.app.domain.custody.DayOverrideStatus
import com.coparently.app.domain.events.EventAcceptance
import com.coparently.app.domain.model.Event
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The rules the inbox's count is built from, for Home's Requests row and the calendar's banner
 * alike. The UI tour showed 6 on one and 2 on the other: the banner left out pending events and
 * counted swaps per day.
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
    fun `an event waits for an answer only while pending, and never from its creator`() {
        // The rule a chat card about an event routes by too: the inbox opens for exactly the
        // events this count includes, and an event outside it opens as a preview.
        fun awaits(candidate: Event, uid: String = me) = AwaitingAnswers.eventAwaitsAnswerFrom(candidate, uid)

        assertTrue(awaits(event("theirs", coParent, EventAcceptance.PENDING)))
        assertFalse(awaits(event("mine", me, EventAcceptance.PENDING)))
        assertFalse(awaits(event("plain", coParent, EventAcceptance.NOT_REQUIRED)))
        assertFalse(awaits(event("accepted", coParent, EventAcceptance.ACCEPTED)))
        assertFalse(awaits(event("declined", coParent, EventAcceptance.DECLINED)))
        // Not before the signed-in uid is known, like the count.
        assertFalse(awaits(event("theirs", coParent, EventAcceptance.PENDING), uid = ""))
    }

    @Test
    fun `a swap offer waits on me only when the co-parent offered it and its days are ahead`() {
        val overrides = mapOf(
            "2026-10-04" to swap(),
            // Offered by me: the co-parent answers it, not me.
            "2026-10-11" to swap(requestedBy = me),
            // Already lived: history, not inbox.
            "2026-09-20" to swap()
        )

        assertEquals(
            listOf(LocalDate.of(2026, 10, 4)),
            AwaitingAnswers.swapOffers(overrides, me, today).map { it.firstDate }
        )
    }

    @Test
    fun `nothing waits before the signed-in uid is known`() {
        val events = listOf(event("theirs-pending", coParent, EventAcceptance.PENDING))

        assertEquals(emptyList(), AwaitingAnswers.swapOffers(mapOf("2026-10-04" to swap()), "", today))
        assertEquals(0, AwaitingAnswers.eventCount(events, ""))
    }
}
