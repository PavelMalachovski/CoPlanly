package com.coparently.app.presentation.calendar

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.isoDayNumber
import kotlinx.datetime.minus
import kotlinx.datetime.plus

/**
 * Helper object for date range calculations.
 * Optimizes date range generation by removing code duplication and
 * providing reusable functions for date range calculations.
 */
object DateRangeHelper {
    /**
     * Remembers and calculates date range starting from currentDate.
     * For week view (daysCount = 7), aligns to the start of the week (Monday).
     * For day and 3-day views, starts from currentDate.
     *
     * @param currentDate The current selected date
     * @param daysCount Number of days to show (1 for day, 3 for 3 days, 7 for week)
     * @return List of LocalDate objects representing the date range
     */
    @Composable
    fun rememberDateRange(
        currentDate: LocalDate,
        daysCount: Int
    ): List<LocalDate> = remember(currentDate, daysCount) {
        calculateDateRange(currentDate, daysCount)
    }

    /**
     * Calculates date range without remembering (for non-composable contexts).
     * For week view (daysCount = 7), aligns to the start of the week (Monday).
     * For day and 3-day views, starts from currentDate.
     *
     * @param currentDate The current selected date
     * @param daysCount Number of days to show (1 for day, 3 for 3 days, 7 for week)
     * @return List of LocalDate objects representing the date range
     */
    fun calculateDateRange(
        currentDate: LocalDate,
        daysCount: Int
    ): List<LocalDate> {
        val startDate = when {
            daysCount == 7 -> {
                // For week view, start from Monday of the week containing currentDate
                // Always a Monday-first week (`WeekFields.ISO`)
                val dayOfWeek = currentDate.dayOfWeek
                val daysFromMonday = (dayOfWeek.isoDayNumber - DayOfWeek.MONDAY.isoDayNumber + 7) % 7
                currentDate.minus(daysFromMonday, DateTimeUnit.DAY)
            }
            else -> {
                // For day and 3-day views, start from currentDate
                currentDate
            }
        }
        return (0 until daysCount).map { startDate.plus(it, DateTimeUnit.DAY) }
    }

    /**
     * [date]'s ISO week number, 1–53: `java.time`'s `WeekFields.ISO.weekOfWeekBasedYear()`, the
     * number Week view labels its header with. A week belongs to the year its Thursday falls in,
     * so 1 January can be in week 52 or 53 of the year before, and 31 December in week 1.
     */
    fun isoWeekNumber(date: LocalDate): Int {
        val week = (date.dayOfYear - date.dayOfWeek.isoDayNumber + THURSDAY_OFFSET) / DAYS_PER_WEEK
        return when {
            week < 1 -> weeksInIsoYear(date.year - 1)
            week > weeksInIsoYear(date.year) -> 1
            else -> week
        }
    }

    /** 53 when the year starts on a Thursday, or on a Wednesday in a leap year; else 52. */
    private fun weeksInIsoYear(year: Int): Int {
        val januaryFirst = LocalDate(year, 1, 1).dayOfWeek
        val leap = LocalDate(year + 1, 1, 1).minus(1, DateTimeUnit.DAY).dayOfYear == DAYS_IN_LEAP_YEAR
        return if (januaryFirst == DayOfWeek.THURSDAY || (leap && januaryFirst == DayOfWeek.WEDNESDAY)) {
            LONG_ISO_YEAR_WEEKS
        } else {
            SHORT_ISO_YEAR_WEEKS
        }
    }

    private const val DAYS_PER_WEEK = 7
    private const val THURSDAY_OFFSET = 10
    private const val DAYS_IN_LEAP_YEAR = 366
    private const val LONG_ISO_YEAR_WEEKS = 53
    private const val SHORT_ISO_YEAR_WEEKS = 52
}
