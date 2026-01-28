package com.coparently.app.presentation.calendar

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import java.time.LocalDate
import java.time.temporal.WeekFields
import java.util.Locale

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
                val weekFields = WeekFields.ISO // Always use Monday-first week
                val dayOfWeek = currentDate.dayOfWeek
                val daysFromMonday = (dayOfWeek.value - weekFields.firstDayOfWeek.value + 7) % 7
                currentDate.minusDays(daysFromMonday.toLong())
            }
            else -> {
                // For day and 3-day views, start from currentDate
                currentDate
            }
        }
        return (0 until daysCount).map { startDate.plusDays(it.toLong()) }
    }
}

