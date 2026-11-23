package com.coparently.app.presentation.home

import com.coparently.app.domain.model.ChangeRequest
import com.coparently.app.domain.model.ChangeRequestStatus
import com.coparently.app.presentation.calendar.MiniMonthMark
import com.coparently.app.presentation.calendar.MiniMonthModel
import com.coparently.app.presentation.changerequests.InboxItem
import com.coparently.app.presentation.changerequests.InboxSection
import com.coparently.app.presentation.changerequests.isMadeBy
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlinx.datetime.yearMonth

/**
 * What Home's Requests block draws (November 2026, board HomeG1), taken from the inbox's own flat
 * list ([com.coparently.app.presentation.changerequests.InboxItems]) so the two can never disagree:
 * an action card for each request under "Waiting for your answer", and a row for each under
 * "Waiting for {co-parent}". The inbox's History is "See all"'s, not Home's.
 *
 * @property forMe What waits on this parent, in the inbox's order.
 * @property forThem What waits on the co-parent, in the inbox's order.
 */
data class HomeRequestItems(
    val forMe: List<InboxItem> = emptyList(),
    val forThem: List<InboxItem> = emptyList()
) {
    /** Whether nothing waits on either parent. */
    val isEmpty: Boolean get() = forMe.isEmpty() && forThem.isEmpty()

    companion object {
        /** [items]' two waiting sections, headers and history left out. */
        fun of(items: List<InboxItem>): HomeRequestItems {
            val forMe = mutableListOf<InboxItem>()
            val forThem = mutableListOf<InboxItem>()
            var section: InboxSection? = null
            items.forEach { item ->
                when {
                    item is InboxItem.Header -> section = item.section
                    item is InboxItem.History -> Unit
                    section == InboxSection.WAITING_ON_ME -> forMe += item
                    section == InboxSection.WAITING_ON_THEM -> forThem += item
                }
            }
            return HomeRequestItems(forMe, forThem)
        }
    }
}

/**
 * What Home's Requests block shows (November 2026, board HomeG1).
 *
 * @property items What waits on each parent, from the inbox's own list.
 * @property forYou How many requests wait on this parent: the Home tab's badge and the inbox's count.
 * @property newCount How many of the co-parent's updates this parent has not opened yet ([unseenCount]).
 */
data class RequestsBlockState(
    val items: HomeRequestItems,
    val forYou: Int,
    val newCount: Int
)

/** What a "Waiting for {co-parent}" row offers at its end. */
enum class WaitingRowAction {
    /** This parent's own request: they may take it back, as in the inbox. */
    WITHDRAW,

    /**
     * The co-parent's custody or split proposal past its deadline: it waits on their last chance,
     * and until it is withdrawn it refuses this parent's own proposals, so Decline stays
     * ([com.coparently.app.presentation.changerequests.CardClock.declineOnly]).
     */
    DECLINE,

    /** Nothing to do here: the next move is the co-parent's, or the row opens its own screen. */
    NONE
}

/**
 * What [item]'s "Waiting for {co-parent}" row offers, from [myUid]'s side. A swap is withdrawn while
 * any of its days is still unanswered, a change request while it is pending — the inbox's rules.
 * Child support and a professional's access draw the inbox's own rows, with their own Withdraw.
 */
fun waitingRowAction(item: InboxItem, myUid: String): WaitingRowAction {
    val mine = item.isMadeBy(myUid)
    return when (item) {
        is InboxItem.Swap -> if (item.group.withdrawableBy(myUid)) WaitingRowAction.WITHDRAW else WaitingRowAction.NONE
        is InboxItem.Request ->
            if (mine && item.request.status == ChangeRequestStatus.PENDING) {
                WaitingRowAction.WITHDRAW
            } else {
                WaitingRowAction.NONE
            }
        is InboxItem.AwaitingEvent -> if (mine) WaitingRowAction.WITHDRAW else WaitingRowAction.NONE
        is InboxItem.Proposal, is InboxItem.Split -> if (mine) WaitingRowAction.WITHDRAW else WaitingRowAction.DECLINE
        is InboxItem.Support, is InboxItem.Professional, is InboxItem.Plan,
        is InboxItem.Header, is InboxItem.History -> WaitingRowAction.NONE
    }
}

/**
 * Whether [item]'s action card offers Details (owner request: every "Waiting for you" card does) —
 * every request answered on Home. Child support, a professional's access and the plan open their
 * own screens instead.
 */
fun hasDetails(item: InboxItem): Boolean = when (item) {
    is InboxItem.Swap, is InboxItem.Request, is InboxItem.AwaitingEvent,
    is InboxItem.Proposal, is InboxItem.Split -> true
    is InboxItem.Support, is InboxItem.Professional, is InboxItem.Plan,
    is InboxItem.Header, is InboxItem.History -> false
}

/**
 * How many of the co-parent's updates ([HomeFeed.items]) are newer than [seenAtMillis], the moment
 * this parent last opened the Requests inbox from Home's "See all": Home's "N new". With no mark —
 * never opened in this family on this phone — every update is new.
 *
 * An update's time is a wall clock, read in [zone], the reader's own.
 */
fun unseenCount(items: List<ActivityItem>, seenAtMillis: Long?, zone: TimeZone): Int {
    if (seenAtMillis == null) return items.size
    return items.count { it.timestamp.toInstant(zone).toEpochMilliseconds() > seenAtMillis }
}

/**
 * The mini month of a change request on Home's Details sheet: the month of the day it would move
 * the event to, in the family's custody, that day marked. No event dots: the sheet is about one day.
 *
 * @param custodyFor Whose day a date is — the family's one custody lookup.
 */
fun changeRequestMonth(request: ChangeRequest, custodyFor: (LocalDate) -> String?): MiniMonthModel {
    val day = request.proposedStartDateTime.date
    return MiniMonthModel.build(
        month = day.yearMonth,
        custodyFor = custodyFor,
        events = emptyList(),
        marks = mapOf(day to MiniMonthMark.PROPOSED_DAY)
    )
}
