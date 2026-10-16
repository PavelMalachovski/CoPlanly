package com.coparently.app.domain.changerequests

import com.coparently.app.domain.custody.CustodyProposal
import com.coparently.app.domain.custody.DayOverride
import com.coparently.app.domain.custody.DaySwap
import com.coparently.app.domain.custody.DaySwapGroup
import com.coparently.app.domain.model.ChangeRequest
import com.coparently.app.domain.model.CustodyModel
import com.coparently.app.domain.model.Event
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * The id of a request's clock (phase 3a, spec §5.1): derived from the request, so both phones find
 * the same document without asking, and carrying the proposer, so the other parent cannot create it.
 */
class RequestDeadlineIdTest {

    private val family = "uA__uB"
    private val at = LocalDateTime.of(2026, 10, 15, 10, 0)

    /** An offer by uA covering [days] days, all under [groupId]. */
    private fun swapOffer(groupId: String?, days: Int = 1) = DaySwapGroup(
        (0 until days).map { offset ->
            DaySwap(
                date = LocalDate.of(2026, 10, 20).plusDays(offset.toLong()),
                override = DayOverride(
                    toParent = "mom",
                    requestedBy = "uA",
                    requestedAt = "2026-10-15T10:00:00",
                    groupId = groupId
                )
            )
        }
    )

    private val changeRequest = ChangeRequest(
        id = "cr-1",
        eventId = "ev-1",
        eventTitle = "Swimming",
        requestedBy = "uA",
        requestedTo = "uB",
        currentStartDateTime = at.plusDays(5),
        proposedStartDateTime = at.plusDays(6),
        createdAt = at
    )

    private fun event(creator: String?) = Event(
        id = "ev-1",
        title = "Swimming",
        startDateTime = at.plusDays(5),
        eventType = "other",
        parentOwner = "mom",
        createdAt = at,
        updatedAt = at,
        createdByFirebaseUid = creator
    )

    private val proposal = CustodyProposal(
        model = CustodyModel.weekOnWeekOff(id = "m1", startDate = LocalDate.of(2026, 9, 7)),
        repeatYearly = true,
        proposedBy = "uA",
        proposedAt = "2026-10-15T10:00:00"
    )

    @Test
    fun `the id carries the kind and the proposer, and a proposal its family`() {
        assertEquals("swap_uA_g-1", RequestDeadlineId.of(RequestKind.SWAP, "uA", "g-1", family))
        assertEquals("change_uA_cr-1", RequestDeadlineId.of(RequestKind.CHANGE, "uA", "cr-1", family))
        assertEquals("event_uA_ev-1", RequestDeadlineId.of(RequestKind.EVENT, "uA", "ev-1", family))
        assertEquals(
            "proposal_uA_uA__uB_2026-10-15T10:00:00",
            RequestDeadlineId.of(RequestKind.PROPOSAL, "uA", "2026-10-15T10:00:00", family)
        )
    }

    @Test
    fun `an offer sent before groups had ids has no clock`() {
        assertNull(RequestDeadlineId.forSwap(swapOffer(groupId = null)))
    }

    @Test
    fun `each record names its own author as the proposer`() {
        // Three days under one group id are still one offer, so one clock.
        assertEquals("swap_uA_g-1", RequestDeadlineId.forSwap(swapOffer(groupId = "g-1", days = 3)))
        assertEquals("change_uA_cr-1", RequestDeadlineId.forChange(changeRequest))
        assertEquals("event_uA_ev-1", RequestDeadlineId.forEvent(event(creator = "uA")))
        assertNull(RequestDeadlineId.forEvent(event(creator = null)))
        assertNull(RequestDeadlineId.forEvent(event(creator = " ")))
        assertEquals(
            "proposal_uA_uA__uB_2026-10-15T10:00:00",
            RequestDeadlineId.forProposal(proposal, family)
        )
    }

    @Test
    fun `a wire name reads back as its kind, and a name this build does not know as nothing`() {
        // These strings are stored in the clock and rebuilt by the rules: they are a wire format.
        assertEquals(
            listOf("swap", "change", "event", "proposal"),
            RequestKind.entries.map { it.wire }
        )
        RequestKind.entries.forEach { assertEquals(it, RequestKind.fromWire(it.wire)) }
        assertNull(RequestKind.fromWire("series")) // a kind a newer build might add
        assertNull(RequestKind.fromWire(null))

        assertEquals("no_answer", RequestClockOutcome.NO_ANSWER.wire)
        assertEquals(RequestClockOutcome.NO_ANSWER, RequestClockOutcome.fromWire("no_answer"))
        assertNull(RequestClockOutcome.fromWire("withdrawn")) // an outcome a newer build might add
        assertNull(RequestClockOutcome.fromWire(null))
    }
}
