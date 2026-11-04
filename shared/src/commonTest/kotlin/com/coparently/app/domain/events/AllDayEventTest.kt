package com.coparently.app.domain.events

import com.coparently.app.domain.model.Event
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.atTime
import kotlinx.datetime.plus
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Which events print "All day" instead of a midnight time. The UI tour showed a birthday running
 * 00:00–23:59 as "12:00 AM" on Home's week card.
 */
class AllDayEventTest {

    private val day = LocalDate(2026, 9, 30)

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
        val birthday = event(day.atTime(0, 0), day.atTime(23, 59))

        assertTrue(AllDayEvent.isAllDay(birthday))
        assertEquals(day, AllDayEvent.lastDay(birthday))
    }

    @Test
    fun `midnight to the next midnight is one whole day`() {
        val imported = event(day.atTime(0, 0), day.plus(1, DateTimeUnit.DAY).atTime(0, 0))

        assertTrue(AllDayEvent.isAllDay(imported))
        assertEquals(day, AllDayEvent.lastDay(imported))
    }

    @Test
    fun `a midnight start with no end is all day`() {
        assertTrue(AllDayEvent.isAllDay(event(day.atTime(0, 0), null)))
    }

    @Test
    fun `a school holiday over several days ends on its last day`() {
        val holiday = event(day.atTime(0, 0), day.plus(3, DateTimeUnit.DAY).atTime(0, 0))

        assertTrue(AllDayEvent.isAllDay(holiday))
        assertEquals(day.plus(2, DateTimeUnit.DAY), AllDayEvent.lastDay(holiday))
    }

    @Test
    fun `a timed event is not all day`() {
        assertFalse(AllDayEvent.isAllDay(event(day.atTime(17, 0), day.atTime(18, 0))))
        assertFalse(AllDayEvent.isAllDay(event(day.atTime(0, 0), day.atTime(1, 30))))
        assertFalse(AllDayEvent.isAllDay(event(day.atTime(9, 0), null)))
    }
}
