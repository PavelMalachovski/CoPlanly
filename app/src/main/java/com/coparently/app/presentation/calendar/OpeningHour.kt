package com.coparently.app.presentation.calendar

import com.coparently.app.domain.events.AllDayEvent
import com.coparently.app.domain.model.Event
import java.time.LocalDate
import java.time.LocalTime

/**
 * The hour Day and Week view open on: an hour before the first timed event of the days shown, and
 * on a span holding today never later than an hour before now (second audit: both views opened at
 * the current hour, so at four in the afternoon a day with a 3:30 pickup looked empty).
 *
 * All-day events are left out, because they are not drawn on the hour grid. With no timed event it
 * is an hour before now, as it always was.
 */
internal object OpeningHour {

    private const val LAST_HOUR = 23

    /**
     * @param events The events the view draws.
     * @param dates The days the view shows.
     * @param today Today's date.
     * @param now The time now.
     * @return The index of the hour row to put at the top, 0 to 23.
     */
    fun of(events: List<Event>, dates: List<LocalDate>, today: LocalDate, now: LocalTime): Int {
        val first = events.asSequence()
            .filterNot { AllDayEvent.isAllDay(it) }
            .filter { it.startDateTime.toLocalDate() in dates }
            .minOfOrNull { it.startDateTime.hour }
        val hour = when {
            first == null -> now.hour
            today in dates -> minOf(first, now.hour)
            else -> first
        }
        return (hour - 1).coerceIn(0, LAST_HOUR)
    }
}
