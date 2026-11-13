package com.coparently.app.presentation.calendar

import com.coparently.app.domain.model.Event
import kotlinx.datetime.LocalDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** How many of a day's events a month cell draws as pills, and the "+n" it leaves for the rest. */
class MonthCellPillsTest {

    private val start = LocalDateTime(2026, 11, 12, 9, 0)

    @Test
    fun `a day without events draws no pill and no count`() {
        val pills = pillsFor(emptyList())

        assertTrue(pills.shown.isEmpty())
        assertEquals(0, pills.more)
    }

    @Test
    fun `one event is one pill`() {
        val dentist = event("Dentist")

        val pills = pillsFor(listOf(dentist))

        assertEquals(listOf(dentist), pills.shown)
        assertEquals(0, pills.more)
    }

    @Test
    fun `three events are the first as a pill and two more`() {
        val events = listOf(event("Dentist"), event("Dance"), event("Riding"))

        val pills = pillsFor(events)

        assertEquals(listOf(events.first()), pills.shown)
        assertEquals(2, pills.more)
    }

    @Test
    fun `a larger max shows that many and counts the rest`() {
        val events = listOf(event("Dentist"), event("Dance"), event("Riding"))

        val pills = pillsFor(events, max = 2)

        assertEquals(events.take(2), pills.shown)
        assertEquals(1, pills.more)
    }

    private fun event(title: String) = Event(
        id = title,
        title = title,
        startDateTime = start,
        endDateTime = start,
        eventType = "general",
        parentOwner = "mom",
        createdAt = start,
        updatedAt = start
    )
}
