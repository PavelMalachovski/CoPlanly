package com.coparently.app.presentation.calendar

import com.coparently.app.domain.model.Event
import kotlinx.datetime.LocalDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** How many of a day's events a month cell draws as pills, and the "+n" it leaves for the rest. */
class MonthCellPillsTest {

    private val start = LocalDateTime(2026, 11, 12, 9, 0)

    private companion object {
        const val LINE = 13f
        const val GAP = 2f
    }

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
    fun `three events are two pills and one more`() {
        val events = listOf(event("Dentist"), event("Dance"), event("Riding"))

        val pills = pillsFor(events)

        assertEquals(events.take(2), pills.shown)
        assertEquals(1, pills.more)
    }

    @Test
    fun `a smaller max shows that many and counts the rest`() {
        val events = listOf(event("Dentist"), event("Dance"), event("Riding"))

        val pills = pillsFor(events, max = 1)

        assertEquals(listOf(events.first()), pills.shown)
        assertEquals(2, pills.more)
    }

    @Test
    fun `room for five lines gives two pills two lines each`() {
        assertEquals(listOf(2, 2), pillLines(wanted = 2, height = 5 * LINE + GAP, lineHeight = LINE, gap = GAP))
    }

    @Test
    fun `room for three lines gives the first pill two and the second one`() {
        assertEquals(listOf(2, 1), pillLines(wanted = 2, height = 3 * LINE + GAP, lineHeight = LINE, gap = GAP))
    }

    @Test
    fun `a single event takes two lines at most however tall the cell`() {
        assertEquals(listOf(2), pillLines(wanted = 1, height = 10 * LINE, lineHeight = LINE, gap = GAP))
    }

    @Test
    fun `room for one line draws one pill on one line`() {
        assertEquals(listOf(1), pillLines(wanted = 2, height = LINE + GAP, lineHeight = LINE, gap = GAP))
    }

    @Test
    fun `a cell shorter than a line draws no pill`() {
        assertTrue(pillLines(wanted = 2, height = LINE - 1f, lineHeight = LINE, gap = GAP).isEmpty())
    }

    @Test
    fun `the gap between two pills counts against the room`() {
        // Two lines exactly, but not two lines and the gap: one pill, on two lines.
        assertEquals(listOf(2), pillLines(wanted = 2, height = 2 * LINE, lineHeight = LINE, gap = GAP))
    }

    @Test
    fun `no event asks for no pill`() {
        assertTrue(pillLines(wanted = 0, height = 5 * LINE, lineHeight = LINE, gap = GAP).isEmpty())
    }

    @Test
    fun `a title whose words all fit wraps at its spaces on the lines it has`() {
        assertEquals(2, pillTitleLines(widestWord = 30f, lineWidth = 40f, maxLines = 2))
    }

    @Test
    fun `a word exactly as wide as the line still fits`() {
        assertEquals(2, pillTitleLines(widestWord = 40f, lineWidth = 40f, maxLines = 2))
    }

    @Test
    fun `a word wider than the line puts the title on one line with an ellipsis`() {
        // "Football" in a narrow cell: never "Foot" over "ball".
        assertEquals(1, pillTitleLines(widestWord = 41f, lineWidth = 40f, maxLines = 2))
    }

    @Test
    fun `a pill with room for one line keeps one`() {
        assertEquals(1, pillTitleLines(widestWord = 10f, lineWidth = 40f, maxLines = 1))
    }

    @Test
    fun `the words of a title are what lies between its spaces`() {
        assertEquals(listOf("Parent", "meeting"), titleWords("  Parent\tmeeting "))
        assertTrue(titleWords(" ").isEmpty())
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
