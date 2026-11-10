package com.coparently.app.presentation.calendar

import kotlinx.datetime.toKotlinLocalDate
import org.junit.Test
import java.time.LocalDate
import java.time.temporal.WeekFields
import kotlin.test.assertEquals

/**
 * Week view's header number (MON-24 phase 2, slice 11c): [DateRangeHelper.isoWeekNumber] is
 * `java.time`'s `WeekFields.ISO.weekOfWeekBasedYear()`, which the header read before it moved, on
 * every day of seventy years — the 53-week years and the days at each year's edges included.
 */
class IsoWeekNumberTest {

    @Test
    fun `every day reads the week java time reads`() {
        var day = LocalDate.of(1990, 1, 1)
        val end = LocalDate.of(2060, 12, 31)
        while (!day.isAfter(end)) {
            assertEquals(
                day.get(WeekFields.ISO.weekOfWeekBasedYear()),
                DateRangeHelper.isoWeekNumber(day.toKotlinLocalDate()),
                day.toString()
            )
            day = day.plusDays(1)
        }
    }
}
