package com.coparently.app.domain.events

import com.coparently.app.domain.model.Event
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlinx.datetime.minus

/**
 * Whether an event reads as an all-day one, and which days it covers.
 *
 * The model has no all-day flag, so this is read from the shape: it starts at midnight and ends
 * at a midnight, at the last minute of a day (23:59 — how the school import's server and some
 * calendars close a whole day), or not at all. A Google import of a birthday or a school holiday
 * arrives as exactly this shape, and so does an all-day event made in the event form (October
 * 2026). Every surface that prints a time — Home's week and today cards, the event preview, the
 * "Today" widget, Day view's event block — used to print it as "00:00" / "12:00 AM" (D-18, and the
 * UI tour). One definition, so those surfaces cannot disagree about which events are all-day.
 */
object AllDayEvent {

    /**
     * From this time of day an end closes its day, rather than being a timed end. The event form
     * saves an all-day event's end here, as the Google and school imports do.
     */
    val LAST_MINUTE: LocalTime = LocalTime(23, 59)

    /** Whether [event] starts at midnight and ends at a day's end (or has no end). */
    fun isAllDay(event: Event): Boolean {
        if (event.startDateTime.time != MIDNIGHT) return false
        val end = event.endDateTime ?: return true
        val endTime = end.time
        return end >= event.startDateTime &&
            (endTime == MIDNIGHT || endTime >= LAST_MINUTE)
    }

    /**
     * The last day an all-day [event] covers: the day before an end at midnight (that midnight
     * is the start of the day after), the end's own day for an end at 23:59, and the start day
     * when there is no end.
     */
    fun lastDay(event: Event): LocalDate {
        val start = event.startDateTime.date
        val end = event.endDateTime ?: return start
        val last = if (end.time == MIDNIGHT) {
            end.date.minus(1, DateTimeUnit.DAY)
        } else {
            end.date
        }
        return if (last < start) start else last
    }

    /** 00:00, which kotlinx-datetime names no constant for. */
    private val MIDNIGHT: LocalTime = LocalTime(0, 0)
}
