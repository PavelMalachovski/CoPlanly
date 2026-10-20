package com.coparently.app.presentation.changerequests

import com.coparently.app.domain.changerequests.HistoryEntry
import com.coparently.app.domain.changerequests.RequestDeadlineId
import com.coparently.app.domain.changerequests.RequestInbox
import com.coparently.app.domain.changerequests.RequestScope
import com.coparently.app.domain.changerequests.TimedRequest
import com.coparently.app.domain.changerequests.Waiting
import com.coparently.app.domain.custody.CustodyProposal
import com.coparently.app.domain.custody.DaySwapGroup
import com.coparently.app.domain.expenses.SplitRatioProposal
import com.coparently.app.domain.model.ChangeRequest
import com.coparently.app.domain.model.Event

/**
 * The inbox's three sections (phase 2, spec §6.2), as the `section` route argument names them.
 *
 * @property routeValue What the route carries — `change_requests?section=history`.
 */
enum class InboxSection(val routeValue: String) {
    /** What waits on this parent's answer. */
    WAITING_ON_ME("waiting"),

    /** What this parent sent and the co-parent has not answered. */
    WAITING_ON_THEM("sent"),

    /** Everything answered or withdrawn. */
    HISTORY("history");

    companion object {
        /** The section [value] names, or null for anything else — a missing argument included. */
        fun fromRoute(value: String?): InboxSection? = entries.firstOrNull { it.routeValue == value }
    }
}

/**
 * One item of the inbox's `LazyColumn`. Its [key] is the column's key and unique across kinds.
 */
sealed interface InboxItem {

    /** The `LazyColumn` key. */
    val key: String

    /** A section's heading. */
    data class Header(val section: InboxSection) : InboxItem {
        override val key: String get() = "h_${section.routeValue}"
    }

    /**
     * The custody-pattern proposal.
     *
     * @property mine Whether this parent made it — then nothing on the card answers it.
     * @property timed Its clock as the inbox saw it, or null when it has none (phase 3a).
     */
    data class Proposal(
        val proposal: CustodyProposal,
        val mine: Boolean,
        val timed: TimedRequest? = null
    ) : InboxItem {
        override val key: String get() = if (mine) "p_mine" else "p_theirs"
    }

    /**
     * A proposed expense split, waiting on the other parent's answer.
     *
     * @property mine Whether this parent proposed it — then the card offers Withdraw, and nothing
     *   that answers it.
     * @property timed Its clock as the inbox saw it, or null when it has none.
     */
    data class Split(
        val proposal: SplitRatioProposal,
        val mine: Boolean,
        val timed: TimedRequest? = null
    ) : InboxItem {
        override val key: String get() = if (mine) "s_mine" else "s_theirs"
    }

    /**
     * An event created for the other parent's days, waiting on their answer.
     *
     * @property mine Whether this parent created it — then the card shows what was sent, with no
     *   buttons.
     * @property timed Its clock as the inbox saw it, or null when it has none (phase 3a).
     */
    data class AwaitingEvent(val event: Event, val mine: Boolean, val timed: TimedRequest? = null) : InboxItem {
        override val key: String get() = "ev_${event.id}"
    }

    /**
     * A day-swap offer, one per offer however many days it covers.
     *
     * @property timed Its clock as the inbox saw it, or null when it has none (phase 3a).
     */
    data class Swap(val group: DaySwapGroup, val timed: TimedRequest? = null) : InboxItem {
        override val key: String get() = "sw_${group.key}"
    }

    /**
     * A pending change request, either way.
     *
     * @property timed Its clock as the inbox saw it, or null when it has none (phase 3a).
     */
    data class Request(val request: ChangeRequest, val timed: TimedRequest? = null) : InboxItem {
        override val key: String get() = "cr_${request.id}"
    }

    /** The whole history, as one group. */
    data class History(val entries: List<HistoryEntry>) : InboxItem {
        override val key: String get() = "history"
    }
}

/** What arriving at the inbox does once it has loaded. */
sealed interface InboxArrival {

    /** Nothing to scroll to: the plain inbox, or a target the list does not draw. */
    data object Stay : InboxArrival

    /** A chat card named an event that has no request at all — the screen says so. */
    data object Missing : InboxArrival

    /** Scroll the item at [index] into view. */
    data class ScrollTo(val index: Int) : InboxArrival
}

/**
 * What answers an event waiting on this parent.
 *
 * @property onAccept Takes it up; it then enters both calendars.
 * @property onDecline Turns it down; it stays out, and the creator learns the answer.
 */
data class AwaitingEventActions(val onAccept: () -> Unit, val onDecline: () -> Unit)

/**
 * The inbox as one flat list — the single record of what its `LazyColumn` draws, and so the only
 * thing a scroll target may be looked up in.
 *
 * It replaced counting headers by hand (`ChangeRequestHighlight.indexInInbox`), which left the
 * custody proposal out and scrolled one card short whenever one was pending.
 */
object InboxItems {

    /**
     * [inbox] in drawing order: what waits on this parent, what waits on the co-parent, then the
     * history. A section with nothing in it has no header either.
     *
     * Each request's item carries its clock ([RequestInbox.timedFor]), and says whether this parent
     * made it from the request itself rather than from its section: since requests are placed by
     * their clocks (phase 3a, spec §4.1), the co-parent's request waits in their section while its
     * last chance is theirs to give, and its card must not read as this parent's own.
     *
     * @param scope The family [inbox] is of: who this parent is, and the family a proposal's clock
     *   is named in.
     */
    fun of(inbox: RequestInbox, scope: RequestScope): List<InboxItem> = buildList {
        addWaiting(InboxSection.WAITING_ON_ME, inbox.waitingOnMe, inbox, scope)
        addWaiting(InboxSection.WAITING_ON_THEM, inbox.waitingOnThem, inbox, scope)
        if (inbox.history.isNotEmpty()) {
            add(InboxItem.Header(InboxSection.HISTORY))
            add(InboxItem.History(inbox.history))
        }
    }

    /** The index of [section]'s header, or -1 when the section is not drawn. */
    fun indexOf(items: List<InboxItem>, section: InboxSection): Int =
        items.indexOfFirst { it is InboxItem.Header && it.section == section }

    /**
     * Where the request [requestId] is drawn: its own card while it is pending, the history group
     * once it was answered or withdrawn, or -1 when it is in neither.
     */
    fun indexOfRequest(items: List<InboxItem>, requestId: String): Int {
        val card = items.indexOfFirst { it is InboxItem.Request && it.request.id == requestId }
        val historyKey = "cr_$requestId"
        return if (card >= 0) {
            card
        } else {
            items.indexOfFirst { item -> item is InboxItem.History && item.entries.any { it.key == historyKey } }
        }
    }

    /**
     * Where arriving at the inbox scrolls to. A chat card wins over a section: it names a request
     * by its event, and when the event has none at all the screen says it is gone.
     *
     * @param linkedEventId The event a chat card carried, or null.
     * @param requestId The request picked for that event (`ChangeRequestHighlight.forEvent`), or
     *   null when it has none.
     * @param section The section the route asked for, or null.
     */
    fun arrival(
        items: List<InboxItem>,
        linkedEventId: String?,
        requestId: String?,
        section: InboxSection?
    ): InboxArrival {
        val index = when {
            linkedEventId == null -> section?.let { indexOf(items, it) } ?: -1
            requestId == null -> null
            else -> indexOfRequest(items, requestId)
        }
        return when {
            index == null -> InboxArrival.Missing
            index >= 0 -> InboxArrival.ScrollTo(index)
            else -> InboxArrival.Stay
        }
    }

    private fun MutableList<InboxItem>.addWaiting(
        section: InboxSection,
        waiting: Waiting,
        inbox: RequestInbox,
        scope: RequestScope
    ) {
        if (waiting.isEmpty) return
        add(InboxItem.Header(section))
        waiting.proposal?.let {
            val timed = inbox.timedFor(RequestDeadlineId.forProposal(it, scope.familyId))
            add(InboxItem.Proposal(it, mine = it.proposedBy == scope.myUid, timed = timed))
        }
        waiting.split?.let {
            val timed = inbox.timedFor(RequestDeadlineId.forSplit(it, scope.familyId))
            add(InboxItem.Split(it, mine = it.proposedBy == scope.myUid, timed = timed))
        }
        waiting.events.forEach {
            val timed = inbox.timedFor(RequestDeadlineId.forEvent(it))
            add(InboxItem.AwaitingEvent(it, mine = it.createdByFirebaseUid == scope.myUid, timed = timed))
        }
        waiting.swaps.forEach { add(InboxItem.Swap(it, inbox.timedFor(RequestDeadlineId.forSwap(it)))) }
        waiting.changeRequests.forEach { add(InboxItem.Request(it, inbox.timedFor(RequestDeadlineId.forChange(it)))) }
    }
}
