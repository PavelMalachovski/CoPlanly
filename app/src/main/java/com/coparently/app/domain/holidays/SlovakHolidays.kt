package com.coparently.app.domain.holidays

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
 * **No school vacations.** Slovakia's spring break is set per region, and the rest of the school
 * calendar is published per school year by the ministry rather than being computable; returning
 * none is the honest answer [HolidayProvider] allows, not a claim that there are none.
 *
 * Names and dates are the Python `holidays` library's (v0.105), and `HolidayReferenceTest` holds
 * this table to it for every year of its fixture (2020–2035). Earlier years are not modelled —
 * the table does not know that 17 November only became a holiday in 2001 — because a calendar
 * app has no reason to draw them.
 */
object SlovakHolidays : HolidayProvider {

    override val localLanguage: String = "sk"

    override val hasSchoolVacations: Boolean = false

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

    override fun schoolVacations(year: Int): List<Pair<ClosedRange<LocalDate>, Pair<String, String>>> =
        emptyList()
}
