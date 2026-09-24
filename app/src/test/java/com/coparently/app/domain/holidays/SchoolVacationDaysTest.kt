package com.coparently.app.domain.holidays

import org.junit.Test
import java.time.LocalDate
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * [HolidayProvider.schoolVacationDaysInRange], which feeds the month grid's school-vacation line.
 *
 * It exists because [HolidayProvider.holidaysInRange] keeps one [Holiday] per date with the public
 * holiday first, so a lookup built on it would drop 24–26 December out of the Christmas break.
 */
class SchoolVacationDaysTest {

    @Test
    fun `a public holiday inside a school vacation is still a vacation day`() {
        val days = CzechHolidays.schoolVacationDaysInRange(
            LocalDate.of(2026, 12, 20),
            LocalDate.of(2027, 1, 3)
        )

        // Christmas Eve is a Czech public holiday, so holidaysInRange names it and not the break.
        assertFalse(CzechHolidays.holidaysInRange(DEC_24, DEC_24).getValue(DEC_24).isSchoolVacation)
        assertTrue(DEC_24 in days)
        assertEquals(
            (23..31).map { LocalDate.of(2026, 12, it) }.toSet() +
                setOf(LocalDate.of(2027, 1, 1), LocalDate.of(2027, 1, 2)),
            days
        )
    }

    @Test
    fun `a period crossing a month boundary is returned on both sides of it`() {
        // Baden-Württemberg's Whitsun break, 26 May – 5 June 2026: the grid draws the line on the
        // days it borrows from June as well.
        val bw = GermanHolidays.forRegion("BW")
        val days = bw.schoolVacationDaysInRange(LocalDate.of(2026, 5, 25), LocalDate.of(2026, 6, 7))

        assertEquals(
            (26..31).map { LocalDate.of(2026, 5, it) }.toSet() +
                (1..5).map { LocalDate.of(2026, 6, it) }.toSet(),
            days
        )
    }

    @Test
    fun `a calendar without school vacations returns none`() {
        // Germany without a Land has no nationwide school period to draw.
        val days = GermanHolidays.schoolVacationDaysInRange(
            LocalDate.of(2026, 1, 1),
            LocalDate.of(2026, 12, 31)
        )

        assertTrue(days.isEmpty())
    }

    @Test
    fun `a range ending before it starts is empty`() {
        val days = CzechHolidays.schoolVacationDaysInRange(
            LocalDate.of(2026, 8, 1),
            LocalDate.of(2026, 7, 1)
        )

        assertTrue(days.isEmpty())
    }

    private companion object {
        val DEC_24: LocalDate = LocalDate.of(2026, 12, 24)
    }
}
