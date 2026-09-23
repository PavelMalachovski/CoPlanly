package com.coparently.app.domain.holidays

import java.time.LocalDate

/**
 * Austrian public holidays — the thirteen of the Holiday Rest Act (Feiertagsruhegesetz 1957),
 * which are nationwide.
 *
 * Some days a reader might expect are deliberately absent, and the reference data files every one
 * of them outside the public holidays. **Good Friday** was a day off for Protestants only, and
 * not even that since BGBl. I Nr. 22/2019. **24 and 31 December** are bank holidays, and so is
 * each Land's patron-saint day (St. Leopold, St. Joseph, St. Florian, …).
 *
 * **Which is why Austria has no region picker**, although Germany does (MON-13's regional half).
 * The reference library returns *no* regional public holiday for any of the nine Länder: the
 * patron-saint days are school-free and many offices close, but they are not statutory days off
 * for employees. A state setting here would change nothing on the grid, which is the promise
 * design rule 8 forbids; [regions] stays empty. Drawing the patron days as a separate, labelled
 * kind of day is a product decision, recorded in ROADMAP MON-13 rather than taken here.
 *
 * **No school vacations.** Austrian school holidays are set per state; there is no nationwide
 * period to draw without knowing the state.
 *
 * Names and dates are the Python `holidays` library's (v0.105), and `HolidayReferenceTest` holds
 * this table to it for every year of its fixture (2020–2035).
 */
object AustrianHolidays : HolidayProvider {

    override val localLanguage: String = "de"

    override val hasSchoolVacations: Boolean = false

    private val table = listOf(
        HolidayRule("New Year's Day", "Neujahr", fixed("--01-01")),
        HolidayRule("Epiphany", "Heilige Drei Könige", fixed("--01-06")),
        HolidayRule("Easter Monday", "Ostermontag", easter(EASTER_MONDAY)),
        HolidayRule("Labor Day", "Staatsfeiertag", fixed("--05-01")),
        HolidayRule("Ascension Day", "Christi Himmelfahrt", easter(ASCENSION_DAY)),
        HolidayRule("Pentecost Monday", "Pfingstmontag", easter(WHIT_MONDAY)),
        HolidayRule("Corpus Christi", "Fronleichnam", easter(CORPUS_CHRISTI)),
        HolidayRule("Assumption Day", "Mariä Himmelfahrt", fixed("--08-15")),
        HolidayRule("National Day", "Nationalfeiertag", fixed("--10-26")),
        HolidayRule("All Saints' Day", "Allerheiligen", fixed("--11-01")),
        HolidayRule("Immaculate Conception", "Mariä Empfängnis", fixed("--12-08")),
        HolidayRule("Christmas Day", "Christtag", fixed("--12-25")),
        HolidayRule("Saint Stephen's Day", "Stephanstag", fixed("--12-26"))
    )

    override fun publicHolidays(year: Int): List<Holiday> = table.holidaysIn(year, localLanguage)

    override fun schoolVacations(year: Int): List<Pair<ClosedRange<LocalDate>, Pair<String, String>>> =
        emptyList()
}
