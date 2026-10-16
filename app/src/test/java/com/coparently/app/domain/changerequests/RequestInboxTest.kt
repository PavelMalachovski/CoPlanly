package com.coparently.app.domain.changerequests

import com.coparently.app.domain.custody.CustodyProposal
import com.coparently.app.domain.custody.DayOverride
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
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The inbox's three sections (phase 2, spec §6.2): what waits on this parent, what waits on the
 * co-parent, and how every past request ended — and, since phase 3a (spec §4.1), where each request
 * stands against its clock.
 */
class RequestInboxTest {

    private val scope = RequestScope(myUid = "u1", coParentUid = "u2", familyId = "u1__u2", soleFamily = false)

    /** The same family on Bob's phone. */
    private val bobsScope = RequestScope(myUid = "u2", coParentUid = "u1", familyId = "u1__u2", soleFamily = false)
    private val today = LocalDate.of(2026, 10, 14)
    private val created = LocalDateTime.of(2026, 10, 10, 9, 0)

    private fun request(
        id: String,
        from: String,
        to: String,
        status: ChangeRequestStatus = ChangeRequestStatus.PENDING,
        createdAt: LocalDateTime = created
    ) = ChangeRequest(
        id = id,
        eventId = "e-$id",
        eventTitle = "Swimming",
        requestedBy = from,
        requestedTo = to,
        currentStartDateTime = LocalDateTime.of(2026, 10, 15, 17, 0),
        proposedStartDateTime = LocalDateTime.of(2026, 10, 16, 18, 30),
        status = status,
        createdAt = createdAt,
        respondedAt = created.plusDays(1).takeIf { status != ChangeRequestStatus.PENDING }
    )

    private fun event(id: String, creator: String, start: LocalDateTime = LocalDateTime.of(2026, 10, 21, 16, 0)) =
        Event(
            id = id,
            title = id,
            startDateTime = start,
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

    private val requests = listOf(request("theirs", from = "u2", to = "u1"), request("mine", from = "u1", to = "u2"))
    private val events = listOf(event("for-me", creator = "u2"), event("for-them", creator = "u1"))
    private val overrides = mapOf(
        "2026-10-20" to swap(requestedBy = "u2", groupId = "theirs"),
        "2026-10-22" to swap(requestedBy = "u1", groupId = "mine")
    )

    /**
     * The clock of the request [ref] names, written by its author [from] for the other parent, due at
     * [DEADLINE] with no cap: the last chance can be given for a day after it.
     */
    private fun clock(kind: RequestKind, ref: String, from: String) = RequestDeadline(
        id = RequestDeadlineId.of(kind, from, ref, FAMILY),
        kind = kind,
        ref = ref,
        familyId = FAMILY,
        proposerUid = from,
        responderUid = scope.otherOf(from),
        answerByMillis = DEADLINE
    )

    /** A clock for every pending request of the fixture, the co-parent's proposal included. */
    private val fixtureClocks = listOf(
        clock(RequestKind.CHANGE, "theirs", from = "u2"),
        clock(RequestKind.CHANGE, "mine", from = "u1"),
        clock(RequestKind.EVENT, "for-me", from = "u2"),
        clock(RequestKind.EVENT, "for-them", from = "u1"),
        clock(RequestKind.SWAP, "theirs", from = "u2"),
        clock(RequestKind.SWAP, "mine", from = "u1"),
        clock(RequestKind.PROPOSAL, PROPOSED_AT, from = "u2")
    )

    /** The fixture with the co-parent's proposal, on [viewer]'s phone, with [clocks] seen at [at]. */
    private fun inbox(at: Long, clocks: List<RequestDeadline> = fixtureClocks, viewer: RequestScope = scope) =
        RequestInbox.build(viewer, requests, events, overrides, proposalBy("u2"), today, ClocksAt.of(clocks, at))

    /** The fixture with the co-parent's proposal and no clock at all. */
    private val unclocked get() = RequestInbox.build(scope, requests, events, overrides, proposalBy("u2"), today)

    @Test
    fun `what waits on me is the co-parent's proposal, their events, swaps offered to me and their requests`() {
        val inbox = RequestInbox.build(scope, requests, events, overrides, proposalBy("u2"), today)

        assertEquals("u2", inbox.waitingOnMe.proposal?.proposedBy)
        assertEquals(listOf("for-me"), inbox.waitingOnMe.events.map { it.id })
        assertEquals(listOf(LocalDate.of(2026, 10, 20)), inbox.waitingOnMe.swaps.map { it.firstDate })
        assertEquals(listOf("theirs"), inbox.waitingOnMe.changeRequests.map { it.id })
        assertNull(inbox.waitingOnThem.proposal)
    }

    @Test
    fun `what waits on them is my proposal, my pending events, my pending swap offers and my pending requests`() {
        val inbox = RequestInbox.build(scope, requests, events, overrides, proposalBy("u1"), today)

        assertEquals("u1", inbox.waitingOnThem.proposal?.proposedBy)
        assertEquals(listOf("for-them"), inbox.waitingOnThem.events.map { it.id })
        assertEquals(listOf(LocalDate.of(2026, 10, 22)), inbox.waitingOnThem.swaps.map { it.firstDate })
        assertEquals(listOf("mine"), inbox.waitingOnThem.changeRequests.map { it.id })
        assertNull(inbox.waitingOnMe.proposal)
    }

    @Test
    fun `an answered item leaves both waiting sections and appears in history`() {
        val inbox = RequestInbox.build(
            scope,
            changeRequests = listOf(request("answered", from = "u1", to = "u2", status = ChangeRequestStatus.ACCEPTED)),
            events = emptyList(),
            overrides = emptyMap(),
            shared = null,
            today = today
        )

        assertTrue(inbox.waitingOnMe.isEmpty)
        assertTrue(inbox.waitingOnThem.isEmpty)
        assertEquals(listOf("cr_answered"), inbox.history.map { it.key })
    }

    @Test
    fun `a swap I offered never waits on me`() {
        val inbox = RequestInbox.build(
            scope,
            changeRequests = emptyList(),
            events = emptyList(),
            overrides = mapOf("2026-10-22" to swap(requestedBy = "u1", groupId = "mine")),
            shared = null,
            today = today
        )

        assertEquals(emptyList(), inbox.waitingOnMe.swaps)
        assertEquals(1, inbox.waitingOnThem.swaps.size)
    }

    @Test
    fun `count includes the proposal and countWithoutProposal does not`() {
        val inbox = RequestInbox.build(
            scope,
            changeRequests = emptyList(),
            events = listOf(event("for-me", creator = "u2")),
            overrides = mapOf("2026-10-20" to swap(requestedBy = "u2", groupId = "theirs")),
            shared = proposalBy("u2"),
            today = today
        )

        assertEquals(3, inbox.waitingOnMe.count)
        assertEquals(2, inbox.waitingOnMe.countWithoutProposal)
        assertEquals(0, inbox.waitingOnThem.count)
    }

    @Test
    fun `waiting items keep the inbox's order, events by start and requests by creation`() {
        val inbox = RequestInbox.build(
            scope,
            changeRequests = listOf(
                request("later", from = "u2", to = "u1", createdAt = created.plusHours(2)),
                request("earlier", from = "u2", to = "u1", createdAt = created)
            ),
            events = listOf(
                event("second", creator = "u2", start = LocalDateTime.of(2026, 10, 25, 9, 0)),
                event("first", creator = "u2", start = LocalDateTime.of(2026, 10, 18, 9, 0))
            ),
            overrides = emptyMap(),
            shared = null,
            today = today
        )

        assertEquals(listOf("first", "second"), inbox.waitingOnMe.events.map { it.id })
        assertEquals(listOf("earlier", "later"), inbox.waitingOnMe.changeRequests.map { it.id })
    }

    @Test
    fun `an inbox is empty only when all three sections are`() {
        assertTrue(RequestInbox.build(scope, emptyList(), emptyList(), emptyMap(), null, today).isEmpty)
        assertFalse(
            RequestInbox.build(
                scope,
                changeRequests = listOf(
                    request("answered", from = "u2", to = "u1", status = ChangeRequestStatus.DECLINED)
                ),
                events = emptyList(),
                overrides = emptyMap(),
                shared = null,
                today = today
            ).isEmpty
        )
    }

    @Test
    fun `an open request waits where it always did`() {
        val open = inbox(at = NOW)

        assertEquals(unclocked.waitingOnMe, open.waitingOnMe)
        assertEquals(unclocked.waitingOnThem, open.waitingOnThem)
        assertEquals(emptyList(), open.history)
        assertEquals(fixtureClocks.map { it.id }.toSet(), open.timed.keys)
        assertTrue(open.timed.values.all { it.state == ClockState.OPEN })
        assertEquals(ClockState.OPEN, open.timedFor(RequestDeadlineId.forChange(requests.first()))?.state)
        assertNull(open.timedFor(null))
        assertEquals(emptySet(), open.unansweredSwapDates)
    }

    @Test
    fun `past the deadline with the button still offered, it waits on the proposer on both phones`() {
        for (viewer in listOf(scope, bobsScope)) {
            val past = inbox(at = PAST_DEADLINE, viewer = viewer)

            assertTrue(past.waitingOnMe.isEmpty, "nothing waits on ${viewer.myUid}'s answer")
            assertEquals("u2", past.waitingOnThem.proposal?.proposedBy)
            assertEquals(listOf("for-me", "for-them"), past.waitingOnThem.events.map { it.id })
            assertEquals(
                listOf(LocalDate.of(2026, 10, 20), LocalDate.of(2026, 10, 22)),
                past.waitingOnThem.swaps.map { it.firstDate }
            )
            assertEquals(listOf("theirs", "mine"), past.waitingOnThem.changeRequests.map { it.id })
            assertEquals(emptyList(), past.history)
            assertTrue(past.timed.values.all { it.state == ClockState.NO_ANSWER_LAST_CHANCE_POSSIBLE })
        }
    }

    @Test
    fun `under a last chance it waits on the answering parent again`() {
        val lastChance = fixtureClocks.map { it.copy(lastChanceUntilMillis = LAST_CHANCE_UNTIL) }

        val given = inbox(at = PAST_DEADLINE, clocks = lastChance)

        assertEquals(unclocked.waitingOnMe, given.waitingOnMe)
        assertEquals(unclocked.waitingOnThem, given.waitingOnThem)
        assertEquals(emptyList(), given.history)
        assertTrue(given.timed.values.all { it.state == ClockState.LAST_CHANCE })
    }

    @Test
    fun `after the end it is "No answer" in History on both phones`() {
        val keys = setOf(
            "cr_theirs",
            "cr_mine",
            "ev_for-me",
            "ev_for-them",
            "swap_theirs",
            "swap_mine",
            "custody_$PROPOSED_AT"
        )
        for (viewer in listOf(scope, bobsScope)) {
            val over = inbox(at = AFTER_END, viewer = viewer)

            assertTrue(over.waitingOnMe.isEmpty, "nothing waits on ${viewer.myUid}")
            assertTrue(over.waitingOnThem.isEmpty, "nothing waits on ${viewer.coParentUid}")
            assertEquals(keys, over.history.map { it.key }.toSet())
            assertTrue(over.history.all { it.outcome == RequestOutcome.NO_ANSWER && it.clock != null })
            // Asked by the proposer and left by the other parent, whichever phone reads it — the
            // pending proposal included, whose clock nobody has closed yet.
            assertEquals(
                mapOf(
                    "cr_theirs" to ("u2" to "u1"),
                    "cr_mine" to ("u1" to "u2"),
                    "custody_$PROPOSED_AT" to ("u2" to "u1")
                ),
                over.history
                    .filter { it.key in setOf("cr_theirs", "cr_mine", "custody_$PROPOSED_AT") }
                    .associate { it.key to (it.askedBy to it.decidedBy) }
            )
            assertEquals(fixtureClocks.map { it.id }.toSet(), over.timed.keys)
        }
    }

    @Test
    fun `a request without a clock behaves as before`() {
        // A single day an older build offered carries no group id, so it cannot name a clock at all.
        val withOlderOffer = overrides +
            ("2026-10-24" to DayOverride(toParent = "dad", requestedBy = "u2", requestedAt = "2026-10-13T10:00:00"))

        val plain = RequestInbox.build(scope, requests, events, withOlderOffer, proposalBy("u2"), today)
        val late = RequestInbox.build(
            scope,
            requests,
            events,
            withOlderOffer,
            proposalBy("u2"),
            today,
            ClocksAt.of(emptyList(), AFTER_END)
        )

        assertEquals(plain, late)
        assertEquals(
            listOf(LocalDate.of(2026, 10, 20), LocalDate.of(2026, 10, 24)),
            late.waitingOnMe.swaps.map { it.firstDate }
        )
        assertEquals(emptyMap(), late.timed)
    }

    @Test
    fun `a clock that does not name the record's author is ignored`() {
        // Filed under Bob's requests, but written the other way round, or naming somebody else.
        val forged = listOf(
            clock(RequestKind.CHANGE, "theirs", from = "u2").copy(
                proposerUid = "u1",
                responderUid = "u2",
                outcome = RequestClockOutcome.NO_ANSWER
            ),
            clock(RequestKind.EVENT, "for-me", from = "u2").copy(
                responderUid = "u3",
                outcome = RequestClockOutcome.NO_ANSWER
            ),
            clock(RequestKind.PROPOSAL, PROPOSED_AT, from = "u2").copy(proposerUid = "u1", responderUid = "u2")
        )

        assertEquals(unclocked, inbox(at = AFTER_END, clocks = forged))
    }

    @Test
    fun `a clock of the family not on screen changes nothing here`() {
        // Alice also co-parents with Carol (u3). Clocks of that family, closed or long past their
        // ends — even one filed under a request of this family — move nothing in this one.
        val carols = "u1__u3"
        val elsewhere = listOf(
            RequestDeadline(
                id = RequestDeadlineId.of(RequestKind.CHANGE, "u3", "carols", carols),
                kind = RequestKind.CHANGE,
                ref = "carols",
                familyId = carols,
                proposerUid = "u3",
                responderUid = "u1",
                answerByMillis = DEADLINE,
                outcome = RequestClockOutcome.NO_ANSWER
            ),
            RequestDeadline(
                id = RequestDeadlineId.of(RequestKind.PROPOSAL, "u1", PROPOSED_AT, carols),
                kind = RequestKind.PROPOSAL,
                ref = PROPOSED_AT,
                familyId = carols,
                proposerUid = "u1",
                responderUid = "u3",
                answerByMillis = DEADLINE,
                outcome = RequestClockOutcome.NO_ANSWER
            ),
            RequestDeadline(
                id = RequestDeadlineId.of(RequestKind.SWAP, "u1", "carols-days", carols),
                kind = RequestKind.SWAP,
                ref = "carols-days",
                familyId = carols,
                proposerUid = "u1",
                responderUid = "u3",
                answerByMillis = DEADLINE,
                firstDate = LocalDate.of(2026, 10, 20),
                dayCount = 1,
                outcome = RequestClockOutcome.NO_ANSWER
            ),
            clock(RequestKind.CHANGE, "theirs", from = "u2").copy(familyId = carols)
        )

        val here = inbox(at = AFTER_END, clocks = elsewhere)

        assertEquals(unclocked, here)
        assertEquals(unclocked.waitingOnMe.count, here.waitingOnMe.count)
    }

    @Test
    fun `the days of an unanswered offer are listed for the calendar`() {
        val twoDays = swap(requestedBy = "u2", groupId = "two-days")
        val offers = mapOf(
            "2026-10-20" to twoDays,
            "2026-10-21" to twoDays,
            "2026-10-22" to swap(requestedBy = "u1", groupId = "mine"),
            "2026-10-25" to swap(requestedBy = "u2", groupId = "still-open")
        )
        val clocks = ClocksAt.of(
            listOf(
                // Past its deadline: the last chance is Bob's to give.
                clock(RequestKind.SWAP, "two-days", from = "u2"),
                // Over.
                clock(RequestKind.SWAP, "mine", from = "u1").copy(outcome = RequestClockOutcome.NO_ANSWER),
                // Still open.
                clock(RequestKind.SWAP, "still-open", from = "u2").copy(answerByMillis = PAST_DEADLINE + HOUR)
            ),
            PAST_DEADLINE
        )

        for (viewer in listOf(scope, bobsScope)) {
            val inbox = RequestInbox.build(viewer, emptyList(), emptyList(), offers, null, today, clocks)

            assertEquals(
                setOf(LocalDate.of(2026, 10, 20), LocalDate.of(2026, 10, 21), LocalDate.of(2026, 10, 22)),
                inbox.unansweredSwapDates
            )
        }
    }

    @Test
    fun `Home's count drops when a request stops waiting on this parent`() {
        val theirs = listOf(request("theirs", from = "u2", to = "u1"))
        val open = clock(RequestKind.CHANGE, "theirs", from = "u2")
        val lastChance = open.copy(lastChanceUntilMillis = LAST_CHANCE_UNTIL)
        val at = { moment: Long, clock: RequestDeadline ->
            RequestInbox.build(scope, theirs, emptyList(), emptyMap(), null, today, ClocksAt.of(listOf(clock), moment))
        }

        assertEquals(1, at(NOW, open).waitingOnMe.count)
        assertEquals(0, at(PAST_DEADLINE, open).waitingOnMe.count)
        assertEquals(1, at(PAST_DEADLINE, open).waitingOnThem.count)
        assertEquals(1, at(PAST_DEADLINE, lastChance).waitingOnMe.count)
        assertEquals(0, at(AFTER_END, open).waitingOnMe.count)
        assertEquals(0, at(AFTER_END, open).waitingOnThem.count)
    }

    private companion object {
        const val FAMILY = "u1__u2"

        /** When the co-parent's proposal of [proposalBy] was made: its clock's reference. */
        const val PROPOSED_AT = "2026-10-12T08:00:00"

        const val HOUR = 3_600_000L
        const val DAY = 24 * HOUR

        /** 2026-10-14T09:00:00Z: every clock of the fixture is still open. */
        const val NOW = 1_791_968_400_000L

        /** The fixture's deadline. */
        const val DEADLINE = NOW + HOUR

        /** Past the deadline, with a day of the proposer's last-chance button still to go. */
        const val PAST_DEADLINE = NOW + 2 * HOUR

        /** Where a last chance given just after the deadline ends. */
        const val LAST_CHANCE_UNTIL = NOW + 6 * HOUR

        /** Past the end of a clock whose last chance was never given. */
        const val AFTER_END = DEADLINE + DAY + HOUR
    }
}
