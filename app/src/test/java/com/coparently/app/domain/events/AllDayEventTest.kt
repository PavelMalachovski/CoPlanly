package com.coparently.app.domain.events

import com.coparently.app.domain.model.Event
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Which events print "All day" instead of a midnight time. The UI tour showed a birthday running
 * 00:00–23:59 as "12:00 AM" on Home's week card.
 */
class AllDayEventTest {

    private val day = LocalDate.of(2026, 9, 30)

    private fun event(start: LocalDateTime, end: LocalDateTime?) = Event(
        id = "e",
        title = "Grandma's birthday",
        startDateTime = start,
        endDateTime = end,
        eventType = "birthday",
        parentOwner = "dad",
        createdAt = start,
        updatedAt = start
    )

    @Test
    fun `midnight to the last minute of the day is all day`() {
        val birthday = event(day.atStartOfDay(), day.atTime(23, 59))

        assertTrue(AllDayEvent.isAllDay(birthday))
        assertEquals(day, AllDayEvent.lastDay(birthday))
    }

    @Test
    fun `midnight to the next midnight is one whole day`() {
        val imported = event(day.atStartOfDay(), day.plusDays(1).atStartOfDay())

        assertTrue(AllDayEvent.isAllDay(imported))
        assertEquals(day, AllDayEvent.lastDay(imported))
    }

    @Test
    fun `a midnight start with no end is all day`() {
        assertTrue(AllDayEvent.isAllDay(event(day.atStartOfDay(), null)))
    }

    @Test
    fun `a school holiday over several days ends on its last day`() {
        val holiday = event(day.atStartOfDay(), day.plusDays(3).atStartOfDay())

        assertTrue(AllDayEvent.isAllDay(holiday))
        assertEquals(day.plusDays(2), AllDayEvent.lastDay(holiday))
    }

    @Test
    fun `a timed event is not all day`() {
        assertFalse(AllDayEvent.isAllDay(event(day.atTime(17, 0), day.atTime(18, 0))))
        assertFalse(AllDayEvent.isAllDay(event(day.atStartOfDay(), day.atTime(1, 30))))
        assertFalse(AllDayEvent.isAllDay(event(day.atTime(9, 0), null)))
    }
}
