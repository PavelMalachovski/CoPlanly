package com.coparently.app.domain.holidays

import org.junit.Test
import java.time.LocalDate
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Unit tests for CzechHolidays.
 * Verifies fixed holidays, the Easter computus and school vacations.
 */
class CzechHolidaysTest {

    @Test
    fun `there are 13 public holidays every year`() {
        assertEquals(13, CzechHolidays.publicHolidays(2026).size)
        assertEquals(13, CzechHolidays.publicHolidays(2030).size)
    }

    @Test
    fun `easter computus matches known dates`() {
        // Easter Sunday 2026-04-05 -> Good Friday 04-03, Easter Monday 04-06
        val holidays2026 = CzechHolidays.publicHolidays(2026)
        assertTrue(holidays2026.any { it.date == LocalDate.of(2026, 4, 3) && it.nameEn == "Good Friday" })
        assertTrue(holidays2026.any { it.date == LocalDate.of(2026, 4, 6) && it.nameEn == "Easter Monday" })

        // Easter Sunday 2025-04-20 -> Good Friday 04-18, Easter Monday 04-21
        val holidays2025 = CzechHolidays.publicHolidays(2025)
        assertTrue(holidays2025.any { it.date == LocalDate.of(2025, 4, 18) && it.nameEn == "Good Friday" })
        assertTrue(holidays2025.any { it.date == LocalDate.of(2025, 4, 21) && it.nameEn == "Easter Monday" })
    }

    @Test
    fun `fixed holidays are present`() {
        val holidays = CzechHolidays.publicHolidays(2026).associateBy { it.date }
        assertNotNull(holidays[LocalDate.of(2026, 1, 1)])
        assertNotNull(holidays[LocalDate.of(2026, 5, 8)])
        assertNotNull(holidays[LocalDate.of(2026, 10, 28)])
        assertNotNull(holidays[LocalDate.of(2026, 12, 24)])
    }

    @Test
    fun `summer vacation is a school vacation`() {
        val holiday = CzechHolidays.holidayFor(LocalDate.of(2026, 7, 15))
        assertNotNull(holiday)
        assertTrue(holiday.isSchoolVacation)
        assertEquals("Summer vacation", holiday.nameEn)
    }

    @Test
    fun `public holiday takes precedence over vacation`() {
        // 24 December is both a public holiday and inside Christmas vacation
        val holiday = CzechHolidays.holidayFor(LocalDate.of(2026, 12, 24))
        assertNotNull(holiday)
        assertTrue(!holiday.isSchoolVacation)
    }

    @Test
    fun `regular day has no holiday`() {
        assertNull(CzechHolidays.holidayFor(LocalDate.of(2026, 3, 11)))
    }

    @Test
    fun `holidaysInRange maps every holiday day`() {
        val map = CzechHolidays.holidaysInRange(
            LocalDate.of(2026, 12, 20),
            LocalDate.of(2027, 1, 5)
        )
        // 23-31 Dec vacation + 24/25/26 holidays + 1-2 Jan vacation + 1 Jan holiday
        assertTrue(map.containsKey(LocalDate.of(2026, 12, 23)))
        assertTrue(map.containsKey(LocalDate.of(2026, 12, 31)))
        assertTrue(map.containsKey(LocalDate.of(2027, 1, 1)))
        assertTrue(map.containsKey(LocalDate.of(2027, 1, 2)))
        assertNull(map[LocalDate.of(2027, 1, 5)])
    }
}
