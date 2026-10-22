package com.coparently.app.presentation.calendar

import com.coparently.app.domain.events.CalendarVisibility
import com.coparently.app.domain.model.Event
import com.coparently.app.domain.usecase.RecurrenceExpander
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth

/**
 * What the mini month marks on a day besides its custody colour and its dot.
 */
enum class MiniMonthMark {
    /** The day of an event created for the reader, waiting for their answer. */
    NEW_EVENT,

    /**
     * A day offered in a swap (21 October 2026, Zuzana's test), drawn in the wash of the parent
     * it would go to, so the reader sees each day's direction before answering.
     */
    SWAP_DAY
}

/**
 * The dot a mini-month cell carries: the day's first event, coloured the way `MonthView` colours
 * it — the owner's slot, or the calendar friend's teal when a friend takes part.
 *
 * @property parentOwner The event's custody slot, `"mom"` or `"dad"`.
 * @property friendParticipates Whether a calendar friend takes part in the event.
 */
data class MiniMonthDot(val parentOwner: String, val friendParticipates: Boolean)

/**
 * Everything the mini month draws, computed off the main thread by its caller.
 *
 * Keyed by the grid's dates, borrowed days from the neighbouring months included, because the
 * custody band runs to the edge of the grid (design refresh item 10) and a dot on a borrowed day
 * is drawn the way `MonthView` draws it.
 *
 * @property month The month shown.
 * @property custody The custody slot of each grid date that has one; a date absent here has none.
 * @property dots The first event of each grid date that has one.
 * @property marks The days that carry a mark.
 */
data class MiniMonthModel(
    val month: YearMonth,
    val custody: Map<LocalDate, String>,
    val dots: Map<LocalDate, MiniMonthDot>,
    val marks: Map<LocalDate, MiniMonthMark>
) {
    companion object {
        /**
         * The model for [month].
         *
         * @param custodyFor Whose custody day a date is — the caller's `CustodyResolver`, the one
         *   custody lookup (UX/UI overhaul item 4).
         * @param events Events already visible and expanded; each date's dot is its first one.
         * @param marks The marked days.
         */
        fun build(
            month: YearMonth,
            custodyFor: (LocalDate) -> String?,
            events: List<Event>,
            marks: Map<LocalDate, MiniMonthMark>
        ): MiniMonthModel {
            val dates = MiniMonthGrid.weeks(month).flatten()
            val byDay = eventsByDay(events)
            return MiniMonthModel(
                month = month,
                custody = dates.mapNotNull { date -> custodyFor(date)?.let { date to it } }.toMap(),
                dots = dates.mapNotNull { date ->
                    byDay[date]?.firstOrNull()?.let { first ->
                        date to MiniMonthDot(
                            parentOwner = first.parentOwner,
                            friendParticipates = !first.friendParticipates.isNullOrBlank()
                        )
                    }
                }.toMap(),
                marks = marks
            )
        }

        /**
         * The mini month of an event waiting for an answer: the month it starts in, the family's
         * calendar as the reader's own month grid would draw it, and the event's start day marked.
         *
         * The waiting event itself draws no dot — it is in nobody's calendar until it is answered,
         * which is what [CalendarVisibility] says — so the mark is the only thing that shows it.
         *
         * @param familyEvents The family's events, every acceptance and recurring masters
         *   unexpanded; visibility is applied to the masters before expansion, as
         *   [CalendarVisibility] requires.
         * @param custodyFor Whose custody day a date is.
         */
        fun forAwaitingEvent(
            event: Event,
            familyEvents: List<Event>,
            custodyFor: (LocalDate) -> String?
        ): MiniMonthModel {
            val start = event.startDateTime.toLocalDate()
            val month = YearMonth.from(start)
            val dates = MiniMonthGrid.weeks(month).flatten()
            val shown = RecurrenceExpander.expandAll(
                CalendarVisibility.visible(familyEvents),
                dates.first().atStartOfDay(),
                dates.last().atTime(LocalTime.MAX)
            )
            return build(month, custodyFor, shown, mapOf(start to MiniMonthMark.NEW_EVENT))
        }
    }
}

/**
 * The mini months of a day swap: one per month its days fall in, soonest first, with the family's
 * agreed custody and each offered day ringed in the wash of the parent it would go to.
 *
 * @param days Each offered day and the slot it would go to.
 * @param familyEvents The family's events, as [MiniMonthModel.forAwaitingEvent] takes them.
 * @param custodyFor Whose custody day a date is now — the agreed schedule, the offer not applied.
 */
fun swapMiniMonths(
    days: Map<LocalDate, String>,
    familyEvents: List<Event>,
    custodyFor: (LocalDate) -> String?
): List<MiniMonthModel> =
    days.keys.map(YearMonth::from).distinct().sorted().map { month ->
        val dates = MiniMonthGrid.weeks(month).flatten()
        val shown = RecurrenceExpander.expandAll(
            CalendarVisibility.visible(familyEvents),
            dates.first().atStartOfDay(),
            dates.last().atTime(LocalTime.MAX)
        )
        MiniMonthModel.build(
            month = month,
            custodyFor = { date -> days[date] ?: custodyFor(date) },
            events = shown,
            // Only the days this grid draws: another month's day would be read out here too.
            marks = days.keys.filter { it in dates }.associateWith { MiniMonthMark.SWAP_DAY }
        )
    }

/** The mini month's layout: whole weeks, Monday first, as `MonthView` lays out its grid. */
object MiniMonthGrid {

    /**
     * The weeks that show [month]: from the Monday on or before its first day to the Sunday on or
     * after its last, so four to six rows.
     */
    fun weeks(month: YearMonth): List<List<LocalDate>> {
        var first = month.atDay(1)
        while (first.dayOfWeek != DayOfWeek.MONDAY) first = first.minusDays(1)
        var last = month.atEndOfMonth()
        while (last.dayOfWeek != DayOfWeek.SUNDAY) last = last.plusDays(1)
        val days = generateSequence(first) { it.plusDays(1) }.takeWhile { !it.isAfter(last) }.toList()
        return days.chunked(DAYS_IN_WEEK)
    }

    private const val DAYS_IN_WEEK = 7
}
