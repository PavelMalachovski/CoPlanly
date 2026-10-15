package com.coparently.app.domain.changerequests

import com.coparently.app.domain.custody.CustodyProposal
import com.coparently.app.domain.custody.DayOverride
import com.coparently.app.domain.custody.DaySwapGroup
import com.coparently.app.domain.custody.DaySwapInbox
import com.coparently.app.domain.custody.SharedCustody
import com.coparently.app.domain.model.ChangeRequest
import com.coparently.app.domain.model.ChangeRequestStatus
import com.coparently.app.domain.model.Event
import java.time.LocalDate

/**
 * What waits on one parent: a custody proposal, events created for their days, day-swap offers and
 * change requests — each still unanswered, in the order the inbox lists them.
 */
data class Waiting(
    val proposal: CustodyProposal? = null,
    val events: List<Event> = emptyList(),
    val swaps: List<DaySwapGroup> = emptyList(),
    val changeRequests: List<ChangeRequest> = emptyList()
) {
    /** Everything waiting, the proposal included: the number Home's Requests row shows. */
    val count: Int get() = countWithoutProposal + if (proposal != null) 1 else 0

    /** Everything but the proposal, which draws a banner of its own over the calendar. */
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
 */
data class RequestInbox(
    val waitingOnMe: Waiting = Waiting(),
    val waitingOnThem: Waiting = Waiting(),
    val history: List<HistoryEntry> = emptyList()
) {
    /** Whether all three sections are empty — the inbox then says "No requests yet". */
    val isEmpty: Boolean get() = waitingOnMe.isEmpty && waitingOnThem.isEmpty && history.isEmpty()

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
         */
        @Suppress("LongParameterList") // the four sources and the scope and day they are read for
        fun build(
            scope: RequestScope,
            changeRequests: List<ChangeRequest>,
            events: List<Event>,
            overrides: Map<String, DayOverride>,
            shared: SharedCustody?,
            today: LocalDate
        ): RequestInbox {
            val me = scope.myUid
            val pendingRequests = changeRequests
                .filter { it.status == ChangeRequestStatus.PENDING && scope.covers(it) }
                .sortedBy { it.createdAt }
            val pendingEvents = events
                .filter { it.acceptance.isPending && scope.covers(it) }
                .sortedBy { it.startDateTime }
            val proposal = shared?.proposal
            return RequestInbox(
                waitingOnMe = Waiting(
                    proposal = proposal?.takeIf { it.proposedBy != me },
                    events = pendingEvents.filter { AwaitingAnswers.eventAwaitsAnswerFrom(it, me) },
                    swaps = AwaitingAnswers.swapOffers(overrides, me, today),
                    changeRequests = pendingRequests.filter { it.requestedTo == me }
                ),
                waitingOnThem = Waiting(
                    proposal = proposal?.takeIf { it.proposedBy == me },
                    events = pendingEvents.filter { it.createdByFirebaseUid == me },
                    swaps = DaySwapInbox.groups(overrides, today).filter { group ->
                        group.swaps.first().override.requestedBy == me && group.swaps.any { it.override.isPending }
                    },
                    changeRequests = pendingRequests.filter { it.requestedBy == me }
                ),
                history = RequestHistory.build(scope, changeRequests, events, overrides, shared?.lastDecision)
            )
        }
    }
}
