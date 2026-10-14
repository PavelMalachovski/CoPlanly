package com.coparently.app.presentation.calendar

import com.coparently.app.domain.model.Event
import java.time.LocalDate

/**
 * Events covering [date], including multi-day and overnight spans, in start order.
 *
 * The reference definition of "which day does this event belong to". The UI does not call this
 * per day any more — [eventsByDay] is the indexed form it uses — but this stays as the spec the
 * index is held to: `EventsByDayTest` asserts the two agree on every day they touch, so the
 * agenda card under the grid can never disagree with the dots above it.
 *
 * @param events Events already filtered by parent and type
 * @param date The day to collect
 */
internal fun eventsOn(events: List<Event>, date: LocalDate): List<Event> {
    val dayStart = date.atStartOfDay()
    val dayEnd = date.plusDays(1).atStartOfDay()
    return events
        .filter { event ->
            val end = event.endDateTime ?: event.startDateTime
            event.startDateTime < dayEnd && end >= dayStart
        }
        .sortedBy { it.startDateTime }
}

/**
 * [events] bucketed by every day each one covers, each bucket in start order.
 *
 * Built once per event list so the month grid can index it instead of scanning: `dayContent`
 * runs for all 42 cells on every recomposition — a day tap, a filter change, any repository
 * emission — and a per-cell filter+sort made that O(42·N) over a list the ±3-month query window
 * grew by roughly 1.7×.
 *
 * A multi-day or overnight event is bucketed under each day of its span, not only its start day:
 * matching by start date alone is a bug this project has already shipped once (see the
 * range/day-query note in CLAUDE.md). Equivalent to calling [eventsOn] per day, which is what
 * `EventsByDayTest` checks.
 *
 * @param events Events already filtered by parent and type
 */
internal fun eventsByDay(events: List<Event>): Map<LocalDate, List<Event>> {
    val buckets = mutableMapOf<LocalDate, MutableList<Event>>()
    for (event in events) {
        val firstDay = event.startDateTime.toLocalDate()
        // `eventsOn` keeps an event whose end lands exactly on a day's 00:00 (`end >= dayStart`),
        // so the last covered day is the end's own date, not the day before it.
        val lastDay = (event.endDateTime ?: event.startDateTime).toLocalDate()
        var day = firstDay
        // An end before the start covers nothing, and this loop yields nothing for it — same
        // answer `eventsOn` gives such an event on every date.
        while (!day.isAfter(lastDay)) {
            buckets.getOrPut(day) { mutableListOf() }.add(event)
            day = day.plusDays(1)
        }
    }
    // sortedBy is stable, so events sharing a start time keep the incoming order, exactly as the
    // filter-then-sort in eventsOn did.
    return buckets.mapValues { (_, dayEvents) -> dayEvents.sortedBy { it.startDateTime } }
}
