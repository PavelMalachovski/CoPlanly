package com.coparently.app.domain.changerequests

import com.coparently.app.domain.custody.CustodyDecision
import com.coparently.app.domain.custody.CustodyDecisionOutcome
import com.coparently.app.domain.custody.DayOverride
import com.coparently.app.domain.custody.DayOverrideDecisionCodec
import com.coparently.app.domain.custody.DayOverrideStatus
import com.coparently.app.domain.events.EventAcceptance
import com.coparently.app.domain.expenses.SplitRatioDecision
import com.coparently.app.domain.expenses.SplitRatioOutcome
import com.coparently.app.domain.model.ChangeRequest
import com.coparently.app.domain.model.ChangeRequestStatus
import com.coparently.app.domain.model.Event
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The inbox's history (phase 2, spec §6.2): every request answered or withdrawn, derived from the
 * records both phones already hold — never a stored history list that could disagree with them —
 * and, since phase 3a (spec §4.4, §6.6), every request nobody answered by the end of its clock.
 */
class RequestHistoryTest {

    private val scope = RequestScope(myUid = "u1", coParentUid = "u2", familyId = "u1__u2", soleFamily = false)
    private val created = LocalDateTime.of(2026, 10, 1, 9, 0)

    @Suppress("LongParameterList") // one request's fields, all defaulted
    private fun request(
        id: String,
        from: String = "u1",
        to: String = "u2",
        status: ChangeRequestStatus,
        respondedAt: LocalDateTime? = null,
        synced: Boolean = true
    ) = ChangeRequest(
        id = id,
        eventId = "e-$id",
        eventTitle = "Swimming",
        requestedBy = from,
        requestedTo = to,
        currentStartDateTime = LocalDateTime.of(2026, 10, 15, 17, 0),
        proposedStartDateTime = LocalDateTime.of(2026, 10, 16, 18, 30),
        status = status,
        createdAt = created,
        respondedAt = respondedAt,
        syncedToFirestore = synced
    )

    private fun swap(
        status: DayOverrideStatus,
        requestedBy: String = "u1",
        decidedBy: String? = "u2".takeIf { status != DayOverrideStatus.PENDING },
        decidedAt: String? = "2026-09-02T20:00:00".takeIf { status != DayOverrideStatus.PENDING },
        groupId: String? = "g1"
    ) = DayOverride(
        toParent = "dad",
        requestedBy = requestedBy,
        requestedAt = "2026-08-30T10:00:00",
        status = status,
        decidedBy = decidedBy,
        decidedAt = decidedAt,
        groupId = groupId
    )

    private fun event(
        id: String,
        acceptance: EventAcceptance,
        acceptedAt: LocalDateTime? = null,
        familyId: String = "u1__u2"
    ) = Event(
        id = id,
        title = "Dentist",
        startDateTime = LocalDateTime.of(2026, 10, 21, 16, 0),
        eventType = "medical",
        parentOwner = "dad",
        createdAt = created,
        updatedAt = created,
        createdByFirebaseUid = "u1",
        familyId = familyId,
        acceptance = acceptance,
        acceptedBy = "u2".takeIf { acceptedAt != null },
        acceptedAt = acceptedAt
    )

    private fun history(
        changeRequests: List<ChangeRequest> = emptyList(),
        events: List<Event> = emptyList(),
        overrides: Map<String, DayOverride> = emptyMap(),
        decision: CustodyDecision? = null,
        clocks: ClocksAt = ClocksAt()
    ) = RequestHistory.build(scope, changeRequests, events, overrides, decision, clocks)

    /** [time] on this device's clock as epoch millis — the zone the history reads a clock's times in. */
    private fun millisOf(time: LocalDateTime): Long = time.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()

    /** The device's local time at [millis]. */
    private fun localOf(millis: Long): LocalDateTime =
        Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).toLocalDateTime()

    /**
     * The clock of the request [ref] names, written by its author [from] for the other parent, due at
     * [DEADLINE] with no cap, so it ends a day later unless a last chance was given.
     */
    private fun clock(kind: RequestKind, ref: String, from: String = "u1") = RequestDeadline(
        id = RequestDeadlineId.of(kind, from, ref, "u1__u2"),
        kind = kind,
        ref = ref,
        familyId = "u1__u2",
        proposerUid = from,
        responderUid = scope.otherOf(from),
        answerByMillis = millisOf(DEADLINE)
    )

    /** [clock] as the proposer's phone closed it at [CLOSED_AT]. */
    private fun closed(kind: RequestKind, ref: String, from: String = "u1") =
        clock(kind, ref, from).copy(outcome = RequestClockOutcome.NO_ANSWER, closedAtMillis = millisOf(CLOSED_AT))

    /** [clocks] seen at [LATER], well past every clock's end. */
    private fun seenLater(vararg clocks: RequestDeadline) = ClocksAt.of(clocks.toList(), millisOf(LATER))

    @Test
    fun `my request the co-parent accepted is answered by them at respondedAt`() {
        val answered = LocalDateTime.of(2026, 10, 2, 8, 15)

        val entries = history(listOf(request("r1", status = ChangeRequestStatus.ACCEPTED, respondedAt = answered)))

        assertEquals(
            listOf(
                HistoryEntry(
                    key = "cr_r1",
                    subject = RequestSubject.NewTime("e-r1", "Swimming", LocalDateTime.of(2026, 10, 16, 18, 30)),
                    outcome = RequestOutcome.ACCEPTED,
                    askedBy = "u1",
                    decidedBy = "u2",
                    decidedAt = answered
                )
            ),
            entries
        )
    }

    @Test
    fun `a withdrawn request without respondedAt is dated by its creation`() {
        val entry = history(listOf(request("r1", status = ChangeRequestStatus.CANCELLED))).single()

        assertEquals(RequestOutcome.WITHDRAWN, entry.outcome)
        assertEquals("u1", entry.decidedBy)
        assertEquals(created, entry.decidedAt)
    }

    @Test
    fun `a pending request is not history`() {
        assertEquals(emptyList(), history(listOf(request("r1", status = ChangeRequestStatus.PENDING))))
    }

    @Test
    fun `a swap offered as one group is one entry with its first date and day count, past days included`() {
        val decided = swap(DayOverrideStatus.ACCEPTED)

        val entries = history(
            overrides = mapOf("2026-09-01" to decided, "2026-09-02" to decided, "2026-09-03" to decided)
        )

        assertEquals(
            listOf(
                HistoryEntry(
                    key = "swap_g1",
                    subject = RequestSubject.DaySwap(LocalDate.of(2026, 9, 1), 3),
                    outcome = RequestOutcome.ACCEPTED,
                    askedBy = "u1",
                    decidedBy = "u2",
                    decidedAt = LocalDateTime.of(2026, 9, 2, 20, 0),
                    swapDays = (1..3).associate { LocalDate.of(2026, 9, it) to "dad" }
                )
            ),
            entries
        )
    }

    @Test
    fun `a swap group with a day still pending is not history`() {
        val entries = history(
            overrides = mapOf(
                "2026-09-01" to swap(DayOverrideStatus.ACCEPTED),
                "2026-09-02" to swap(DayOverrideStatus.PENDING)
            )
        )

        assertEquals(emptyList(), entries)
    }

    @Test
    fun `a swap the offering parent withdrew is a withdrawn row credited to them`() {
        val withdrawn = swap(DayOverrideStatus.CANCELLED, decidedBy = "u1")

        val entries = history(overrides = mapOf("2026-09-01" to withdrawn, "2026-09-02" to withdrawn))

        assertEquals(
            listOf(
                HistoryEntry(
                    key = "swap_g1",
                    subject = RequestSubject.DaySwap(LocalDate.of(2026, 9, 1), 2),
                    outcome = RequestOutcome.WITHDRAWN,
                    askedBy = "u1",
                    decidedBy = "u1",
                    decidedAt = LocalDateTime.of(2026, 9, 2, 20, 0)
                )
            ),
            entries
        )
    }

    @Test
    fun `a swap decided by an older build without decidedAt is dated by requestedAt`() {
        val missing = swap(DayOverrideStatus.DECLINED, decidedAt = null, groupId = null)
        val garbage = swap(DayOverrideStatus.DECLINED, decidedAt = "garbage", groupId = null)

        val entries = history(overrides = mapOf("2026-09-05" to missing, "2026-09-06" to garbage))

        assertEquals(2, entries.size)
        assertTrue(entries.all { it.decidedAt == LocalDateTime.of(2026, 8, 30, 10, 0) })
        assertTrue(entries.all { it.outcome == RequestOutcome.DECLINED })
    }

    @Test
    fun `an event the co-parent declined is history and a pending one is not`() {
        val answered = LocalDateTime.of(2026, 10, 3, 19, 0)

        val entries = history(
            events = listOf(
                event("declined", EventAcceptance.DECLINED, acceptedAt = answered),
                event("pending", EventAcceptance.PENDING)
            )
        )

        assertEquals(
            listOf(
                HistoryEntry(
                    key = "ev_declined",
                    subject = RequestSubject.NewEvent("declined", "Dentist", LocalDateTime.of(2026, 10, 21, 16, 0)),
                    outcome = RequestOutcome.DECLINED,
                    askedBy = "u1",
                    decidedBy = "u2",
                    decidedAt = answered
                )
            ),
            entries
        )
    }

    @Test
    fun `the custody decision is one entry, asked by the other parent`() {
        val decision = CustodyDecision(
            outcome = CustodyDecisionOutcome.DECLINED,
            by = "u2",
            at = "2026-10-04T12:30:00",
            proposalAt = "2026-10-04T09:00:00",
            note = null
        )

        assertEquals(
            listOf(
                HistoryEntry(
                    key = "custody_2026-10-04T09:00:00",
                    subject = RequestSubject.CustodySchedule,
                    outcome = RequestOutcome.DECLINED,
                    askedBy = "u1",
                    decidedBy = "u2",
                    decidedAt = LocalDateTime.of(2026, 10, 4, 12, 30)
                )
            ),
            history(decision = decision)
        )
    }

    @Test
    fun `another family's request and event are left out`() {
        val entries = history(
            changeRequests = listOf(
                request("r1", to = "u3", status = ChangeRequestStatus.ACCEPTED),
                request("r2", from = "u3", to = "u1", status = ChangeRequestStatus.DECLINED)
            ),
            events = listOf(event("other", EventAcceptance.ACCEPTED, acceptedAt = created, familyId = "u1__u3"))
        )

        assertEquals(emptyList(), entries)
    }

    @Test
    fun `entries are newest first`() {
        val entries = history(
            changeRequests = listOf(
                request(
                    "older",
                    status = ChangeRequestStatus.ACCEPTED,
                    respondedAt = LocalDateTime.of(2026, 10, 2, 8, 0)
                ),
                request(
                    "newer",
                    status = ChangeRequestStatus.DECLINED,
                    respondedAt = LocalDateTime.of(2026, 10, 5, 8, 0)
                )
            ),
            events = listOf(event("middle", EventAcceptance.ACCEPTED, acceptedAt = LocalDateTime.of(2026, 10, 3, 8, 0)))
        )

        assertEquals(listOf("cr_newer", "ev_middle", "cr_older"), entries.map { it.key })
    }

    @Test
    fun `an answer this phone has not uploaded is marked queued`() {
        val entries = history(
            listOf(
                request("mine", from = "u2", to = "u1", status = ChangeRequestStatus.DECLINED, synced = false),
                request("theirs", status = ChangeRequestStatus.ACCEPTED, synced = true)
            )
        )

        assertEquals(mapOf("cr_mine" to true, "cr_theirs" to false), entries.associate { it.key to it.queued })
    }

    @Test
    fun `"No answer" is its own outcome, dated by its closure`() {
        val pending = request("r1", status = ChangeRequestStatus.PENDING)
        val closedClock = closed(RequestKind.CHANGE, "r1")

        assertEquals(
            listOf(
                HistoryEntry(
                    key = "cr_r1",
                    subject = RequestSubject.NewTime("e-r1", "Swimming", LocalDateTime.of(2026, 10, 16, 18, 30)),
                    outcome = RequestOutcome.NO_ANSWER,
                    askedBy = "u1",
                    decidedBy = "u2",
                    decidedAt = CLOSED_AT,
                    clock = closedClock
                )
            ),
            history(listOf(pending), clocks = seenLater(closedClock))
        )

        // Until the proposer's phone closes it, the row is dated by the clock's own end: a day after
        // the deadline, the last chance never given.
        val unclosed = clock(RequestKind.CHANGE, "r1")
        val entry = history(listOf(pending), clocks = seenLater(unclosed)).single()
        assertEquals(RequestOutcome.NO_ANSWER, entry.outcome)
        assertEquals(localOf(RequestClock.endMillis(unclosed)), entry.decidedAt)
        assertTrue(entry.decidedAt.isAfter(DEADLINE))
    }

    @Test
    fun `a late answer shows as the answer, not as "No answer"`() {
        // Each was closed as "No answer" first, then answered from a co-parent's older build.
        val answeredAt = CLOSED_AT.plusHours(10)
        val entries = history(
            changeRequests = listOf(request("r1", status = ChangeRequestStatus.DECLINED, respondedAt = answeredAt)),
            events = listOf(event("e1", EventAcceptance.ACCEPTED, acceptedAt = answeredAt)),
            overrides = mapOf("2026-10-20" to swap(DayOverrideStatus.ACCEPTED)),
            decision = CustodyDecision(
                outcome = CustodyDecisionOutcome.DECLINED,
                by = "u2",
                at = "2026-10-06T07:00:00",
                proposalAt = PROPOSED_AT,
                note = null
            ),
            clocks = seenLater(
                closed(RequestKind.CHANGE, "r1"),
                closed(RequestKind.EVENT, "e1"),
                // A swap's clock names its offer's days, as every one this build writes does.
                closed(RequestKind.SWAP, "g1").copy(firstDate = LocalDate.of(2026, 10, 20), dayCount = 1),
                closed(RequestKind.PROPOSAL, PROPOSED_AT)
            )
        )

        assertEquals(
            mapOf(
                "cr_r1" to RequestOutcome.DECLINED,
                "ev_e1" to RequestOutcome.ACCEPTED,
                "swap_g1" to RequestOutcome.ACCEPTED,
                "custody_$PROPOSED_AT" to RequestOutcome.DECLINED
            ),
            entries.associate { it.key to it.outcome }
        )
        assertEquals(4, entries.size)
    }

    @Test
    fun `a proposal withdrawn at its end stays in History from its clock`() {
        // The proposer's phone closed the clock and took the proposal back; the custody document's
        // last decision is an earlier proposal's.
        val withdrawn = closed(RequestKind.PROPOSAL, PROPOSED_AT, from = "u2")
        val earlier = CustodyDecision(
            outcome = CustodyDecisionOutcome.ACCEPTED,
            by = "u1",
            at = "2026-09-20T12:00:00",
            proposalAt = "2026-09-19T08:00:00",
            note = null
        )

        val entries = history(decision = earlier, clocks = seenLater(withdrawn))

        assertEquals(
            HistoryEntry(
                key = "custody_$PROPOSED_AT",
                subject = RequestSubject.CustodySchedule,
                outcome = RequestOutcome.NO_ANSWER,
                askedBy = "u2",
                decidedBy = "u1",
                decidedAt = CLOSED_AT,
                clock = withdrawn
            ),
            entries.first()
        )
        assertEquals(listOf("custody_$PROPOSED_AT", "custody_2026-09-19T08:00:00"), entries.map { it.key })
    }

    @Test
    fun `a swap offered again keeps its "No answer" row from the clock's first day and count`() {
        // Alice offered two days, Bob never answered, and she offered them again: the new offer
        // replaced the old one's entries and left its closed clock with nothing to point at.
        val again = swap(DayOverrideStatus.PENDING, groupId = "g-again")
        val oldOffer = closed(RequestKind.SWAP, "g-old").copy(firstDate = LocalDate.of(2026, 10, 20), dayCount = 2)

        val entries = history(
            overrides = mapOf("2026-10-20" to again, "2026-10-21" to again),
            clocks = seenLater(oldOffer)
        )

        assertEquals(
            listOf(
                HistoryEntry(
                    key = "swap_g-old",
                    subject = RequestSubject.DaySwap(LocalDate.of(2026, 10, 20), 2),
                    outcome = RequestOutcome.NO_ANSWER,
                    askedBy = "u1",
                    decidedBy = "u2",
                    decidedAt = CLOSED_AT,
                    clock = oldOffer
                )
            ),
            entries
        )
    }

    @Test
    fun `an orphaned clock without an outcome shows nothing`() {
        // Long past their ends but never closed, so superseded: an offer made again, and a proposal
        // its author corrected — or one answered before a later decision took the answer's place.
        val again = swap(DayOverrideStatus.PENDING, groupId = "g-again")
        val replacedOffer = clock(RequestKind.SWAP, "g-old").copy(firstDate = LocalDate.of(2026, 10, 20), dayCount = 1)
        val supersededProposal = clock(RequestKind.PROPOSAL, PROPOSED_AT, from = "u2")
        val later = millisOf(LATER)
        assertEquals(ClockState.NO_ANSWER, RequestClock.stateOf(replacedOffer, later))
        assertEquals(ClockState.NO_ANSWER, RequestClock.stateOf(supersededProposal, later))

        val entries = history(
            overrides = mapOf("2026-10-20" to again),
            clocks = seenLater(replacedOffer, supersededProposal)
        )

        assertEquals(emptyList(), entries)
    }

    @Test
    fun `a "No answer" whose change request is gone is left out`() {
        val entries = history(
            clocks = seenLater(closed(RequestKind.CHANGE, "gone"), closed(RequestKind.EVENT, "gone-too"))
        )

        assertEquals(emptyList(), entries)
    }

    @Test
    fun `a day offered again keeps its earlier answer in History beside the new offer`() {
        val accepted = swap(DayOverrideStatus.ACCEPTED, requestedBy = "u2", decidedBy = "u1")
        val answer = DayOverrideDecisionCodec.answerOf(accepted)
        val change = swap(
            DayOverrideStatus.DECLINED,
            requestedBy = "u1",
            decidedBy = "u2",
            decidedAt = "2026-09-05T08:00:00",
            groupId = "g2"
        ).copy(toParent = "mom", requestedAt = "2026-09-04T18:00:00", earlier = listOf(answer))

        val entries = history(overrides = mapOf("2026-09-07" to change, "2026-09-08" to change))

        assertEquals(
            listOf(
                HistoryEntry(
                    key = "swap_g2",
                    subject = RequestSubject.DaySwap(LocalDate.of(2026, 9, 7), 2),
                    outcome = RequestOutcome.DECLINED,
                    askedBy = "u1",
                    decidedBy = "u2",
                    decidedAt = LocalDateTime.of(2026, 9, 5, 8, 0)
                ),
                HistoryEntry(
                    key = "swap_earlier_g1@2026-09-02T20:00:00",
                    subject = RequestSubject.DaySwap(LocalDate.of(2026, 9, 7), 2),
                    outcome = RequestOutcome.ACCEPTED,
                    askedBy = "u2",
                    decidedBy = "u1",
                    decidedAt = LocalDateTime.of(2026, 9, 2, 20, 0)
                )
            ),
            entries
        )
    }

    // ---- The expense split (October 2026) ---------------------------------------------------------

    /** The co-parent's answer to the split proposal made at [SPLIT_AT], given at [at]. */
    private fun splitDecision(outcome: SplitRatioOutcome, by: String = "u2", at: LocalDateTime = DECIDED_AT) =
        SplitRatioDecision(outcome = outcome, by = by, atMillis = millisOf(at), proposalAtMillis = SPLIT_AT)

    @Test
    fun `the split's last decision is one entry, keyed by its proposal and asked by the other parent`() {
        val entries = RequestHistory.build(
            scope,
            changeRequests = emptyList(),
            events = emptyList(),
            overrides = emptyMap(),
            decision = null,
            splitDecision = splitDecision(SplitRatioOutcome.DECLINED)
        )

        assertEquals(
            listOf(
                HistoryEntry(
                    key = "split_$SPLIT_AT",
                    subject = RequestSubject.SplitRatio,
                    outcome = RequestOutcome.DECLINED,
                    askedBy = "u1",
                    decidedBy = "u2",
                    decidedAt = DECIDED_AT
                )
            ),
            entries
        )
    }

    @Test
    fun `a split decision with no readable time, or by somebody outside the family, is no entry`() {
        listOf(
            splitDecision(SplitRatioOutcome.ACCEPTED).copy(atMillis = 0L),
            splitDecision(SplitRatioOutcome.ACCEPTED, by = "u3"),
            splitDecision(SplitRatioOutcome.ACCEPTED, by = "")
        ).forEach { decision ->
            val entries = RequestHistory.build(scope, emptyList(), emptyList(), emptyMap(), null, ClocksAt(), decision)
            assertEquals(emptyList(), entries, "$decision")
        }
    }

    @Test
    fun `a split withdrawn at its end stays in History from its clock, and its answer wins over the clock`() {
        val withdrawn = closed(RequestKind.SPLIT, SPLIT_AT.toString(), from = "u2")

        val unanswered = RequestHistory.build(
            scope,
            changeRequests = emptyList(),
            events = emptyList(),
            overrides = emptyMap(),
            decision = null,
            clocks = seenLater(withdrawn)
        )
        assertEquals(
            listOf(
                HistoryEntry(
                    key = "split_$SPLIT_AT",
                    subject = RequestSubject.SplitRatio,
                    outcome = RequestOutcome.NO_ANSWER,
                    askedBy = "u2",
                    decidedBy = "u1",
                    decidedAt = CLOSED_AT,
                    clock = withdrawn
                )
            ),
            unanswered
        )

        // The proposal was answered after all — by u1, the clock's responder — and that answer wins.
        val answered = RequestHistory.build(
            scope,
            changeRequests = emptyList(),
            events = emptyList(),
            overrides = emptyMap(),
            decision = null,
            clocks = seenLater(withdrawn),
            splitDecision = splitDecision(SplitRatioOutcome.ACCEPTED, by = "u1")
        )
        assertEquals(listOf(RequestOutcome.ACCEPTED), answered.map { it.outcome })
    }

    private companion object {
        /** When the proposal of the custody cases was made: its clock's reference. */
        const val PROPOSED_AT = "2026-10-03T09:00:00"

        /** When the split proposal of the split cases was made, in epoch millis: its clock's reference. */
        const val SPLIT_AT = 1_791_100_000_000L

        /** When the co-parent answered the split, on this device's clock. */
        val DECIDED_AT: LocalDateTime = LocalDateTime.of(2026, 10, 6, 18, 30)

        /** The deadline of every clock here. */
        val DEADLINE: LocalDateTime = LocalDateTime.of(2026, 10, 4, 20, 0)

        /** When the proposer's phone closed a clock: an hour after its end, the last chance unused. */
        val CLOSED_AT: LocalDateTime = LocalDateTime.of(2026, 10, 5, 21, 0)

        /** Well past every clock's end. */
        val LATER: LocalDateTime = LocalDateTime.of(2026, 10, 10, 9, 0)
    }
}
