package com.coparently.app.presentation.event

import com.coparently.app.domain.events.AllDayEvent
import com.coparently.app.domain.model.Event
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * [formStartDateTime], [formEndDateTime], [endDaySpanOf] and [endDaySpanFor]: what the event form
 * saves for its start date, end date, times and "All day" switch. Saving the end time on the start
 * date used to cut a multi-day event to one day, refuse an overnight one and give an event with no
 * end one, and until October 2026 the form had no way to create an event over several days.
 */
class EventFormEndTest {

    private fun event(start: LocalDateTime, end: LocalDateTime?) = Event(
        id = "e1",
        title = "Weekend",
        startDateTime = start,
        endDateTime = end,
        eventType = "general",
        parentOwner = "mom",
        createdAt = start,
        updatedAt = start
    )

    /** What the form saves for [loaded] when only its title was edited. */
    private fun savedEndOf(loaded: Event): LocalDateTime? {
        val start = loaded.startDateTime
        val endTime = loaded.endDateTime?.toLocalTime() ?: start.toLocalTime().plusHours(1)
        return formEndDateTime(start.toLocalDate(), endTime, endDaySpanOf(loaded), loaded.endDateTime == null)
    }

    @Test
    fun `a same-day event ends on its start date`() {
        val loaded = event(LocalDateTime.of(2026, 7, 20, 16, 0), LocalDateTime.of(2026, 7, 20, 17, 0))
        assertEquals(0, endDaySpanOf(loaded))
        assertEquals(loaded.endDateTime, savedEndOf(loaded))
    }

    @Test
    fun `a multi-day event keeps its end date when only the title changes`() {
        val loaded = event(LocalDateTime.of(2026, 7, 17, 18, 0), LocalDateTime.of(2026, 7, 19, 18, 0))
        assertEquals(2, endDaySpanOf(loaded))
        assertEquals(loaded.endDateTime, savedEndOf(loaded))
    }

    @Test
    fun `an overnight event ends the next day, after its start`() {
        val loaded = event(LocalDateTime.of(2026, 7, 20, 22, 0), LocalDateTime.of(2026, 7, 21, 2, 0))
        assertEquals(loaded.endDateTime, savedEndOf(loaded))
    }

    @Test
    fun `moving a multi-day event's start date moves its end with it`() {
        assertEquals(
            LocalDateTime.of(2026, 8, 3, 18, 0),
            formEndDateTime(LocalDate.of(2026, 8, 1), LocalTime.of(18, 0), 2, keepNoEnd = false)
        )
    }

    @Test
    fun `an event with no end keeps none until an end time is set`() {
        val loaded = event(LocalDateTime.of(2026, 7, 20, 0, 0), null)
        assertEquals(0, endDaySpanOf(loaded))
        assertNull(savedEndOf(loaded))
        assertEquals(
            LocalDateTime.of(2026, 7, 20, 3, 0),
            formEndDateTime(LocalDate.of(2026, 7, 20), LocalTime.of(3, 0), 0, keepNoEnd = false)
        )
    }

    @Test
    fun `an end date picked after the start spans the days between`() {
        assertEquals(4, endDaySpanFor(LocalDate.of(2026, 10, 23), LocalDate.of(2026, 10, 27)))
        assertEquals(0, endDaySpanFor(LocalDate.of(2026, 10, 23), LocalDate.of(2026, 10, 23)))
    }

    @Test
    fun `an end date before the start ends before the start, so the form refuses it`() {
        val span = endDaySpanFor(LocalDate.of(2026, 10, 23), LocalDate.of(2026, 10, 20))
        assertEquals(-3, span)
        val start = formStartDateTime(LocalDate.of(2026, 10, 23), LocalTime.of(9, 0), allDay = true)
        val end = formEndDateTime(
            LocalDate.of(2026, 10, 23),
            LocalTime.of(10, 0),
            span,
            keepNoEnd = false,
            allDay = true
        )
        assertTrue(checkNotNull(end).isBefore(start))
    }

    @Test
    fun `a new event that ends the next morning is saved after its start`() {
        val start = formStartDateTime(LocalDate.of(2026, 10, 23), LocalTime.of(18, 0), allDay = false)
        val end = formEndDateTime(LocalDate.of(2026, 10, 23), LocalTime.of(10, 0), 1, keepNoEnd = false)
        assertEquals(LocalDateTime.of(2026, 10, 23, 18, 0), start)
        assertEquals(LocalDateTime.of(2026, 10, 24, 10, 0), end)
    }

    @Test
    fun `an all-day event runs from the first day's start to the last day's last minute`() {
        val start = formStartDateTime(LocalDate.of(2026, 10, 23), LocalTime.of(15, 30), allDay = true)
        val end = formEndDateTime(
            LocalDate.of(2026, 10, 23),
            LocalTime.of(16, 30),
            2,
            keepNoEnd = false,
            allDay = true
        )
        assertEquals(LocalDateTime.of(2026, 10, 23, 0, 0), start)
        assertEquals(LocalDateTime.of(2026, 10, 25, 23, 59), end)
        // Every surface that prints a time reads this shape as all day, over exactly those days.
        val saved = event(start, end)
        assertTrue(AllDayEvent.isAllDay(saved))
        assertEquals(LocalDate.of(2026, 10, 25), AllDayEvent.lastDay(saved))
    }

    @Test
    fun `a one-day all-day event ends on its own day`() {
        val end = formEndDateTime(
            LocalDate.of(2026, 10, 23),
            LocalTime.of(9, 0),
            0,
            keepNoEnd = false,
            allDay = true
        )
        assertEquals(LocalDateTime.of(2026, 10, 23, 23, 59), end)
    }

    @Test
    fun `an all-day event loaded with no end keeps none`() {
        assertNull(
            formEndDateTime(LocalDate.of(2026, 10, 23), LocalTime.of(1, 0), 0, keepNoEnd = true, allDay = true)
        )
    }

    @Test
    fun `an all-day event ending at the next midnight opens on its own last day`() {
        // Older imports closed a whole day at the next midnight; the form's end date is the last
        // day the event covers, not the midnight after it.
        val loaded = event(LocalDateTime.of(2026, 10, 23, 0, 0), LocalDateTime.of(2026, 10, 26, 0, 0))
        assertEquals(2, endDaySpanOf(loaded))
        val closedAtLastMinute =
            event(LocalDateTime.of(2026, 10, 23, 0, 0), LocalDateTime.of(2026, 10, 25, 23, 59))
        assertEquals(2, endDaySpanOf(closedAtLastMinute))
    }

    @Test
    fun `a timed event ending at the next midnight keeps that day as its end date`() {
        val loaded = event(LocalDateTime.of(2026, 10, 23, 22, 0), LocalDateTime.of(2026, 10, 24, 0, 0))
        assertEquals(1, endDaySpanOf(loaded))
        assertEquals(loaded.endDateTime, savedEndOf(loaded))
    }

    @Test
    fun `a new event starts at the next full hour, not at the current minute`() {
        // UX audit H5: the form opened at 8:49 PM to 9:49 PM, which nearly every parent changed.
        assertEquals(LocalTime.of(21, 0), defaultStartTime(LocalTime.of(20, 49)))
        assertEquals(LocalTime.of(22, 0), defaultEndTime(defaultStartTime(LocalTime.of(20, 49))))
        assertEquals(LocalTime.of(9, 0), defaultStartTime(LocalTime.of(8, 0)))
    }

    @Test
    fun `the last hour of the day does not wrap past midnight`() {
        assertEquals(LocalTime.of(23, 0), defaultStartTime(LocalTime.of(23, 40)))
        assertEquals(AllDayEvent.LAST_MINUTE, defaultEndTime(LocalTime.of(23, 0)))
    }
}
