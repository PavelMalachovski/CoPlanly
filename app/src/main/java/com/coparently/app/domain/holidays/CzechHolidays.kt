package com.coparently.app.domain.holidays

import java.time.LocalDate

/**
 * A public holiday or school vacation day shown in the calendar.
 *
 * @property date The concrete date
 * @property nameEn English display name
 * @property nameCs Czech display name
 * @property isSchoolVacation True for school vacation days, false for public holidays
 */
data class Holiday(
    val date: LocalDate,
    val nameEn: String,
    val nameCs: String,
    val isSchoolVacation: Boolean = false
)

/**
 * Provider of Czech public holidays and nationwide school vacations.
 *
 * Public holidays are computed for any year (Easter via the anonymous
 * Gregorian computus). School vacations follow the official MŠMT calendar;
 * nationwide periods (autumn, Christmas, Easter, summer) are covered while
 * the district-dependent spring break is intentionally omitted.
 */
object CzechHolidays {

    /**
     * Returns all public holidays for the given year.
     */
    fun publicHolidays(year: Int): List<Holiday> {
        val easterSunday = easterSunday(year)
        return listOf(
            Holiday(LocalDate.of(year, 1, 1), "New Year's Day", "Nový rok"),
            Holiday(easterSunday.minusDays(2), "Good Friday", "Velký pátek"),
            Holiday(easterSunday.plusDays(1), "Easter Monday", "Velikonoční pondělí"),
            Holiday(LocalDate.of(year, 5, 1), "Labour Day", "Svátek práce"),
            Holiday(LocalDate.of(year, 5, 8), "Victory Day", "Den vítězství"),
            Holiday(LocalDate.of(year, 7, 5), "Saints Cyril and Methodius Day", "Den slovanských věrozvěstů Cyrila a Metoděje"),
            Holiday(LocalDate.of(year, 7, 6), "Jan Hus Day", "Den upálení mistra Jana Husa"),
            Holiday(LocalDate.of(year, 9, 28), "Czech Statehood Day", "Den české státnosti"),
            Holiday(LocalDate.of(year, 10, 28), "Independent Czechoslovak State Day", "Den vzniku samostatného československého státu"),
            Holiday(LocalDate.of(year, 11, 17), "Freedom and Democracy Day", "Den boje za svobodu a demokracii"),
            Holiday(LocalDate.of(year, 12, 24), "Christmas Eve", "Štědrý den"),
            Holiday(LocalDate.of(year, 12, 25), "Christmas Day", "1. svátek vánoční"),
            Holiday(LocalDate.of(year, 12, 26), "St. Stephen's Day", "2. svátek vánoční")
        )
    }

    /**
     * Returns nationwide school vacation periods that overlap the given year.
     * Each period is a pair of (start date inclusive, end date inclusive) with names.
     */
    fun schoolVacations(year: Int): List<Pair<ClosedRange<LocalDate>, Pair<String, String>>> {
        val easterSunday = easterSunday(year)
        // Easter school vacation: the Thursday before Good Friday
        val easterVacationStart = easterSunday.minusDays(3)

        return listOf(
            // Summer vacation (1 July – 31 August, fixed every year)
            LocalDate.of(year, 7, 1)..LocalDate.of(year, 8, 31) to
                ("Summer vacation" to "Hlavní prázdniny"),
            // Christmas vacation (approx. 23 Dec – 2 Jan; nationwide)
            LocalDate.of(year, 12, 23)..LocalDate.of(year, 12, 31) to
                ("Christmas vacation" to "Vánoční prázdniny"),
            LocalDate.of(year, 1, 1)..LocalDate.of(year, 1, 2) to
                ("Christmas vacation" to "Vánoční prázdniny"),
            // Autumn vacation (around 29–30 October, adjacent to 28 Oct holiday)
            LocalDate.of(year, 10, 29)..LocalDate.of(year, 10, 30) to
                ("Autumn vacation" to "Podzimní prázdniny"),
            // Easter vacation (Thursday before Good Friday)
            easterVacationStart..easterVacationStart to
                ("Easter vacation" to "Velikonoční prázdniny")
        )
    }

    /**
     * Returns the holiday (public holiday first, then school vacation) for a date,
     * or null when the date is a regular day.
     */
    fun holidayFor(date: LocalDate): Holiday? {
        publicHolidays(date.year).firstOrNull { it.date == date }?.let { return it }

        schoolVacations(date.year).firstOrNull { (range, _) -> date in range }?.let { (_, names) ->
            return Holiday(date, names.first, names.second, isSchoolVacation = true)
        }
        return null
    }

    /**
     * Returns all holidays (public + school vacations, day by day) within a range.
     * Keyed by date for quick lookup in calendar views.
     */
    fun holidaysInRange(start: LocalDate, end: LocalDate): Map<LocalDate, Holiday> {
        val result = mutableMapOf<LocalDate, Holiday>()
        var date = start
        while (!date.isAfter(end)) {
            holidayFor(date)?.let { result[date] = it }
            date = date.plusDays(1)
        }
        return result
    }

    /**
     * Anonymous Gregorian computus — Easter Sunday for the given year.
     */
    private fun easterSunday(year: Int): LocalDate {
        val a = year % 19
        val b = year / 100
        val c = year % 100
        val d = b / 4
        val e = b % 4
        val f = (b + 8) / 25
        val g = (b - f + 1) / 3
        val h = (19 * a + b - d - g + 15) % 30
        val i = c / 4
        val k = c % 4
        val l = (32 + 2 * e + 2 * i - h - k) % 7
        val m = (a + 11 * h + 22 * l) / 451
        val month = (h + l - 7 * m + 114) / 31
        val day = ((h + l - 7 * m + 114) % 31) + 1
        return LocalDate.of(year, month, day)
    }
}
