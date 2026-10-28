package com.coparently.app.presentation.changerequests

import com.coparently.app.domain.changerequests.ClockState
import com.coparently.app.domain.changerequests.ClocksAt
import com.coparently.app.domain.changerequests.RequestDeadline
import com.coparently.app.domain.changerequests.RequestDeadlineId
import com.coparently.app.domain.changerequests.RequestInbox
import com.coparently.app.domain.changerequests.RequestKind
import com.coparently.app.domain.changerequests.RequestScope
import com.coparently.app.domain.changerequests.TimedRequest
import com.coparently.app.domain.changerequests.Waiting
import com.coparently.app.domain.custody.CustodyProposal
import com.coparently.app.domain.custody.DayOverride
import com.coparently.app.domain.custody.DaySwap
import com.coparently.app.domain.custody.DaySwapGroup
import com.coparently.app.domain.custody.SharedCustody
import com.coparently.app.domain.events.EventAcceptance
import com.coparently.app.domain.expenses.FamilySettings
import com.coparently.app.domain.expenses.SplitRatio
import com.coparently.app.domain.expenses.SplitRatioProposal
import com.coparently.app.domain.model.ChangeRequest
import com.coparently.app.domain.model.ChangeRequestStatus
import com.coparently.app.domain.model.CustodyModel
import com.coparently.app.domain.model.Event
import com.coparently.app.domain.professionals.ProfessionalGrant
import com.coparently.app.domain.professionals.ProfessionalRole
import com.coparently.app.domain.support.SupportProposal
import com.coparently.app.domain.support.SupportTerms
import org.junit.Test
import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.YearMonth
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The inbox's one flat list (phase 2, spec §6.2): what its `LazyColumn` draws, in order, and the
 * only thing a scroll target is looked up in — so a chat card's highlight and a "History" link
 * land on the card they name, a pending proposal above them or not. Since phase 3a each request's
 * item carries its clock, for its card to read.
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

    /** The family's split document, agreed half each, with [uid]'s proposal of 35/65. */
    private fun splitBy(uid: String) = FamilySettings(
        ratio = SplitRatio.EVEN,
        participants = listOf("u1", "u2"),
        lastModifiedBy = uid,
        lastModifiedAtMillis = 1L,
        proposal = SplitRatioProposal(SplitRatio(THIRTY_FIVE), proposedBy = uid, proposedAtMillis = SPLIT_AT)
    )

    /**
     * Something in every section: the co-parent's proposal, one of each kind either way, a past
     * answer — with [clocks], none by default.
     */
    private fun fullInbox(clocks: ClocksAt = ClocksAt()) = RequestInbox.build(
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
        today = today,
        clocks = clocks
    )

    private val full = fullInbox()

    /** The clock of the request [ref] names, written by [from] for the other parent, due at [DEADLINE]. */
    private fun clock(kind: RequestKind, ref: String, from: String) = RequestDeadline(
        id = RequestDeadlineId.of(kind, from, ref, "u1__u2"),
        kind = kind,
        ref = ref,
        familyId = "u1__u2",
        proposerUid = from,
        responderUid = scope.otherOf(from),
        answerByMillis = DEADLINE
    )

    @Test
    fun `sections come in order with their items, and an empty section has no header`() {
        assertEquals(
            listOf(
                "h_waiting", "p_theirs", "ev_for-me", "sw_g-theirs", "cr_theirs",
                "h_sent", "ev_for-them", "sw_g-mine", "cr_mine",
                "h_history", "history"
            ),
            InboxItems.of(full, scope).map { it.key }
        )

        val onlyTheirs = RequestInbox.build(
            scope,
            changeRequests = listOf(request("theirs", from = "u2", to = "u1")),
            events = emptyList(),
            overrides = emptyMap(),
            shared = null,
            today = today
        )
        assertEquals(listOf("h_waiting", "cr_theirs"), InboxItems.of(onlyTheirs, scope).map { it.key })
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

        assertEquals(2, InboxItems.indexOfRequest(InboxItems.of(inbox, scope), "r1"))
    }

    @Test
    fun `an answered request is found at the History item`() {
        val items = InboxItems.of(full, scope)

        assertEquals(items.indexOfFirst { it is InboxItem.History }, InboxItems.indexOfRequest(items, "answered"))
        assertEquals(items.indexOfFirst { it.key == "cr_mine" }, InboxItems.indexOfRequest(items, "mine"))
        assertEquals(-1, InboxItems.indexOfRequest(items, "nowhere"))
    }

    @Test
    fun `the history header can be scrolled to`() {
        val items = InboxItems.of(full, scope)

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
            ),
            scope
        )
        assertEquals(-1, InboxItems.indexOf(noHistory, InboxSection.HISTORY))
    }

    @Test
    fun `arriving for a section scrolls to its heading, and stays put when it is not drawn`() {
        val items = InboxItems.of(full, scope)

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
        val items = InboxItems.of(full, scope)

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
            InboxItem.Split(splitBy("u2").proposal!!, mine = false),
            InboxItem.Split(splitBy("u1").proposal!!, mine = true),
            InboxItem.AwaitingEvent(event("e1", creator = "u2"), mine = false),
            InboxItem.Swap(group),
            InboxItem.Request(request("r1", from = "u2", to = "u1")),
            InboxItem.History(emptyList())
        )

        assertEquals(
            listOf(
                "h_waiting", "h_sent", "h_history", "p_theirs", "p_mine", "s_theirs", "s_mine",
                "ev_e1", "sw_g1", "cr_r1", "history"
            ),
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

    @Test
    fun `each item carries its clock`() {
        val clocks = listOf(
            clock(RequestKind.PROPOSAL, "2026-10-12T08:00:00", from = "u2"),
            clock(RequestKind.EVENT, "for-me", from = "u2"),
            clock(RequestKind.SWAP, "g-theirs", from = "u2"),
            clock(RequestKind.CHANGE, "theirs", from = "u2"),
            clock(RequestKind.EVENT, "for-them", from = "u1"),
            clock(RequestKind.SWAP, "g-mine", from = "u1"),
            clock(RequestKind.CHANGE, "mine", from = "u1")
        )

        val items = InboxItems.of(fullInbox(ClocksAt.of(clocks, NOW)), scope)

        assertEquals(
            listOf(
                "h_waiting" to null,
                "p_theirs" to clocks[0].id,
                "ev_for-me" to clocks[1].id,
                "sw_g-theirs" to clocks[2].id,
                "cr_theirs" to clocks[3].id,
                "h_sent" to null,
                "ev_for-them" to clocks[4].id,
                "sw_g-mine" to clocks[5].id,
                "cr_mine" to clocks[6].id,
                "h_history" to null,
                "history" to null
            ),
            items.map { it.key to it.timedOrNull()?.clock?.id }
        )
        assertTrue(items.mapNotNull { it.timedOrNull() }.all { it.nowMillis == NOW && it.state == ClockState.OPEN })
        // The same inbox without clocks carries none.
        assertTrue(InboxItems.of(full, scope).all { it.timedOrNull() == null })
    }

    @Test
    fun `a request past its deadline is still the co-parent's on its card, under their name`() {
        // Bob's proposal and event, past their deadlines with his last chance still to give: they
        // wait in his section on Alice's phone, but she did not make them, and her cards must not
        // say she did.
        val clocks = ClocksAt.of(
            listOf(
                clock(RequestKind.PROPOSAL, "2026-10-12T08:00:00", from = "u2"),
                clock(RequestKind.EVENT, "for-me", from = "u2")
            ),
            PAST_DEADLINE
        )
        val inbox = RequestInbox.build(
            scope,
            changeRequests = emptyList(),
            events = listOf(event("for-me", creator = "u2")),
            overrides = emptyMap(),
            shared = proposalBy("u2"),
            today = today,
            clocks = clocks
        )

        val items = InboxItems.of(inbox, scope)

        assertEquals(listOf("h_sent", "p_theirs", "ev_for-me"), items.map { it.key })
        assertFalse(assertIs<InboxItem.Proposal>(items[1]).mine)
        assertFalse(assertIs<InboxItem.AwaitingEvent>(items[2]).mine)
    }

    @Test
    fun `a proposed split comes right after the custody proposal, on the side of whoever must answer it`() {
        val theirs = RequestInbox.build(
            scope,
            changeRequests = listOf(request("theirs", from = "u2", to = "u1")),
            events = emptyList(),
            overrides = emptyMap(),
            shared = proposalBy("u2"),
            today = today,
            settings = splitBy("u2")
        )
        val theirItems = InboxItems.of(theirs, scope)
        assertEquals(listOf("h_waiting", "p_theirs", "s_theirs", "cr_theirs"), theirItems.map { it.key })
        assertFalse(assertIs<InboxItem.Split>(theirItems[2]).mine)

        val mine = RequestInbox.build(
            scope,
            changeRequests = emptyList(),
            events = emptyList(),
            overrides = emptyMap(),
            shared = null,
            today = today,
            settings = splitBy("u1")
        )
        val myItems = InboxItems.of(mine, scope)
        assertEquals(listOf("h_sent", "s_mine"), myItems.map { it.key })
        assertTrue(assertIs<InboxItem.Split>(myItems[1]).mine)
    }

    @Test
    fun `a proposed split carries its clock, named in its family`() {
        val split = splitBy("u2")
        val splitClock = clock(RequestKind.SPLIT, SPLIT_AT.toString(), from = "u2")
        assertEquals(RequestDeadlineId.forSplit(split.proposal!!, "u1__u2"), splitClock.id)
        val inbox = RequestInbox.build(
            scope,
            changeRequests = emptyList(),
            events = emptyList(),
            overrides = emptyMap(),
            shared = null,
            today = today,
            clocks = ClocksAt.of(listOf(splitClock), NOW),
            settings = split
        )

        val item = assertIs<InboxItem.Split>(InboxItems.of(inbox, scope)[1])

        assertEquals(splitClock.id, item.timed?.clock?.id)
        assertEquals(ClockState.OPEN, item.timed?.state)
    }

    /** The clock an item of a request kind carries; null for a header, the history, or no clock. */
    private fun InboxItem.timedOrNull(): TimedRequest? = when (this) {
        is InboxItem.Proposal -> timed
        is InboxItem.Split -> timed
        is InboxItem.AwaitingEvent -> timed
        is InboxItem.Swap -> timed
        is InboxItem.Request -> timed
        is InboxItem.Header, is InboxItem.History,
        is InboxItem.Support, is InboxItem.Professional, is InboxItem.Plan -> null
    }

    private companion object {
        const val HOUR = 3_600_000L

        /** 35 % for slot 1, in basis points. */
        const val THIRTY_FIVE = 3_500

        /** When the split was proposed: 2026-10-13T09:00:00Z. */
        const val SPLIT_AT = 1_791_882_000_000L

        /** 2026-10-14T09:00:00Z: every clock here is open. */
        const val NOW = 1_791_968_400_000L

        /** The deadline of every clock here. */
        const val DEADLINE = NOW + HOUR

        /** Past the deadline, with the proposer's last-chance button still offered. */
        const val PAST_DEADLINE = NOW + 2 * HOUR
    }

    private val supportTerms = SupportTerms(BigDecimal("3000"), "CZK", 15, "u2", YearMonth.of(2026, 11))

    private fun grantAwaiting(consentBy: String) = ProfessionalGrant(
        id = "u1__u2__pro",
        familyId = "u1__u2",
        familyParents = listOf("u1", "u2"),
        proUid = "pro",
        role = ProfessionalRole.MEDIATOR,
        name = "Dana",
        invitedBy = consentBy,
        grantedAtMillis = 1L,
        expiresAtMillis = Long.MAX_VALUE,
        consents = mapOf(consentBy to 1L)
    )

    @Test
    fun agreementRowsCloseTheirSection() {
        val split = SplitRatioProposal(SplitRatio(3_500), proposedBy = "u2", proposedAtMillis = 1L)
        val inbox = RequestInbox(
            waitingOnMe = Waiting(
                split = split,
                changeRequests = listOf(request("r1", from = "u2", to = "u1")),
                support = SupportProposal(supportTerms, "u2", 1L),
                professionals = listOf(grantAwaiting("u2")),
                planQuestions = listOf("care_weekday", "holidays_school")
            )
        )

        val items = InboxItems.of(inbox, scope)

        assertEquals(
            listOf("h_waiting", "s_theirs", "cr_r1", "sup_theirs", "pro_u1__u2__pro", "plan"),
            items.map { it.key }
        )
        assertEquals(2, assertIs<InboxItem.Plan>(items.last()).questionCount)
        assertFalse(assertIs<InboxItem.Support>(items[3]).mine)
        assertFalse(assertIs<InboxItem.Professional>(items[4]).mine)
    }

    @Test
    fun minePlacesWithdrawRowsUnderThem() {
        val inbox = RequestInbox(
            waitingOnThem = Waiting(
                support = SupportProposal(supportTerms, "u1", 1L),
                professionals = listOf(grantAwaiting("u1"))
            )
        )

        val items = InboxItems.of(inbox, scope)

        assertEquals(listOf("h_sent", "sup_mine", "pro_u1__u2__pro"), items.map { it.key })
        assertTrue(assertIs<InboxItem.Support>(items[1]).mine)
        assertTrue(assertIs<InboxItem.Professional>(items[2]).mine)
    }
}
