package com.coparently.app.presentation.changerequests

import com.coparently.app.presentation.calendar.MiniMonthMark
import kotlinx.datetime.LocalDate
import kotlinx.datetime.YearMonth
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * A proposed schedule on a mini month (November 2026, board HomeScheduleDialog): which days change,
 * and in whose colour they are ringed.
 */
class ScheduleChangesTest {

    private val december = YearMonth(2026, 12)

    /** Now: every December day is the second parent's before the 7th, the first's from it. */
    private val current: (LocalDate) -> String? = { date -> if (date.day < 7) "dad" else "mom" }

    /** Proposed: Wednesdays and Thursdays go to the second parent, everything else to the first. */
    private val proposed: (LocalDate) -> String? = { date ->
        if (date.dayOfWeek.ordinal == 2 || date.dayOfWeek.ordinal == 3) "dad" else "mom"
    }

    @Test
    fun `a changed day is ringed in the colour of whom it would go to`() {
        val month = scheduleChangeMonth(december, proposed, current, showProposed = true, effectiveFrom = START)

        // Tuesday 1 December: the second parent's now, the first's under the proposal.
        assertEquals("mom", month.markSlots[LocalDate(2026, 12, 1)])
        assertEquals(MiniMonthMark.CUSTODY_CHANGE, month.marks[LocalDate(2026, 12, 1)])
        // Wednesday 9 December: the first parent's now, the second's under the proposal.
        assertEquals("dad", month.markSlots[LocalDate(2026, 12, 9)])
        // Monday 14 December stays the first parent's: no ring.
        assertTrue(LocalDate(2026, 12, 14) !in month.marks)
        assertEquals(month.marks.keys, month.markSlots.keys)
    }

    @Test
    fun `the proposed month is drawn in the proposed pattern`() {
        val month = scheduleChangeMonth(december, proposed, current, showProposed = true, effectiveFrom = START)

        assertEquals("dad", month.custody[LocalDate(2026, 12, 9)])
        assertEquals("mom", month.custody[LocalDate(2026, 12, 1)])
    }

    @Test
    fun `now is drawn in the current pattern and rings nothing`() {
        val month = scheduleChangeMonth(december, proposed, current, showProposed = false, effectiveFrom = START)

        assertEquals("dad", month.custody[LocalDate(2026, 12, 1)])
        assertTrue(month.marks.isEmpty())
        assertTrue(month.markSlots.isEmpty())
    }

    @Test
    fun `borrowed days carry the band and never a ring`() {
        val month = scheduleChangeMonth(december, proposed, current, showProposed = true, effectiveFrom = START)

        // Monday 30 November leads December's grid.
        assertEquals("mom", month.custody[LocalDate(2026, 11, 30)])
        assertTrue(month.marks.keys.all { it.month == december.month })
    }

    @Test
    fun `a day the proposal leaves to nobody is not a change`() {
        val changed = changedDays(december, proposed = { null }, current = current, effectiveFrom = START)

        assertTrue(changed.isEmpty())
    }

    @Test
    fun `days before the proposal takes effect keep the current schedule and are never ringed`() {
        // Accepted today, the proposal decides from Tuesday 15 December: the days before keep the
        // schedule agreed now as history (CustodyHistory, item 30).
        val from = LocalDate(2026, 12, 15)
        val month = scheduleChangeMonth(december, proposed, current, showProposed = true, effectiveFrom = from)

        // Tuesday 1 December changes under the proposal, but before the 15th it keeps its parent.
        assertEquals("dad", month.custody[LocalDate(2026, 12, 1)])
        assertTrue(LocalDate(2026, 12, 1) !in month.marks)
        // Wednesday 9 December too.
        assertEquals("mom", month.custody[LocalDate(2026, 12, 9)])
        assertTrue(month.marks.keys.all { it >= from })
        // Wednesday 16 December is the first changed day on or after it.
        assertEquals("dad", month.custody[LocalDate(2026, 12, 16)])
        assertEquals("dad", month.markSlots[LocalDate(2026, 12, 16)])
    }

    @Test
    fun `changed days start on the effective date`() {
        val from = LocalDate(2026, 12, 9)
        val changed = changedDays(december, proposed, current, effectiveFrom = from)

        assertEquals(LocalDate(2026, 12, 9), changed.keys.minOrNull())
        assertEquals("dad", changed[LocalDate(2026, 12, 9)])
    }

    private companion object {
        /** The proposal decides the whole of December: it takes effect before the month begins. */
        val START = LocalDate(2026, 11, 1)
    }
}
