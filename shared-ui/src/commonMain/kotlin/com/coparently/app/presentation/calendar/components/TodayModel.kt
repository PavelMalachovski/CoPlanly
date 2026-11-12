package com.coparently.app.presentation.calendar.components

import com.coparently.app.domain.events.AllDayEvent
import com.coparently.app.domain.home.TodayAgenda
import com.coparently.app.domain.model.Event
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime

/**
 * What the calendar's Today card says: the day, whose day it is with the children's names, and
 * the next event. Built from [TodayAgenda], the value `HomeWeek.todayOf` gives Home's today card
 * and the widget, so the three never disagree about today (design item 18).
 *
 * @property date Today
 * @property dayParent The slot whose day it is, or null when no arrangement answers
 * @property childNames The children to name with that parent; empty on a day they are apart
 *   (FAM-4), when the card says whose day it is instead of claiming they are together
 * @property nextEvent The first event of today that has not ended, a timed one before an all-day
 *   one; null when nothing is left
 * @property hasEvents Whether today held any event at all, to tell "nothing scheduled" from
 *   "nothing more today"
 */
data class TodayModel(
    val date: LocalDate,
    val dayParent: String?,
    val childNames: List<String>,
    val nextEvent: Event?,
    val hasEvents: Boolean
) {
    companion object {
        /**
         * @param agenda Today's agenda, from `HomeWeek.todayOf`
         * @param childNames The family's children, in display order
         * @param childrenApart Whether a child's own schedule puts the children with different
         *   parents today (`ChildrenToday`)
         * @param now The moment the card is drawn
         */
        fun of(
            agenda: TodayAgenda,
            childNames: List<String>,
            childrenApart: Boolean,
            now: LocalDateTime
        ): TodayModel {
            val ahead = agenda.events.filter { (it.endDateTime ?: it.startDateTime) >= now }
            return TodayModel(
                date = agenda.date,
                dayParent = agenda.dayParent,
                childNames = if (childrenApart) emptyList() else childNames,
                nextEvent = ahead.firstOrNull { !AllDayEvent.isAllDay(it) } ?: ahead.firstOrNull(),
                hasEvents = agenda.events.isNotEmpty()
            )
        }
    }
}
