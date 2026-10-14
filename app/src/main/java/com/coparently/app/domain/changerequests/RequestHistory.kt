package com.coparently.app.domain.changerequests

import com.coparently.app.domain.custody.CustodyDecision
import com.coparently.app.domain.custody.CustodyDecisionOutcome
import com.coparently.app.domain.custody.DayOverride
import com.coparently.app.domain.custody.DayOverrideStatus
import com.coparently.app.domain.custody.DaySwapGroup
import com.coparently.app.domain.custody.DaySwapInbox
import com.coparently.app.domain.events.EventAcceptance
import com.coparently.app.domain.model.ChangeRequest
import com.coparently.app.domain.model.ChangeRequestStatus
import com.coparently.app.domain.model.Event
import java.time.LocalDate
import java.time.LocalDateTime

/** How a request ended. */
enum class RequestOutcome {
    /** The other parent took it up. */
    ACCEPTED,

    /** The other parent turned it down. */
    DECLINED,

    /** The parent who asked took it back before it was answered. */
    WITHDRAWN
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
}

/**
 * One answered or withdrawn request in the inbox's history.
 *
 * @property key Unique across every kind of request: `cr_`, `swap_`, `ev_` or `custody_` and the
 *   record's own id.
 * @property askedBy The parent who asked.
 * @property decidedBy The parent who answered — or, for a withdrawal, the one who withdrew.
 * @property decidedAt When, in the naive local time every one of these records stores.
 * @property queued This phone's own answer or withdrawal that has not reached the server yet, so
 *   the row must not claim an outcome the other parent has not received (CQ-20).
 */
data class HistoryEntry(
    val key: String,
    val subject: RequestSubject,
    val outcome: RequestOutcome,
    val askedBy: String,
    val decidedBy: String,
    val decidedAt: LocalDateTime,
    val queued: Boolean = false
)

/**
 * The inbox's history: every request of the family on screen that has been answered or withdrawn,
 * newest first (phase 2, spec §6.2).
 *
 * Derived from the records both phones already hold — change requests, day swaps, events created
 * for the other parent, and the custody document's last decision — never from a stored history
 * list, which would be a second account of what happened and could disagree with the first. Only
 * the latest custody decision is kept by the document; older ones stay in the chat's cards.
 */
object RequestHistory {

    /**
     * The history for [scope], newest first.
     *
     * Every source is kept to what [scope] covers, so another family's requests and an
     * ex-partner's never appear. Swaps are not bounded by the date, unlike the inbox's waiting
     * sections: a swap of a day already lived is still a record of how it was answered.
     */
    fun build(
        scope: RequestScope,
        changeRequests: List<ChangeRequest>,
        events: List<Event>,
        overrides: Map<String, DayOverride>,
        decision: CustodyDecision?
    ): List<HistoryEntry> =
        (
            changeRequests.mapNotNull { fromRequest(scope, it) } +
                DaySwapInbox.allGroups(overrides).mapNotNull { fromSwap(scope, it) } +
                events.mapNotNull { fromEvent(scope, it) } +
                listOfNotNull(decision?.let { fromDecision(scope, it) })
            ).sortedWith(compareByDescending<HistoryEntry> { it.decidedAt }.thenBy { it.key })

    private fun fromRequest(scope: RequestScope, request: ChangeRequest): HistoryEntry? {
        val outcome = request.status.outcome()
        return if (outcome == null || !scope.covers(request)) {
            null
        } else {
            HistoryEntry(
                key = "cr_${request.id}",
                subject = RequestSubject.NewTime(request.eventId, request.eventTitle, request.proposedStartDateTime),
                outcome = outcome,
                askedBy = request.requestedBy,
                decidedBy = if (outcome == RequestOutcome.WITHDRAWN) request.requestedBy else request.requestedTo,
                decidedAt = request.respondedAt ?: request.createdAt,
                queued = !request.syncedToFirestore
            )
        }
    }

    private fun fromSwap(scope: RequestScope, group: DaySwapGroup): HistoryEntry? {
        val first = group.swaps.first().override
        val outcome = first.status.outcome()
        val settled = group.swaps.none { it.override.isPending } && scope.isPair(first.requestedBy)
        // An older build may have left decidedAt blank or unreadable; the offer's own time still
        // places the answer in the history rather than dropping it.
        val decidedAt = parse(first.decidedAt) ?: parse(first.requestedAt)
        return if (outcome == null || decidedAt == null || !settled) {
            null
        } else {
            HistoryEntry(
                key = "swap_${group.key}",
                subject = RequestSubject.DaySwap(group.firstDate, group.dayCount),
                outcome = outcome,
                askedBy = first.requestedBy,
                decidedBy = first.decidedBy ?: scope.otherOf(first.requestedBy),
                decidedAt = decidedAt
            )
        }
    }

    private fun fromEvent(scope: RequestScope, event: Event): HistoryEntry? {
        val outcome = event.acceptance.outcome()
        val decidedAt = event.acceptedAt
        // Covered means one of the two parents created it, so the creator is known from here on.
        val creator = event.createdByFirebaseUid?.takeIf { scope.covers(event) }
        return if (outcome == null || decidedAt == null || creator == null) {
            null
        } else {
            HistoryEntry(
                key = "ev_${event.id}",
                subject = RequestSubject.NewEvent(event.id, event.title, event.startDateTime),
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
    }

    private fun EventAcceptance.outcome(): RequestOutcome? = when (this) {
        EventAcceptance.NOT_REQUIRED, EventAcceptance.PENDING -> null
        EventAcceptance.ACCEPTED -> RequestOutcome.ACCEPTED
        EventAcceptance.DECLINED -> RequestOutcome.DECLINED
    }

    private fun parse(iso: String?): LocalDateTime? =
        iso?.let { runCatching { LocalDateTime.parse(it) }.getOrNull() }
}
