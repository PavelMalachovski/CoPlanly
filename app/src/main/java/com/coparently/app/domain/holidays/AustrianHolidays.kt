package com.coparently.app.domain.holidays

import com.coparently.app.domain.holidays.SchoolBreak.ALLERSEELEN
import com.coparently.app.domain.holidays.SchoolBreak.HERBSTFERIEN
import com.coparently.app.domain.holidays.SchoolBreak.OSTERFERIEN
import com.coparently.app.domain.holidays.SchoolBreak.PFINGSTFERIEN
import com.coparently.app.domain.holidays.SchoolBreak.WEIHNACHTSFERIEN
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
 * **School vacations: the nationwide periods only** — autumn (27–31 October), All Souls' Day,
 * Christmas, Easter and Whitsun, which are the same in every Land — as a dated
 * table, [vacations], from school year 2025/26 to the last one the dataset publishes (Christmas
 * 2028/29 at the commit the fixture pins). The **semester and summer breaks** are set per Land,
 * and they are left out rather than given a Land picker: the dataset has final Land dates only
 * for 2025/26, marks every later one `Provisional`, and at least one provisional grouping
 * disagrees with the ministry's published 2026/27 list (bmb.gv.at). A picker that added nothing
 * but a past school year would be design rule 8's affordance that does not deliver, so [regions]
 * stays empty until final Land dates can be sourced. The Länder's **patron-saint days**, which
 * are school-free in their Land, are left out for the same reason and because drawing them at all
 * is the open product decision above. `SchoolVacationReferenceTest` holds the table to the
 * fixture `tools/generate-school-vacation-fixture.py` writes from the OpenHolidays dataset.
 *
 * Names and dates are the Python `holidays` library's (v0.105), and `HolidayReferenceTest` holds
 * this table to it for every year of its fixture (2020–2035).
 */
object AustrianHolidays : HolidayProvider {

    override val localLanguage: String = "de"

    override val hasSchoolVacations: Boolean = true

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

    /** The nationwide school vacations; see the class KDoc for what is left out and why. */
    private val vacations = listOf(
        HERBSTFERIEN.on("2025-10-27", "2025-10-31"),
        ALLERSEELEN.on("2025-11-02"),
        WEIHNACHTSFERIEN.on("2025-12-24", "2026-01-06"),
        OSTERFERIEN.on("2026-03-28", "2026-04-06"),
        PFINGSTFERIEN.on("2026-05-23", "2026-05-25"),
        HERBSTFERIEN.on("2026-10-27", "2026-10-31"),
        ALLERSEELEN.on("2026-11-02"),
        WEIHNACHTSFERIEN.on("2026-12-24", "2027-01-06"),
        OSTERFERIEN.on("2027-03-20", "2027-03-29"),
        PFINGSTFERIEN.on("2027-05-15", "2027-05-17"),
        HERBSTFERIEN.on("2027-10-27", "2027-10-31"),
        ALLERSEELEN.on("2027-11-02"),
        WEIHNACHTSFERIEN.on("2027-12-24", "2028-01-06"),
        OSTERFERIEN.on("2028-04-08", "2028-04-17"),
        PFINGSTFERIEN.on("2028-06-03", "2028-06-05"),
        HERBSTFERIEN.on("2028-10-27", "2028-10-31"),
        ALLERSEELEN.on("2028-11-02"),
        WEIHNACHTSFERIEN.on("2028-12-24", "2029-01-06")
    )

    override fun schoolVacations(year: Int): List<Pair<ClosedRange<LocalDate>, Pair<String, String>>> =
        vacations.overlapping(year)
}
