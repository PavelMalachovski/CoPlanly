package com.coparently.app.domain.events

import com.coparently.app.domain.model.Event
import java.time.LocalDate
import java.time.LocalTime

/**
 * Whether an event reads as an all-day one, and which days it covers.
 *
 * The model has no all-day flag, so this is read from the shape: it starts at midnight and ends
 * at a midnight, at the last minute of a day (23:59 — how the school import's server and some
 * calendars close a whole day), or not at all. A Google import of a birthday or a school holiday
 * arrives as exactly this shape, and every surface that prints a time — Home's week and today
 * cards, the event preview, the "Today" widget — used to print it as "00:00" / "12:00 AM"
 * (D-18, and the UI tour). One definition, so those surfaces cannot disagree about which events
 * are all-day.
 */
object AllDayEvent {

    /** From this time of day an end closes its day, rather than being a timed end. */
    private val LAST_MINUTE: LocalTime = LocalTime.of(23, 59)

    /** Whether [event] starts at midnight and ends at a day's end (or has no end). */
    fun isAllDay(event: Event): Boolean {
        if (event.startDateTime.toLocalTime() != LocalTime.MIDNIGHT) return false
        val end = event.endDateTime ?: return true
        val endTime = end.toLocalTime()
        return !end.isBefore(event.startDateTime) &&
            (endTime == LocalTime.MIDNIGHT || !endTime.isBefore(LAST_MINUTE))
    }

    /**
     * The last day an all-day [event] covers: the day before an end at midnight (that midnight
     * is the start of the day after), the end's own day for an end at 23:59, and the start day
     * when there is no end.
     */
    fun lastDay(event: Event): LocalDate {
        val start = event.startDateTime.toLocalDate()
        val end = event.endDateTime ?: return start
        val last = if (end.toLocalTime() == LocalTime.MIDNIGHT) {
            end.toLocalDate().minusDays(1)
        } else {
            end.toLocalDate()
        }
        return if (last.isBefore(start)) start else last
    }
}
