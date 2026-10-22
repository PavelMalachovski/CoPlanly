package com.coparently.app.domain.changerequests

import com.coparently.app.domain.custody.CustodyDecision
import com.coparently.app.domain.custody.CustodyDecisionOutcome
import com.coparently.app.domain.custody.CustodyProposal
import com.coparently.app.domain.custody.DayOverride
import com.coparently.app.domain.custody.DayOverrideStatus
import com.coparently.app.domain.custody.DaySwap
import com.coparently.app.domain.custody.DaySwapGroup
import com.coparently.app.domain.custody.DaySwapInbox
import com.coparently.app.domain.events.EventAcceptance
import com.coparently.app.domain.expenses.SplitRatioDecision
import com.coparently.app.domain.expenses.SplitRatioOutcome
import com.coparently.app.domain.expenses.SplitRatioProposal
import com.coparently.app.domain.model.ChangeRequest
import com.coparently.app.domain.model.ChangeRequestStatus
import com.coparently.app.domain.model.Event
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

/** How a request ended. */
enum class RequestOutcome {
    /** The other parent took it up. */
    ACCEPTED,

    /** The other parent turned it down. */
    DECLINED,

    /** The parent who asked took it back before it was answered. */
    WITHDRAWN,

    /**
     * Nobody answered by the end of its clock (phase 3a, spec §4.4): the deadline passed, and the
     * last chance with it, or unused. Its own outcome and never shown as [DECLINED]: the other
     * parent said nothing, which is not saying no, and nothing changed in the calendar.
     */
    NO_ANSWER;

    /**
     * Whether the other parent answered: took it up or turned it down. A withdrawal is the asker's
     * own act, and [NO_ANSWER] is silence.
     */
    val isAnswer: Boolean get() = this == ACCEPTED || this == DECLINED
}

/** What a request was about — the line a history row leads with. */
sealed interface RequestSubject {

    /**
     * A new time for an event.
     *
     * @property proposedStart The time asked for.
     */
    data class NewTime(val eventId: String, val eventTitle: String, val proposedStart: LocalDateTime) : RequestSubject

    /**
     * One day swap, however many days the offer covered.
     *
     * @property firstDate The offer's first day.
     * @property dayCount How many days it covered.
     */
    data class DaySwap(val firstDate: LocalDate, val dayCount: Int) : RequestSubject

    /**
     * An event created for the other parent's days.
     *
     * @property start When the event starts.
     */
    data class NewEvent(val eventId: String, val eventTitle: String, val start: LocalDateTime) : RequestSubject

    /** A new custody schedule. */
    data object CustodySchedule : RequestSubject

    /**
     * A new expense split (October 2026). Like [CustodySchedule] it names nothing of its own: the
     * family has one agreement, and its proposal is the only one that can wait.
     */
    data object SplitRatio : RequestSubject
}

/**
 * One answered, withdrawn or unanswered request in the inbox's history.
 *
 * @property key Unique across every kind of request: `cr_`, `swap_`, `ev_`, `custody_` or `split_`
 *   and the record's own id. A [RequestOutcome.NO_ANSWER] row takes the key its record's own row
 *   would.
 * @property askedBy The parent who asked.
 * @property decidedBy The parent who answered — or, for a withdrawal, the one who withdrew; for
 *   [RequestOutcome.NO_ANSWER], the one whose answer was due.
 * @property decidedAt When, in the naive local time every one of these records stores. For
 *   [RequestOutcome.NO_ANSWER], when the proposer's phone closed the clock, or the clock's end while
 *   it has not, on this device's clock.
 * @property queued This phone's own answer or withdrawal that has not reached the server yet, so
 *   the row must not claim an outcome the other parent has not received (CQ-20).
 * @property clock The clock of a [RequestOutcome.NO_ANSWER] row — its deadline, last chance, opening
 *   and reminders, for the row's own line; null for every other outcome.
 * @property swapDays The days of an accepted swap that is still the live answer, each with the slot
 *   it was given to — what "Propose a change" offers back; empty for every other row, an earlier
 *   answer's row included.
 */
data class HistoryEntry(
    val key: String,
    val subject: RequestSubject,
    val outcome: RequestOutcome,
    val askedBy: String,
    val decidedBy: String,
    val decidedAt: LocalDateTime,
    val queued: Boolean = false,
    val clock: RequestDeadline? = null,
    val swapDays: Map<LocalDate, String> = emptyMap()
)

/**
 * The inbox's history: every request of the family on screen that has been answered, withdrawn or
 * left unanswered, newest first (phase 2, spec §6.2; phase 3a, spec §4.4).
 *
 * Derived from the records both phones already hold — change requests, day swaps, events created
 * for the other parent, the custody document's last decision and the money agreement's — and from
 * the family's request clocks, never from a stored history list, which would be a second account of
 * what happened and could disagree with the first. Only the latest custody decision and the latest
 * split decision are kept by their documents; older ones stay in the chat's cards.
 */
object RequestHistory {

    /**
     * The history for [scope], newest first.
     *
     * Every source is kept to what [scope] covers, so another family's requests and an
     * ex-partner's never appear. Swaps are not bounded by the date, unlike the inbox's waiting
     * sections: a swap of a day already lived is still a record of how it was answered.
     *
     * **"No answer"** (phase 3a, spec §4.4): a request still pending in its record whose clock says
     * it is over is a [RequestOutcome.NO_ANSWER] row, and so is a closed clock whose request is
     * gone ([UnansweredRows.ofClosedClocks]). An answer wins over its clock, late or not: it is an
     * explicit yes or no. A clock that does not govern its record is ignored ([governing]). The
     * pending custody and split proposals are not among these sources, so the inbox adds their rows
     * while their clocks are over but not yet closed ([UnansweredRows.withPendingProposal],
     * [UnansweredRows.withPendingSplit]).
     *
     * @param clocks The family's request clocks, seen at one moment; none by default.
     * @param splitDecision The money agreement's last decision (`family_settings`), or none.
     */
    @Suppress("LongParameterList") // the five sources, the scope, and the clocks that close them
    fun build(
        scope: RequestScope,
        changeRequests: List<ChangeRequest>,
        events: List<Event>,
        overrides: Map<String, DayOverride>,
        decision: CustodyDecision?,
        clocks: ClocksAt = ClocksAt(),
        splitDecision: SplitRatioDecision? = null
    ): List<HistoryEntry> {
        val offers = DaySwapInbox.allGroups(overrides)
        return (
            changeRequests.mapNotNull { fromRequest(scope, it, clocks) } +
                offers.flatMap { fromSwap(scope, it, clocks) } +
                events.mapNotNull { fromEvent(scope, it, clocks) } +
                listOfNotNull(decision?.let { fromDecision(scope, it) }) +
                listOfNotNull(splitDecision?.let { splitDecisionRow(scope, it) }) +
                earlierSwapAnswers(scope, overrides) +
                UnansweredRows.ofClosedClocks(scope, offers, decision, clocks, splitDecision)
            ).sortedWith(NEWEST_FIRST)
    }

    private fun fromRequest(scope: RequestScope, request: ChangeRequest, clocks: ClocksAt): HistoryEntry? {
        if (!scope.covers(request)) return null
        val key = "cr_${request.id}"
        val subject = RequestSubject.NewTime(request.eventId, request.eventTitle, request.proposedStartDateTime)
        val outcome = request.status.outcome() ?: return UnansweredRows.ifOver(
            key,
            subject,
            clocks.governing(scope, RequestDeadlineId.forChange(request), request.requestedBy)
        )
        return HistoryEntry(
            key = key,
            subject = subject,
            outcome = outcome,
            askedBy = request.requestedBy,
            decidedBy = if (outcome == RequestOutcome.WITHDRAWN) request.requestedBy else request.requestedTo,
            decidedAt = request.respondedAt ?: request.createdAt,
            queued = !request.syncedToFirestore
        )
    }

    /**
     * The rows of one offer: "no answer" while any of its days waits past its clock, and once every
     * day is answered, **one row per answer the days were given** — an offer answered day by day
     * (21 October 2026, Zuzana's test), accepted for two days and declined for three, reads as both.
     * An offer answered one way keeps the single row, and the key, it always had.
     */
    private fun fromSwap(scope: RequestScope, group: DaySwapGroup, clocks: ClocksAt): List<HistoryEntry> {
        val first = group.swaps.first().override
        if (!scope.isPair(first.requestedBy)) return emptyList()
        val key = "swap_${group.key}"
        if (group.swaps.any { it.override.isPending }) {
            return listOfNotNull(
                UnansweredRows.ifOver(
                    key,
                    RequestSubject.DaySwap(group.firstDate, group.dayCount),
                    clocks.governing(scope, RequestDeadlineId.forSwap(group), first.requestedBy)
                )
            )
        }
        val byAnswer = group.swaps.groupBy { it.override.status }
        return byAnswer.mapNotNull { (status, days) ->
            val answer = days.first().override
            answeredRow(
                scope = scope,
                key = if (byAnswer.size == 1) key else "${key}_${status.name.lowercase()}",
                days = days,
                outcome = status.outcome(),
                // An older build may have left decidedAt blank or unreadable; the offer's own time
                // still places the answer in the history rather than dropping it.
                decidedAt = parse(answer.decidedAt) ?: parse(answer.requestedAt)
            )
        }
    }

    /**
     * The answers swapped days had before they were offered again ([DayOverride.earlier]): one row
     * per earlier offer, kept beside the row of the offer that replaced it, so an agreement a
     * parent later asked to change stays in the History it was given in (tester feedback,
     * October 2026).
     */
    private fun earlierSwapAnswers(scope: RequestScope, overrides: Map<String, DayOverride>): List<HistoryEntry> =
        overrides.entries
            .flatMap { (iso, override) ->
                val date = runCatching { LocalDate.parse(iso) }.getOrNull() ?: return@flatMap emptyList()
                override.earlierDecisions.map { date to it }
            }
            .filter { (_, decision) -> scope.isPair(decision.requestedBy) }
            // One row per earlier offer: its group, or its single day, at the moment it was answered.
            .groupBy { (date, decision) -> "${decision.groupId ?: "single:$date"}@${decision.decidedAt}" }
            .mapNotNull { (key, days) ->
                val (firstDate, decision) = days.minBy { it.first }
                val outcome = decision.status.outcome() ?: return@mapNotNull null
                val decidedAt = parse(decision.decidedAt) ?: parse(decision.requestedAt) ?: return@mapNotNull null
                HistoryEntry(
                    key = "swap_earlier_$key",
                    subject = RequestSubject.DaySwap(firstDate, days.size),
                    outcome = outcome,
                    askedBy = decision.requestedBy,
                    decidedBy = decision.decidedBy ?: scope.otherOf(decision.requestedBy),
                    decidedAt = decidedAt
                )
            }

    private fun fromEvent(scope: RequestScope, event: Event, clocks: ClocksAt): HistoryEntry? {
        // Covered means one of the two parents created it, so the creator is known from here on.
        val creator = event.createdByFirebaseUid?.takeIf { scope.covers(event) } ?: return null
        val key = "ev_${event.id}"
        val subject = RequestSubject.NewEvent(event.id, event.title, event.startDateTime)
        if (event.acceptance.isPending) {
            return UnansweredRows.ifOver(
                key,
                subject,
                clocks.governing(scope, RequestDeadlineId.forEvent(event), creator)
            )
        }
        val outcome = event.acceptance.outcome()
        val decidedAt = event.acceptedAt
        return if (outcome == null || decidedAt == null) {
            null
        } else {
            HistoryEntry(
                key = key,
                subject = subject,
                outcome = outcome,
                askedBy = creator,
                decidedBy = event.acceptedBy ?: scope.otherOf(creator),
                decidedAt = decidedAt,
                queued = !event.syncedToFirestore && event.acceptedBy == scope.myUid
            )
        }
    }

    private fun fromDecision(scope: RequestScope, decision: CustodyDecision): HistoryEntry? {
        val decidedAt = parse(decision.at)
        return if (decidedAt == null || !scope.isPair(decision.by)) {
            null
        } else {
            HistoryEntry(
                key = "custody_${decision.proposalAt}",
                subject = RequestSubject.CustodySchedule,
                outcome = when (decision.outcome) {
                    CustodyDecisionOutcome.ACCEPTED -> RequestOutcome.ACCEPTED
                    CustodyDecisionOutcome.DECLINED -> RequestOutcome.DECLINED
                },
                // The proposer is never the one who decides (CustodyProposalTransition), so in a
                // pair the one who asked is the other parent.
                askedBy = scope.otherOf(decision.by),
                decidedBy = decision.by,
                decidedAt = decidedAt
            )
        }
    }

    private fun ChangeRequestStatus.outcome(): RequestOutcome? = when (this) {
        ChangeRequestStatus.PENDING -> null
        ChangeRequestStatus.ACCEPTED -> RequestOutcome.ACCEPTED
        ChangeRequestStatus.DECLINED -> RequestOutcome.DECLINED
        ChangeRequestStatus.CANCELLED -> RequestOutcome.WITHDRAWN
    }

    private fun DayOverrideStatus.outcome(): RequestOutcome? = when (this) {
        DayOverrideStatus.PENDING -> null
        DayOverrideStatus.ACCEPTED -> RequestOutcome.ACCEPTED
        DayOverrideStatus.DECLINED -> RequestOutcome.DECLINED
        DayOverrideStatus.CANCELLED -> RequestOutcome.WITHDRAWN
    }

    private fun EventAcceptance.outcome(): RequestOutcome? = when (this) {
        EventAcceptance.NOT_REQUIRED, EventAcceptance.PENDING -> null
        EventAcceptance.ACCEPTED -> RequestOutcome.ACCEPTED
        EventAcceptance.DECLINED -> RequestOutcome.DECLINED
    }

    private fun parse(iso: String?): LocalDateTime? =
        iso?.let { runCatching { LocalDateTime.parse(it) }.getOrNull() }
}

/**
 * The rows of requests nobody answered in time (phase 3a, spec §4.4, §6.6), each from its clock:
 * asked by the clock's proposer, left unanswered by its responder, and dated by the clock's closure
 * — or by its end while nobody has closed it — on this device's clock.
 */
internal object UnansweredRows {

    /**
     * The "No answer" row keyed [key] about [subject] when the clock [timed] says its request is
     * over; null while it is not, and for a request without a clock that governs it.
     */
    fun ifOver(key: String, subject: RequestSubject, timed: TimedRequest?): HistoryEntry? =
        timed?.takeIf { it.state == ClockState.NO_ANSWER }?.let { row(key, subject, it.clock) }

    /**
     * The rows of closed clocks whose request is gone (spec §5.1): a custody or expense split
     * proposal withdrawn at its end, and a day-swap offer replaced by a newer offer for its days,
     * which only the clock still describes, by its first day and day count. Only a closed clock
     * speaks for a request that is no longer there: one never closed was superseded — answered,
     * corrected or offered again — and shows nothing. A proposal's own answer, while its document
     * still holds it, wins over its clock, and a change request or event that is gone shows nothing
     * at all (ruling 13): its chat card still records it.
     *
     * @param offers Every day-swap offer the family's custody document holds.
     * @param decision The custody document's last decision.
     * @param splitDecision The money agreement's last decision.
     */
    fun ofClosedClocks(
        scope: RequestScope,
        offers: List<DaySwapGroup>,
        decision: CustodyDecision?,
        clocks: ClocksAt,
        splitDecision: SplitRatioDecision? = null
    ): List<HistoryEntry> {
        val offerIds = offers.mapNotNullTo(HashSet()) { it.swaps.first().override.groupId }
        return clocks.byId.values
            .filter { it.outcome != null && clocks.governing(scope, it.id, it.proposerUid) != null }
            .mapNotNull { clock ->
                when (clock.kind) {
                    RequestKind.PROPOSAL -> row("custody_${clock.ref}", RequestSubject.CustodySchedule, clock)
                        .takeIf { decision?.proposalAt != clock.ref }
                    RequestKind.SPLIT -> row("split_${clock.ref}", RequestSubject.SplitRatio, clock)
                        .takeIf { splitDecision?.proposalAtMillis?.toString() != clock.ref }
                    RequestKind.SWAP -> replacedOffer(clock).takeIf { clock.ref !in offerIds }
                    RequestKind.CHANGE, RequestKind.EVENT -> null
                }
            }
    }

    /**
     * [history] with the "No answer" row of the family's pending custody [proposal] when its clock
     * [timed] says it is over but nobody has closed it yet. [RequestHistory.build] cannot see that
     * row: it is not given the custody document, and a clock never closed whose proposal it cannot
     * see may as well have been answered or corrected. Once the clock is closed, the row is already
     * there, from the clock alone, and is not repeated.
     */
    fun withPendingProposal(
        history: List<HistoryEntry>,
        proposal: CustodyProposal,
        timed: TimedRequest?
    ): List<HistoryEntry> =
        withPendingRow(history, ifOver("custody_${proposal.proposedAt}", RequestSubject.CustodySchedule, timed))

    /**
     * [history] with the "No answer" row of the family's pending expense split [proposal] when its
     * clock [timed] says it is over but nobody has closed it yet — [withPendingProposal]'s rule for
     * the money agreement's proposal.
     */
    fun withPendingSplit(
        history: List<HistoryEntry>,
        proposal: SplitRatioProposal,
        timed: TimedRequest?
    ): List<HistoryEntry> =
        withPendingRow(history, ifOver("split_${proposal.proposedAtMillis}", RequestSubject.SplitRatio, timed))

    /** [history] with [row] in its place, unless there is no row or the history already has its key. */
    private fun withPendingRow(history: List<HistoryEntry>, row: HistoryEntry?): List<HistoryEntry> =
        if (row == null || history.any { it.key == row.key }) {
            history
        } else {
            (history + row).sortedWith(NEWEST_FIRST)
        }

    /** The row of a replaced offer, from what its clock says it covered; null for a clock that does not say. */
    private fun replacedOffer(clock: RequestDeadline): HistoryEntry? {
        val firstDate = clock.firstDate ?: return null
        val dayCount = clock.dayCount ?: return null
        return row("swap_${clock.ref}", RequestSubject.DaySwap(firstDate, dayCount), clock)
    }

    private fun row(key: String, subject: RequestSubject, clock: RequestDeadline) = HistoryEntry(
        key = key,
        subject = subject,
        outcome = RequestOutcome.NO_ANSWER,
        askedBy = clock.proposerUid,
        decidedBy = clock.responderUid,
        decidedAt = Instant.ofEpochMilli(clock.closedAtMillis ?: RequestClock.endMillis(clock))
            .atZone(ZoneId.systemDefault())
            .toLocalDateTime(),
        clock = clock
    )
}

/**
 * The clock [clockId] names, seen at this moment, when it governs a request [authorUid] made in
 * [scope]'s family: it is that family's clock, and it names the author as its proposer and the
 * other parent as its responder ([RequestClock.governs]). Null otherwise — no id, no such clock, or
 * one this phone must ignore — and the request is then placed as if it had none.
 */
internal fun ClocksAt.governing(scope: RequestScope, clockId: String?, authorUid: String?): TimedRequest? {
    val timed = timed(clockId) ?: return null
    val author = authorUid?.takeIf { scope.isPair(it) } ?: return null
    val governs = timed.clock.familyId == scope.familyId &&
        RequestClock.governs(timed.clock, author, scope.otherOf(author))
    return timed.takeIf { governs }
}

/**
 * The money agreement's last decision as a History row (October 2026), keyed by the proposal it
 * answered and dated on this device's clock. A decision with no readable time, or by somebody
 * outside [scope]'s family, is no row: an older or damaged document must not put a 1970 answer at
 * the bottom of History.
 */
private fun splitDecisionRow(scope: RequestScope, decision: SplitRatioDecision): HistoryEntry? =
    if (decision.atMillis <= 0L || !scope.isPair(decision.by)) {
        null
    } else {
        HistoryEntry(
            key = "split_${decision.proposalAtMillis}",
            subject = RequestSubject.SplitRatio,
            outcome = when (decision.outcome) {
                SplitRatioOutcome.ACCEPTED -> RequestOutcome.ACCEPTED
                SplitRatioOutcome.DECLINED -> RequestOutcome.DECLINED
            },
            // As for custody: the proposer never decides their own (SplitRatioTransition), so in a
            // pair the one who asked is the other parent.
            askedBy = scope.otherOf(decision.by),
            decidedBy = decision.by,
            decidedAt = Instant.ofEpochMilli(decision.atMillis).atZone(ZoneId.systemDefault()).toLocalDateTime()
        )
    }

/** The history's order: newest first, and equal times by key, so the order never flickers. */
private val NEWEST_FIRST: Comparator<HistoryEntry> =
    compareByDescending<HistoryEntry> { it.decidedAt }.thenBy { it.key }

/** One answer given to [days] of an offer, or null when it is not an answer or has no time. */
private fun answeredRow(
    scope: RequestScope,
    key: String,
    days: List<DaySwap>,
    outcome: RequestOutcome?,
    decidedAt: LocalDateTime?
): HistoryEntry? {
    val first = days.first().override
    return if (outcome == null || decidedAt == null) {
        null
    } else {
        HistoryEntry(
            key = key,
            subject = RequestSubject.DaySwap(days.first().date, days.size),
            outcome = outcome,
            askedBy = first.requestedBy,
            decidedBy = first.decidedBy ?: scope.otherOf(first.requestedBy),
            decidedAt = decidedAt,
            swapDays = if (outcome == RequestOutcome.ACCEPTED) {
                days.associate { it.date to it.override.toParent }
            } else {
                emptyMap()
            }
        )
    }
}
