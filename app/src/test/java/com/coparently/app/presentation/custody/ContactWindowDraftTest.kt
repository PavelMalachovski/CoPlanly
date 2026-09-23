package com.coparently.app.presentation.custody

import com.coparently.app.domain.custody.ContactWindow
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The translation from what a parent picks ("every Wednesday", "Wednesday of week 2") to the
 * cycle days a pattern stores (MON-6b).
 */
class ContactWindowDraftTest {

    @Test
    fun `every Wednesday in a fortnight is two windows, one per week`() {
        val windows = draft(week = null).toWindows(MONDAY, cycleDays = 14)

        assertEquals(listOf(2, 9), windows.map { it.dayIndex })
        assertTrue(windows.all { it.parent == "dad" && it.start == FROM && it.end == TO })
    }

    @Test
    fun `a named week is only that week`() {
        assertEquals(listOf(9), draft(week = 1).toWindows(MONDAY, 14).map { it.dayIndex })
    }

    @Test
    fun `the weekday is read from the start date, not assumed to be Monday`() {
        // A cycle anchored on a Wednesday has its Wednesdays at 0 and 7.
        val wednesday = MONDAY.plusDays(2)

        assertEquals(listOf(0, 7), draft(week = null).toWindows(wednesday, 14).map { it.dayIndex })
    }

    @Test
    fun `a week past the end of the cycle, or an inverted time, gives nothing`() {
        assertEquals(emptyList(), draft(week = 2).toWindows(MONDAY, 14))
        assertEquals(emptyList(), draft(week = null).copy(end = FROM).toWindows(MONDAY, 14))
    }

    @Test
    fun `a custom cycle that is not whole weeks offers a partial last week`() {
        assertEquals(2, ContactWindowDraft.weeksIn(10))
        assertEquals(1, ContactWindowDraft.weeksIn(7))
        // Day 9 of a ten-day cycle anchored on a Monday is the second Wednesday.
        assertEquals(listOf(2, 9), draft(week = null).toWindows(MONDAY, 10).map { it.dayIndex })
    }

    private fun draft(week: Int?) = ContactWindowDraft(
        weekday = DayOfWeek.WEDNESDAY,
        week = week,
        start = FROM,
        end = TO,
        parent = ContactWindow.SLOT_TWO
    )

    private companion object {
        val MONDAY: LocalDate = LocalDate.of(2026, 8, 3)
        val FROM: LocalTime = LocalTime.of(15, 0)
        val TO: LocalTime = LocalTime.of(19, 0)
    }
}
