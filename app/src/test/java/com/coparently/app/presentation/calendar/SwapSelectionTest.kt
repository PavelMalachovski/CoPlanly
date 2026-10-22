package com.coparently.app.presentation.calendar

import com.coparently.app.presentation.calendar.components.isConsecutive
import org.junit.Test
import java.time.LocalDate
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Picking days for one swap offer (21 October 2026): any days, and a tap on a picked day — the
 * middle one included — unpicks it alone.
 */
class SwapSelectionTest {

    private val monday = LocalDate.of(2026, 10, 19)
    private fun day(offset: Long) = monday.plusDays(offset)

    @Test
    fun `a tap on the middle of three picked days unpicks that day alone`() {
        val three = setOf(day(0), day(1), day(2))

        assertEquals(setOf(day(0), day(2)), SwapSelection.toggled(three, day(1)))
    }

    @Test
    fun `a tap on a day apart from the selection adds that day, not the gap`() {
        assertEquals(setOf(day(0), day(5)), SwapSelection.toggled(setOf(day(0)), day(5)))
    }

    @Test
    fun `a tap past the cap changes nothing, and a tap on a picked day still unpicks it`() {
        val full = (0L until SwapSelection.MAX_DAYS).map(::day).toSet()

        assertEquals(full, SwapSelection.toggled(full, day(40)))
        assertEquals(full - day(3), SwapSelection.toggled(full, day(3)))
    }

    @Test
    fun `a drag adds the run from its anchor to what was picked before, in either direction`() {
        val before = setOf(day(10))

        assertEquals(setOf(day(10), day(0), day(1), day(2)), SwapSelection.dragged(before, day(0), day(2)))
        assertEquals(setOf(day(10), day(2), day(1), day(0)), SwapSelection.dragged(before, day(2), day(0)))
        // Back over the anchor: the run is the anchor alone again.
        assertEquals(setOf(day(10), day(2)), SwapSelection.dragged(before, day(2), day(2)))
    }

    @Test
    fun `a drag past the cap keeps the anchor and trims the far end`() {
        val dragged = SwapSelection.dragged(emptySet(), day(30), day(0))

        assertEquals(SwapSelection.MAX_DAYS, dragged.size)
        assertTrue(day(30) in dragged)
        assertTrue(day(0) !in dragged)
    }

    @Test
    fun `the sheet names a run by its ends and days picked apart one by one`() {
        assertTrue(isConsecutive(listOf(day(0), day(1), day(2))))
        assertFalse(isConsecutive(listOf(day(0), day(2))))
    }
}
