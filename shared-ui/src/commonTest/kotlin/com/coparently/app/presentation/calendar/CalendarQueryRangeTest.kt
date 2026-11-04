package com.coparently.app.presentation.calendar

import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.YearMonth
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * The MONTH query window is anchored and sticky, so it must cover the grid of every month the
 * user can page to without re-anchoring. If it does not, paging shows a month whose events were
 * never loaded — an empty grid that looks like data loss.
 */
class CalendarQueryRangeTest {

    /**
     * The 42 cells kizitonwose renders for [month] under `OutDateStyle.EndOfGrid`: six rows
     * starting from the Monday on or before the 1st.
     */
    private fun gridBounds(month: YearMonth): Pair<LocalDate, LocalDate> {
        var start = month.firstDay
        while (start.dayOfWeek != DayOfWeek.MONDAY) {
            start = start.minus(1, DateTimeUnit.DAY)
        }
        return start to start.plus(41, DateTimeUnit.DAY)
    }

    private fun windowFor(anchor: YearMonth): Pair<LocalDate, LocalDate> {
        val (start, end) = queryRangeFor(CalendarViewMode.MONTH, anchor.firstDay)
        return start.date to end.date
    }

    @Test
    fun `the window covers every grid reachable without a re-anchor`() {
        val anchors = listOf(
            YearMonth(2026, 1),
            YearMonth(2026, 2), // short month
            YearMonth(2026, 8),
            YearMonth(2026, 11), // window crosses the year boundary
            YearMonth(2027, 1),
            YearMonth(2028, 2) // leap February
        )
        val tolerance = CalendarSelection.QUERY_ANCHOR_TOLERANCE_MONTHS

        for (anchor in anchors) {
            val (windowStart, windowEnd) = windowFor(anchor)
            for (offset in -tolerance..tolerance) {
                val displayed = anchor.plus(offset, DateTimeUnit.MONTH)
                val (gridStart, gridEnd) = gridBounds(displayed)
                assertTrue(
                    gridStart >= windowStart,
                    "grid of $displayed starts before the window anchored on $anchor"
                )
                assertTrue(
                    gridEnd <= windowEnd,
                    "grid of $displayed ends after the window anchored on $anchor"
                )
            }
        }
    }

    @Test
    fun `the window radius leaves room beyond the re-anchor tolerance`() {
        assertTrue(
            MONTH_WINDOW_RADIUS > CalendarSelection.QUERY_ANCHOR_TOLERANCE_MONTHS,
            "the window must reach further than the anchor is allowed to drift"
        )
    }

    @Test
    fun `the window is week-aligned so no partial row is unloaded`() {
        val (start, end) = queryRangeFor(CalendarViewMode.MONTH, YearMonth(2026, 8).firstDay)
        assertTrue(start.dayOfWeek == DayOfWeek.MONDAY, "window starts on a Monday")
        assertTrue(end.dayOfWeek == DayOfWeek.SUNDAY, "window ends on a Sunday")
    }
}
