package com.coparently.app.domain.holidays

import com.coparently.app.domain.holidays.SchoolBreak.JESENNE_PRAZDNINY
import com.coparently.app.domain.holidays.SchoolBreak.LETNE_PRAZDNINY
import com.coparently.app.domain.holidays.SchoolBreak.VELKONOCNE_PRAZDNINY
import com.coparently.app.domain.holidays.SchoolBreak.VIANOCNE_PRAZDNINY
import java.time.LocalDate

/**
 * Slovak public holidays — the days off under Act 241/1993, as amended.
 *
 * **The list depends on the year, and that is the point of writing it as rules.** Slovakia's
 * consolidation packages took days off the non-working list while leaving them on the statute
 * book as "state holidays" that are ordinary working days:
 * - Constitution Day (1 September) stopped being a day off from 2024 (Act 530/2023);
 * - Struggle for Freedom and Democracy Day (17 November) from 2025 (Act 261/2025);
 * - Victory over Fascism Day (8 May) and Our Lady of the Seven Sorrows (15 September) are working
 *   days in **2026 only** (Act 261/2025). They are days off again from 2027 because that is what
 *   the law says today; if a later package extends the suspension, this table and the reference
 *   fixture change together.
 *
 * Only days off are drawn. A state holiday that is a working day — 28 October among them — is not
 * a day a parent plans a handover around, and drawing it would say otherwise.
 *
 * **School vacations: the nationwide ones, as the ministry published them** (MŠVVaM SR,
 * "Termíny prázdnin", per school year). They are not computable, so they are a dated table,
 * [vacations], from school year 2025/26 to the last one published — the summer of 2028 at the
 * dataset commit the fixture pins. Two things are deliberately missing. The **spring holidays**
 * are set per region (kraj) in three staggered weeks, and the app has no Slovak region, so they
 * are left out — the trade [CzechHolidays] makes for its district-dependent spring break. And the
 * one-day **half-year holiday** (polročné prázdniny) is not in the source dataset, so it is not
 * drawn rather than typed from memory. `SchoolVacationReferenceTest` holds the table to the
 * fixture `tools/generate-school-vacation-fixture.py` writes from the OpenHolidays dataset.
 *
 * Names and dates are the Python `holidays` library's (v0.105), and `HolidayReferenceTest` holds
 * this table to it for every year of its fixture (2020–2035). Earlier years are not modelled —
 * the table does not know that 17 November only became a holiday in 2001 — because a calendar
 * app has no reason to draw them.
 */
object SlovakHolidays : HolidayProvider {

    override val localLanguage: String = "sk"

    override val hasSchoolVacations: Boolean = true

    /** Constitution Day is a working day from this year (Act 530/2023). */
    private const val CONSTITUTION_DAY_LAST_YEAR_OFF = 2023

    /** 17 November is a working day from this year (Act 261/2025). */
    private const val NOVEMBER_17_LAST_YEAR_OFF = 2024

    /** The one year Act 261/2025 made 8 May and 15 September working days. */
    private const val SUSPENSION_YEAR_2026 = 2026

    private val table = listOf(
        HolidayRule(
            "Day of the Establishment of the Slovak Republic",
            "Deň vzniku Slovenskej republiky",
            fixed("--01-01")
        ),
        HolidayRule(
            "Epiphany (Three Kings' Day and Orthodox Christmas)",
            "Zjavenie Pána (Traja králi a vianočný sviatok pravoslávnych kresťanov)",
            fixed("--01-06")
        ),
        HolidayRule("Good Friday", "Veľký piatok", easter(GOOD_FRIDAY)),
        HolidayRule("Easter Monday", "Veľkonočný pondelok", easter(EASTER_MONDAY)),
        HolidayRule("Labor Day", "Sviatok práce", fixed("--05-01")),
        HolidayRule(
            "Day of Victory over Fascism",
            "Deň víťazstva nad fašizmom",
            fixed("--05-08"),
            inYear = { it != SUSPENSION_YEAR_2026 }
        ),
        HolidayRule(
            "Saints Cyril and Methodius Day",
            "Sviatok svätého Cyrila a svätého Metoda",
            fixed("--07-05")
        ),
        HolidayRule(
            "Slovak National Uprising Anniversary",
            "Výročie Slovenského národného povstania",
            fixed("--08-29")
        ),
        HolidayRule(
            "Constitution Day",
            "Deň Ústavy Slovenskej republiky",
            fixed("--09-01"),
            inYear = { it <= CONSTITUTION_DAY_LAST_YEAR_OFF }
        ),
        HolidayRule(
            "Day of Our Lady of the Seven Sorrows",
            "Sedembolestná Panna Mária",
            fixed("--09-15"),
            inYear = { it != SUSPENSION_YEAR_2026 }
        ),
        HolidayRule("All Saints' Day", "Sviatok Všetkých svätých", fixed("--11-01")),
        HolidayRule(
            "Struggle for Freedom and Democracy Day",
            "Deň boja za slobodu a demokraciu",
            fixed("--11-17"),
            inYear = { it <= NOVEMBER_17_LAST_YEAR_OFF }
        ),
        HolidayRule("Christmas Eve", "Štedrý deň", fixed("--12-24")),
        HolidayRule("Christmas Day", "Prvý sviatok vianočný", fixed("--12-25")),
        HolidayRule("Second Day of Christmas", "Druhý sviatok vianočný", fixed("--12-26"))
    )

    override fun publicHolidays(year: Int): List<Holiday> = table.holidaysIn(year, localLanguage)

    /** The nationwide school vacations; see the class KDoc for what is left out and why. */
    private val vacations = listOf(
        JESENNE_PRAZDNINY.on("2025-10-30", "2025-10-31"),
        VIANOCNE_PRAZDNINY.on("2025-12-22", "2026-01-07"),
        VELKONOCNE_PRAZDNINY.on("2026-04-02", "2026-04-07"),
        LETNE_PRAZDNINY.on("2026-07-01", "2026-08-31"),
        JESENNE_PRAZDNINY.on("2026-10-29", "2026-10-30"),
        VIANOCNE_PRAZDNINY.on("2026-12-23", "2027-01-07"),
        VELKONOCNE_PRAZDNINY.on("2027-03-25", "2027-03-30"),
        LETNE_PRAZDNINY.on("2027-07-01", "2027-08-31"),
        JESENNE_PRAZDNINY.on("2027-10-28", "2027-10-29"),
        VIANOCNE_PRAZDNINY.on("2027-12-23", "2028-01-07"),
        VELKONOCNE_PRAZDNINY.on("2028-04-13", "2028-04-18"),
        LETNE_PRAZDNINY.on("2028-07-03", "2028-09-01")
    )

    override fun schoolVacations(year: Int): List<Pair<ClosedRange<LocalDate>, Pair<String, String>>> =
        vacations.overlapping(year)
}
