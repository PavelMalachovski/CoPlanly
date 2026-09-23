package com.coparently.app.domain.custody

import com.coparently.app.domain.holidays.CzechHolidays
import com.coparently.app.domain.holidays.SchoolVacationSuggestions
import com.coparently.app.domain.model.CustodyModel
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Holiday fairness (MON-20) and the school-holiday suggestions a seasonal layer is filled from
 * (MON-14). Both read the calendar's own custody lookup, so a swap, a layer and a contact window
 * count here exactly as the grid shows them.
 */
class HolidayFairnessTest {

    // Week on, week off from Monday 2026-09-07: slot 1 has the first week.
    private val base = CustodyModel.weekOnWeekOff(id = "m1", startDate = LocalDate.of(2026, 9, 7))

    private fun date(iso: String) = LocalDate.parse(iso)

    private fun fairness(
        model: CustodyModel,
        overrides: Map<String, DayOverride> = emptyMap(),
        birthdays: List<Pair<String, LocalDate>> = emptyList()
    ) = HolidayFairnessCalculator.of(
        year = 2026,
        custodyFor = CustodyResolver.resolver(model, overrides) { null },
        provider = CzechHolidays,
        birthdays = birthdays
    )

    private fun HolidayFairness.row(occasion: FairnessOccasion) = rows.single { it.occasion == occasion }

    @Test
    fun `every night of the year belongs to exactly one parent`() {
        val result = fairness(base)

        assertEquals(365, result.nightsBySlot.values.sum())
        // 2026-01-01 is floorMod(-249, 14) = 3 → slot 1; the two halves differ by at most a week.
        assertTrue(kotlin.math.abs(result.nightsBySlot.getValue("mom") - result.nightsBySlot.getValue("dad")) <= 7)
    }

    @Test
    fun `the fixed occasions are named and assigned`() {
        val result = fairness(base)

        // 2026-12-24 is floorMod(108, 14) = 10 → slot 2; 2026-12-25 is 11 → slot 2.
        assertEquals("dad", result.row(FairnessOccasion.ChristmasEve).wholeTo)
        assertEquals("dad", result.row(FairnessOccasion.ChristmasDay).wholeTo)
        // 2026-01-01 is index 3 → slot 1.
        assertEquals("mom", result.row(FairnessOccasion.NewYearsDay).wholeTo)
        // Easter 2026: Sunday 5 April (index 0? floorMod(-155, 14) = 13 → slot 2) and Monday (0 → slot 1).
        assertEquals(mapOf("dad" to 1, "mom" to 1), result.row(FairnessOccasion.Easter).daysBySlot)
        assertNull(result.row(FairnessOccasion.Easter).wholeTo)
    }

    @Test
    fun `an accepted swap moves a holiday, a pending one does not`() {
        val accepted = DayOverride("mom", "alice", "2026-12-01T10:00:00", DayOverrideStatus.ACCEPTED)
        val pending = DayOverride("mom", "alice", "2026-12-01T10:00:00", DayOverrideStatus.PENDING)
        val result = fairness(base, mapOf("2026-12-24" to accepted, "2026-12-25" to pending))

        assertEquals("mom", result.row(FairnessOccasion.ChristmasEve).wholeTo)
        assertEquals("dad", result.row(FairnessOccasion.ChristmasDay).wholeTo)
    }

    @Test
    fun `a seasonal layer counts, in nights and in the vacation it covers`() {
        val summer = SeasonalLayer.allWith("s", "Summer", date("2026-07-01")..date("2026-08-31"), "mom")
        val result = fairness(base.copy(seasonalLayers = listOf(summer)))

        val vacation = result.rows.single {
            (it.occasion as? FairnessOccasion.SchoolVacation)?.nameEn == "Summer vacation"
        }
        assertEquals(mapOf("mom" to 62), vacation.daysBySlot)
        assertTrue(result.nightsBySlot.getValue("mom") > fairness(base).nightsBySlot.getValue("mom"))
    }

    @Test
    fun `contact windows are not nights`() {
        val windows = (0 until 14).map { ContactWindow(it, LocalTime.of(15, 0), LocalTime.of(19, 0), "dad") }
        assertEquals(
            fairness(base).nightsBySlot,
            fairness(base.copy(contactWindows = windows)).nightsBySlot
        )
    }

    @Test
    fun `a birthday lands on its day this year, and 29 February on the 28th`() {
        val result = fairness(
            base,
            birthdays = listOf("Ema" to date("2019-12-24"), "Jan" to date("2020-02-29"))
        )

        assertEquals("dad", result.row(FairnessOccasion.Birthday("Ema")).wholeTo)
        assertEquals(date("2026-02-28"), result.row(FairnessOccasion.Birthday("Jan")).dates.start)
    }

    @Test
    fun `other public holidays are listed once, and the fixed dates are not repeated`() {
        val holidays = fairness(base).rows.mapNotNull { it.occasion as? FairnessOccasion.PublicHoliday }

        assertTrue(holidays.none { it.holiday.date == date("2026-12-24") || it.holiday.date == date("2026-01-01") })
        assertTrue(holidays.any { it.holiday.date == date("2026-10-28") })
    }

    @Test
    fun `without a schedule nobody has anything`() {
        val result = HolidayFairnessCalculator.of(2026, { null }, CzechHolidays, emptyList())

        assertTrue(result.nightsBySlot.isEmpty())
        assertTrue(result.rows.all { it.daysBySlot.isEmpty() })
    }

    // ---- "Fill from school holidays" ----------------------------------------

    @Test
    fun `the Czech breaks come widened over weekends and holidays, soonest first`() {
        val upcoming = SchoolVacationSuggestions.upcoming(CzechHolidays, date("2026-09-23"))

        assertEquals(
            listOf(
                date("2026-10-28")..date("2026-11-01"),
                date("2026-12-23")..date("2027-01-03"),
                date("2027-03-25")..date("2027-03-29"),
                date("2027-07-01")..date("2027-08-31")
            ),
            upcoming.take(4).map { it.dates }
        )
        assertEquals("Christmas vacation", upcoming[1].nameEn)
    }

    @Test
    fun `a calendar without school vacations suggests nothing`() {
        assertTrue(SchoolVacationSuggestions.upcoming(null, date("2026-09-23")).isEmpty())
    }
}
