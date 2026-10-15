package com.coparently.app.presentation.changerequests

import com.coparently.app.domain.changerequests.HistoryEntry
import com.coparently.app.domain.changerequests.RequestInbox
import com.coparently.app.domain.changerequests.Waiting
import com.coparently.app.domain.custody.CustodyProposal
import com.coparently.app.domain.custody.DaySwapGroup
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
     */
    data class Proposal(val proposal: CustodyProposal, val mine: Boolean) : InboxItem {
        override val key: String get() = if (mine) "p_mine" else "p_theirs"
    }

    /**
     * An event created for the other parent's days, waiting on their answer.
     *
     * @property mine Whether this parent created it — then the card shows what was sent, with no
     *   buttons.
     */
    data class AwaitingEvent(val event: Event, val mine: Boolean) : InboxItem {
        override val key: String get() = "ev_${event.id}"
    }

    /** A day-swap offer, one per offer however many days it covers. */
    data class Swap(val group: DaySwapGroup) : InboxItem {
        override val key: String get() = "sw_${group.key}"
    }

    /** A pending change request, either way. */
    data class Request(val request: ChangeRequest) : InboxItem {
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
     */
    fun of(inbox: RequestInbox): List<InboxItem> = buildList {
        addWaiting(InboxSection.WAITING_ON_ME, inbox.waitingOnMe, mine = false)
        addWaiting(InboxSection.WAITING_ON_THEM, inbox.waitingOnThem, mine = true)
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

    private fun MutableList<InboxItem>.addWaiting(section: InboxSection, waiting: Waiting, mine: Boolean) {
        if (waiting.isEmpty) return
        add(InboxItem.Header(section))
        waiting.proposal?.let { add(InboxItem.Proposal(it, mine)) }
        waiting.events.forEach { add(InboxItem.AwaitingEvent(it, mine)) }
        waiting.swaps.forEach { add(InboxItem.Swap(it)) }
        waiting.changeRequests.forEach { add(InboxItem.Request(it)) }
    }
}
