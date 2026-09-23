package com.coparently.app.domain.holidays

import java.time.LocalDate

/**
 * German public holidays — **the nine that are nationwide, and no others.**
 *
 * Public holidays in Germany are state law. Nine are observed in every Land; the rest — Epiphany,
 * Corpus Christi, Assumption, Reformation Day, All Saints' Day and others — hold in some states
 * and not in others, and the app has no Bundesland setting to pick between them. A family in
 * Bavaria therefore sees fewer days off than it has. That is the same trade [CzechHolidays] makes
 * for the district-dependent spring break: an incomplete list of true days beats a complete list
 * that is wrong for half the country. A state setting is what would lift it (ROADMAP MON-13).
 *
 * **No school vacations.** German school holidays are set per state and shift every year; there
 * is no nationwide period to draw. (The reference library does carry per-state school holiday
 * tables, which is where a state setting would take them from.)
 *
 * Names and dates are the Python `holidays` library's (v0.105) for Germany without a subdivision,
 * and `HolidayReferenceTest` holds this table to it for every year of its fixture (2020–2035).
 * The one-off nationwide Reformation Day of 2017 is outside that range and not modelled.
 */
object GermanHolidays : HolidayProvider {

    override val localLanguage: String = "de"

    override val hasSchoolVacations: Boolean = false

    private val table = listOf(
        HolidayRule("New Year's Day", "Neujahr", fixed("--01-01")),
        HolidayRule("Good Friday", "Karfreitag", easter(GOOD_FRIDAY)),
        HolidayRule("Easter Monday", "Ostermontag", easter(EASTER_MONDAY)),
        HolidayRule("Labor Day", "Erster Mai", fixed("--05-01")),
        HolidayRule("Ascension Day", "Christi Himmelfahrt", easter(ASCENSION_DAY)),
        HolidayRule("Pentecost Monday", "Pfingstmontag", easter(WHIT_MONDAY)),
        HolidayRule("German Unity Day", "Tag der Deutschen Einheit", fixed("--10-03")),
        HolidayRule("Christmas Day", "Erster Weihnachtstag", fixed("--12-25")),
        HolidayRule("Second Day of Christmas", "Zweiter Weihnachtstag", fixed("--12-26"))
    )

    override fun publicHolidays(year: Int): List<Holiday> = table.holidaysIn(year, localLanguage)

    override fun schoolVacations(year: Int): List<Pair<ClosedRange<LocalDate>, Pair<String, String>>> =
        emptyList()
}
