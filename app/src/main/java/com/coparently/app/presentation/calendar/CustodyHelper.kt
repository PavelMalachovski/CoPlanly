package com.coparently.app.presentation.calendar

import com.coparently.app.data.local.entity.CustodyScheduleEntity
import com.coparently.app.domain.model.CustodyModel
import java.time.DayOfWeek
import java.time.LocalDate

/**
 * Helper utility for determining custody based on schedule.
 * Used to show indicators for which parent has custody on a given date.
 *
 * Supports two modes:
 * 1. Legacy: Day-of-week based schedules (CustodyScheduleEntity)
 * 2. New: Pattern-based custody models (CustodyModel)
 */
object CustodyHelper {
    /**
     * Determines which parent has custody on a given date based on a CustodyModel.
     * This is the preferred method for pattern-based custody.
     *
     * @param date The date to check custody for
     * @param model The custody model configuration
     * @return "mom" or "dad"
     */
    fun getCustodyForDate(date: LocalDate, model: CustodyModel): String {
        return model.getCustodyFor(date)
    }

    /**
     * Determines which parent has custody on a given date based on legacy schedules.
     * Falls back to this method when no CustodyModel is available.
     *
     * @param date The date to check custody for
     * @param schedules List of active custody schedules
     * @return "mom", "dad", or null if no schedule found
     */
    fun getCustodyForDate(
        date: LocalDate,
        schedules: List<CustodyScheduleEntity>
    ): String? {
        if (schedules.isEmpty()) return null

        val dayOfWeek = date.dayOfWeek.value // 1 = Monday, 7 = Sunday
        val scheduleForDay = schedules.find { it.dayOfWeek == dayOfWeek }

        return scheduleForDay?.parentOwner
    }

    /**
     * Checks if a date is today.
     *
     * @param date The date to check
     * @return true if the date is today, false otherwise
     */
    fun isToday(date: LocalDate): Boolean {
        return date == LocalDate.now()
    }

    /**
     * Gets the day of week value (1-7) from a LocalDate.
     *
     * @param date The date
     * @return Day of week value (1 = Monday, 7 = Sunday)
     */
    fun getDayOfWeekValue(date: LocalDate): Int {
        return date.dayOfWeek.value
    }

    /**
     * Checks if a date is a weekend (Saturday or Sunday).
     *
     * @param date The date to check
     * @return true if the date is Saturday or Sunday
     */
    fun isWeekend(date: LocalDate): Boolean {
        return date.dayOfWeek == DayOfWeek.SATURDAY || date.dayOfWeek == DayOfWeek.SUNDAY
    }
}
