package com.coparently.app.presentation.changerequests

import com.coparently.app.domain.changerequests.RequestInbox
import com.coparently.app.domain.changerequests.RequestScope
import com.coparently.app.domain.custody.CustodyProposal
import com.coparently.app.domain.custody.DayOverride
import com.coparently.app.domain.custody.DaySwap
import com.coparently.app.domain.custody.DaySwapGroup
import com.coparently.app.domain.custody.SharedCustody
import com.coparently.app.domain.events.EventAcceptance
import com.coparently.app.domain.model.ChangeRequest
import com.coparently.app.domain.model.ChangeRequestStatus
import com.coparently.app.domain.model.CustodyModel
import com.coparently.app.domain.model.Event
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * The inbox's one flat list (phase 2, spec §6.2): what its `LazyColumn` draws, in order, and the
 * only thing a scroll target is looked up in — so a chat card's highlight and a "History" link
 * land on the card they name, a pending proposal above them or not.
 */
class InboxItemsTest {

    private val scope = RequestScope(myUid = "u1", coParentUid = "u2", familyId = "u1__u2", soleFamily = false)
    private val today = LocalDate.of(2026, 10, 14)
    private val created = LocalDateTime.of(2026, 10, 10, 9, 0)

    private fun request(
        id: String,
        from: String,
        to: String,
        status: ChangeRequestStatus = ChangeRequestStatus.PENDING
    ) =
        ChangeRequest(
            id = id,
            eventId = "e-$id",
            eventTitle = "Swimming",
            requestedBy = from,
            requestedTo = to,
            currentStartDateTime = LocalDateTime.of(2026, 10, 15, 17, 0),
            proposedStartDateTime = LocalDateTime.of(2026, 10, 16, 18, 30),
            status = status,
            createdAt = created,
            respondedAt = created.plusDays(1).takeIf { status != ChangeRequestStatus.PENDING }
        )

    private fun event(id: String, creator: String) = Event(
        id = id,
        title = id,
        startDateTime = LocalDateTime.of(2026, 10, 21, 16, 0),
        eventType = "other",
        parentOwner = if (creator == "u1") "dad" else "mom",
        createdAt = created,
        updatedAt = created,
        createdByFirebaseUid = creator,
        familyId = "u1__u2",
        acceptance = EventAcceptance.PENDING
    )

    private fun swap(requestedBy: String, groupId: String) = DayOverride(
        toParent = "mom",
        requestedBy = requestedBy,
        requestedAt = "2026-10-13T10:00:00",
        groupId = groupId
    )

    private fun proposalBy(uid: String): SharedCustody {
        val model = CustodyModel.weekOnWeekOff(id = "m1", startDate = LocalDate.of(2026, 9, 7))
        return SharedCustody(
            model = model,
            lastModifiedBy = uid,
            lastModifiedAtMillis = 1L,
            createdAt = "",
            proposal = CustodyProposal(
                model = model,
                repeatYearly = true,
                proposedBy = uid,
                proposedAt = "2026-10-12T08:00:00"
            )
        )
    }

    /** Something in every section: the co-parent's proposal, one of each kind either way, a past answer. */
    private val full = RequestInbox.build(
        scope,
        changeRequests = listOf(
            request("theirs", from = "u2", to = "u1"),
            request("mine", from = "u1", to = "u2"),
            request("answered", from = "u1", to = "u2", status = ChangeRequestStatus.ACCEPTED)
        ),
        events = listOf(event("for-me", creator = "u2"), event("for-them", creator = "u1")),
        overrides = mapOf(
            "2026-10-20" to swap(requestedBy = "u2", groupId = "g-theirs"),
            "2026-10-22" to swap(requestedBy = "u1", groupId = "g-mine")
        ),
        shared = proposalBy("u2"),
        today = today
    )

    @Test
    fun `sections come in order with their items, and an empty section has no header`() {
        assertEquals(
            listOf(
                "h_waiting", "p_theirs", "ev_for-me", "sw_g-theirs", "cr_theirs",
                "h_sent", "ev_for-them", "sw_g-mine", "cr_mine",
                "h_history", "history"
            ),
            InboxItems.of(full).map { it.key }
        )

        val onlyTheirs = RequestInbox.build(
            scope,
            changeRequests = listOf(request("theirs", from = "u2", to = "u1")),
            events = emptyList(),
            overrides = emptyMap(),
            shared = null,
            today = today
        )
        assertEquals(listOf("h_waiting", "cr_theirs"), InboxItems.of(onlyTheirs).map { it.key })
    }

    @Test
    fun `a request below a pending proposal is found at its exact index`() {
        // The old count left the proposal out and scrolled one card short: [0] header,
        // [1] the proposal, [2] the request.
        val inbox = RequestInbox.build(
            scope,
            changeRequests = listOf(request("r1", from = "u2", to = "u1")),
            events = emptyList(),
            overrides = emptyMap(),
            shared = proposalBy("u2"),
            today = today
        )

        assertEquals(2, InboxItems.indexOfRequest(InboxItems.of(inbox), "r1"))
    }

    @Test
    fun `an answered request is found at the History item`() {
        val items = InboxItems.of(full)

        assertEquals(items.indexOfFirst { it is InboxItem.History }, InboxItems.indexOfRequest(items, "answered"))
        assertEquals(items.indexOfFirst { it.key == "cr_mine" }, InboxItems.indexOfRequest(items, "mine"))
        assertEquals(-1, InboxItems.indexOfRequest(items, "nowhere"))
    }

    @Test
    fun `the history header can be scrolled to`() {
        val items = InboxItems.of(full)

        assertEquals(items.indexOfFirst { it.key == "h_history" }, InboxItems.indexOf(items, InboxSection.HISTORY))
        assertEquals(0, InboxItems.indexOf(items, InboxSection.WAITING_ON_ME))

        val noHistory = InboxItems.of(
            RequestInbox.build(
                scope,
                changeRequests = listOf(request("theirs", from = "u2", to = "u1")),
                events = emptyList(),
                overrides = emptyMap(),
                shared = null,
                today = today
            )
        )
        assertEquals(-1, InboxItems.indexOf(noHistory, InboxSection.HISTORY))
    }

    @Test
    fun `arriving for a section scrolls to its heading, and stays put when it is not drawn`() {
        val items = InboxItems.of(full)

        assertEquals(
            InboxArrival.ScrollTo(items.indexOfFirst { it.key == "h_history" }),
            InboxItems.arrival(items, linkedEventId = null, requestId = null, section = InboxSection.HISTORY)
        )
        assertEquals(
            InboxArrival.Stay,
            InboxItems.arrival(emptyList(), linkedEventId = null, requestId = null, section = InboxSection.HISTORY)
        )
        assertEquals(
            InboxArrival.Stay,
            InboxItems.arrival(items, linkedEventId = null, requestId = null, section = null)
        )
    }

    @Test
    fun `arriving from a chat card scrolls to its request, and says so when the event has none`() {
        val items = InboxItems.of(full)

        assertEquals(
            InboxArrival.ScrollTo(items.indexOfFirst { it is InboxItem.History }),
            InboxItems.arrival(items, linkedEventId = "e-answered", requestId = "answered", section = null)
        )
        // The card wins over a section, and an event without a request is said to be gone.
        assertEquals(
            InboxArrival.Missing,
            InboxItems.arrival(items, linkedEventId = "e-gone", requestId = null, section = InboxSection.HISTORY)
        )
    }

    @Test
    fun `every key is unique`() {
        val proposal = proposalBy("u2").proposal!!
        val group = DaySwapGroup(listOf(DaySwap(LocalDate.of(2026, 10, 20), swap(requestedBy = "u2", groupId = "g1"))))
        val items = listOf(
            InboxItem.Header(InboxSection.WAITING_ON_ME),
            InboxItem.Header(InboxSection.WAITING_ON_THEM),
            InboxItem.Header(InboxSection.HISTORY),
            InboxItem.Proposal(proposal, mine = false),
            InboxItem.Proposal(proposal, mine = true),
            InboxItem.AwaitingEvent(event("e1", creator = "u2"), mine = false),
            InboxItem.Swap(group),
            InboxItem.Request(request("r1", from = "u2", to = "u1")),
            InboxItem.History(emptyList())
        )

        assertEquals(
            listOf("h_waiting", "h_sent", "h_history", "p_theirs", "p_mine", "ev_e1", "sw_g1", "cr_r1", "history"),
            items.map { it.key }
        )
        assertEquals(items.size, items.map { it.key }.toSet().size)
    }

    @Test
    fun `fromRoute reads the three values and nothing else`() {
        assertEquals(InboxSection.WAITING_ON_ME, InboxSection.fromRoute("waiting"))
        assertEquals(InboxSection.WAITING_ON_THEM, InboxSection.fromRoute("sent"))
        assertEquals(InboxSection.HISTORY, InboxSection.fromRoute("history"))
        assertNull(InboxSection.fromRoute(null))
        assertNull(InboxSection.fromRoute("null"))
        assertNull(InboxSection.fromRoute(""))
        assertNull(InboxSection.fromRoute("HISTORY"))
    }
}
