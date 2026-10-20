package com.coparently.app.domain.changerequests

import com.coparently.app.domain.custody.CustodyProposal
import com.coparently.app.domain.custody.DayOverride
import com.coparently.app.domain.custody.DaySwapGroup
import com.coparently.app.domain.custody.DaySwapInbox
import com.coparently.app.domain.custody.SharedCustody
import com.coparently.app.domain.expenses.FamilySettings
import com.coparently.app.domain.expenses.SplitRatioProposal
import com.coparently.app.domain.model.ChangeRequest
import com.coparently.app.domain.model.ChangeRequestStatus
import com.coparently.app.domain.model.Event
import java.time.LocalDate

/**
 * What waits on one parent: a custody proposal, an expense split proposal, events created for their
 * days, day-swap offers and change requests — each still unanswered, in the order the inbox lists
 * them.
 *
 * @property split The family's pending expense split proposal (October 2026), when it waits here.
 */
data class Waiting(
    val proposal: CustodyProposal? = null,
    val split: SplitRatioProposal? = null,
    val events: List<Event> = emptyList(),
    val swaps: List<DaySwapGroup> = emptyList(),
    val changeRequests: List<ChangeRequest> = emptyList()
) {
    /** Everything waiting, both proposals included: the number Home's Requests row shows. */
    val count: Int get() = countWithoutProposal + listOfNotNull(proposal, split).size

    /**
     * Everything but the two proposals: the custody one draws its own calendar banner, and a split
     * is about money, not the calendar. The number the calendar's banner shows.
     */
    val countWithoutProposal: Int get() = events.size + swaps.size + changeRequests.size

    /** Whether nothing waits at all. */
    val isEmpty: Boolean get() = count == 0
}

/**
 * The inbox of the family on screen, in its three sections (phase 2, spec §6.2): what waits on this
 * parent, what waits on the co-parent, and the history.
 *
 * One model for the inbox, Home's Requests row and the calendar's banner, built from one source,
 * so none of them can count what the others do not.
 *
 * **Each request is placed by its clock** (phase 3a, spec §4.1) when it has one that governs it:
 * open, it waits where it always did; past its deadline while the proposer can still give a last
 * chance, it waits on the proposer on both phones — the next move is theirs; under a last chance it
 * waits on the answering parent again; and once over it is "No answer" in the history on both
 * phones. A request without a clock — sent before deadlines existed, or by an older build — is
 * placed as it always was.
 *
 * @property timed The clock of every request the inbox shows with one, by clock id
 *   ([RequestDeadlineId]), seen at the moment the inbox was built: each waiting request whose clock
 *   governs it, and each "No answer" row. What a card reads its deadline and last chance from.
 * @property unansweredSwapDates The pending days of every day-swap offer whose clock says nobody
 *   answered in time — past its deadline, or over. The calendar stops marking them as negotiated
 *   (spec §4.4): nothing was accepted, and nothing waits on an answer any more.
 */
data class RequestInbox(
    val waitingOnMe: Waiting = Waiting(),
    val waitingOnThem: Waiting = Waiting(),
    val history: List<HistoryEntry> = emptyList(),
    val timed: Map<String, TimedRequest> = emptyMap(),
    val unansweredSwapDates: Set<LocalDate> = emptySet()
) {
    /** Whether all three sections are empty — the inbox then says "No requests yet". */
    val isEmpty: Boolean get() = waitingOnMe.isEmpty && waitingOnThem.isEmpty && history.isEmpty()

    /**
     * The clock of the request [clockId] names, as this inbox saw it; null when the inbox shows no
     * such request with a clock, and when there is no id — what a request that cannot name its
     * clock has ([RequestDeadlineId.forSwap], [RequestDeadlineId.forEvent]).
     */
    fun timedFor(clockId: String?): TimedRequest? = clockId?.let { timed[it] }

    companion object {
        /**
         * The inbox of [scope]'s family.
         *
         * A day swap in the waiting sections is one of today or later, as the inbox has always
         * shown them; a decided one, whatever its dates, is in the history instead.
         *
         * @param changeRequests Change requests; only [scope]'s count.
         * @param events Events, every acceptance; only [scope]'s count.
         * @param overrides The family's day swaps, keyed by ISO date.
         * @param shared The family's custody document, for its proposal and its last decision.
         * @param today The day the waiting sections start from.
         * @param clocks The family's request clocks, seen at one moment; none by default, which
         *   places every request as one without a clock.
         * @param settings The family's money agreement (`family_settings`), for its expense split
         *   proposal and its last decision; none by default. The split is placed exactly as the
         *   custody proposal is: by its clock when one governs it, else on whoever did not make it.
         */
        @Suppress("LongParameterList") // the five sources, the scope and day they are read for, and their clocks
        fun build(
            scope: RequestScope,
            changeRequests: List<ChangeRequest>,
            events: List<Event>,
            overrides: Map<String, DayOverride>,
            shared: SharedCustody?,
            today: LocalDate,
            clocks: ClocksAt = ClocksAt(),
            settings: FamilySettings? = null
        ): RequestInbox {
            val me = scope.myUid
            val pending = Pendings(
                requests = changeRequests
                    .filter { it.status == ChangeRequestStatus.PENDING && scope.covers(it) }
                    .sortedBy { it.createdAt }
                    .map { request ->
                        val clock = clocks.governing(scope, RequestDeadlineId.forChange(request), request.requestedBy)
                        Pending(request, request.requestedTo == me, clock)
                    },
                events = events
                    .filter { it.acceptance.isPending && scope.covers(it) }
                    .sortedBy { it.startDateTime }
                    .map { event ->
                        val clockId = RequestDeadlineId.forEvent(event)
                        val clock = clocks.governing(scope, clockId, event.createdByFirebaseUid)
                        Pending(event, AwaitingAnswers.eventAwaitsAnswerFrom(event, me), clock)
                    },
                swaps = DaySwapInbox.groups(overrides, today)
                    .filter { group -> group.swaps.any { it.override.isPending } }
                    .map { group ->
                        val author = group.swaps.first().override.requestedBy
                        val clock = clocks.governing(scope, RequestDeadlineId.forSwap(group), author)
                        Pending(group, group.awaitsAnswerFrom(me), clock)
                    },
                proposal = shared?.proposal?.let {
                    val clockId = RequestDeadlineId.forProposal(it, scope.familyId)
                    Pending(it, it.proposedBy != me, clocks.governing(scope, clockId, it.proposedBy))
                },
                split = settings?.proposal?.let {
                    val clockId = RequestDeadlineId.forSplit(it, scope.familyId)
                    Pending(it, it.proposedBy != me, clocks.governing(scope, clockId, it.proposedBy))
                }
            )
            val history = pending.withUnansweredProposals(
                RequestHistory.build(
                    scope,
                    changeRequests,
                    events,
                    overrides,
                    shared?.lastDecision,
                    clocks,
                    settings?.lastDecision
                )
            )
            return RequestInbox(
                waitingOnMe = pending.at(RequestPlace.ON_ME),
                waitingOnThem = pending.at(RequestPlace.ON_THEM),
                history = history,
                timed = pending.all
                    .mapNotNull { it.timed }
                    .plus(history.mapNotNull { entry -> entry.clock?.let { TimedRequest(it, clocks.nowMillis) } })
                    .associateBy { it.clock.id },
                unansweredSwapDates = unansweredSwapDates(scope, overrides, clocks)
            )
        }

        /**
         * The pending days of every offer, whatever its dates, whose clock no longer waits on the
         * answering parent: past its deadline, or over.
         */
        private fun unansweredSwapDates(scope: RequestScope, overrides: Map<String, DayOverride>, clocks: ClocksAt) =
            DaySwapInbox.allGroups(overrides)
                .filter { group ->
                    val author = group.swaps.first().override.requestedBy
                    clocks.governing(scope, RequestDeadlineId.forSwap(group), author)?.waitsOnResponder == false
                }
                .flatMap { group -> group.swaps.filter { it.override.isPending }.map { it.date } }
                .toSet()
    }
}

/** Where a pending request is listed. */
private enum class RequestPlace {
    /** Waiting on this parent's answer. */
    ON_ME,

    /** Waiting on the co-parent: their answer, or — past the deadline — the proposer's last chance. */
    ON_THEM,

    /** Over with no answer: the history, as "No answer". */
    HISTORY
}

/**
 * A pending request of the inbox, with the clock that governs it.
 *
 * @property asksMe Whether it waits on this parent's answer when it has no clock — where the inbox
 *   has always listed it.
 * @property timed Its clock, when one governs it ([governing]).
 */
private data class Pending<out T>(val request: T, val asksMe: Boolean, val timed: TimedRequest?) {

    /**
     * Where it is listed (spec §4.1): over, it is history on both phones; this parent's own request
     * waits on the co-parent while it lasts; and one waiting on this parent's answer stays theirs
     * while it is open or under a last chance, and waits on the proposer while the last chance is
     * theirs to give. Without a clock it is listed where it always was.
     */
    val place: RequestPlace
        get() = when {
            timed?.state == ClockState.NO_ANSWER -> RequestPlace.HISTORY
            !asksMe -> RequestPlace.ON_THEM
            timed == null || timed.waitsOnResponder -> RequestPlace.ON_ME
            else -> RequestPlace.ON_THEM
        }
}

/**
 * Every pending request of one inbox, each with the clock that governs it, in the order each list
 * was given in.
 *
 * @property proposal The custody document's pending proposal, if any.
 * @property split The money agreement's pending expense split proposal, if any.
 */
private data class Pendings(
    val requests: List<Pending<ChangeRequest>>,
    val events: List<Pending<Event>>,
    val swaps: List<Pending<DaySwapGroup>>,
    val proposal: Pending<CustodyProposal>?,
    val split: Pending<SplitRatioProposal>?
) {
    /** Every one of them, whatever its kind. */
    val all: List<Pending<*>> get() = requests + events + swaps + listOfNotNull(proposal, split)

    /** What waits at [place]. */
    fun at(place: RequestPlace) = Waiting(
        proposal = proposal?.takeIf { it.place == place }?.request,
        split = split?.takeIf { it.place == place }?.request,
        events = events.placedAt(place),
        swaps = swaps.placedAt(place),
        changeRequests = requests.placedAt(place)
    )

    /**
     * [history] with the "No answer" row of each pending proposal — custody or split — whose clock
     * says it is over but nobody has closed it yet ([UnansweredRows.withPendingProposal]).
     */
    fun withUnansweredProposals(history: List<HistoryEntry>): List<HistoryEntry> {
        val withCustody = proposal?.takeIf { it.place == RequestPlace.HISTORY }
            ?.let { UnansweredRows.withPendingProposal(history, it.request, it.timed) }
            ?: history
        return split?.takeIf { it.place == RequestPlace.HISTORY }
            ?.let { UnansweredRows.withPendingSplit(withCustody, it.request, it.timed) }
            ?: withCustody
    }

    private fun <T> List<Pending<T>>.placedAt(place: RequestPlace): List<T> =
        filter { it.place == place }.map { it.request }
}
