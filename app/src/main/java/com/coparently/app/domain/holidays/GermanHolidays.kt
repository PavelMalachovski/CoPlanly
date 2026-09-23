package com.coparently.app.domain.holidays

import java.time.LocalDate

/**
 * German public holidays — **the nine that are nationwide, and no others.**
 *
 * Public holidays in Germany are state law. Nine are observed in every Land; the rest — Epiphany,
 * Corpus Christi, Assumption, Reformation Day, All Saints' Day and others — hold in some states
 * and not in others. This object is the nine, and it is what a parent who has not named a state
 * sees: an incomplete list of true days beats a complete list that is wrong for half the
 * country, the same trade [CzechHolidays] makes for the district-dependent spring break.
 *
 * **A parent who names their Land gets its days too** (`users.regionCode`, MON-13's regional
 * half): [forRegion] returns this table plus that state's rules from [GermanState], which says
 * what each state adds and which library entries are deliberately not drawn.
 *
 * **No school vacations**, with or without a state. German school holidays are set per state and
 * shift every year; the reference library carries them, but school calendars stay Czech-only
 * until there is a per-family school calendar to hang them on (ROADMAP MON-13).
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

    override val regions: List<String> = GermanState.entries.map { it.code }

    /** One provider per state, built once: the grid asks on every range change. */
    private val byState: Map<GermanState, HolidayProvider> by lazy {
        GermanState.entries.associateWith { StateHolidays(table + it.rules) }
    }

    override fun forRegion(regionCode: String?): HolidayProvider =
        GermanState.fromCode(regionCode)?.let { byState.getValue(it) } ?: this

    override fun publicHolidays(year: Int): List<Holiday> = table.holidaysIn(year, localLanguage)

    override fun schoolVacations(year: Int): List<Pair<ClosedRange<LocalDate>, Pair<String, String>>> =
        emptyList()

    /**
     * One Land's calendar: the nationwide table and the state's own rules, as one list so the
     * shared [holidaysIn] sorts them together. Has no regions of its own — a state is already the
     * finest division the app knows.
     */
    private class StateHolidays(private val rules: List<HolidayRule>) : HolidayProvider {
        override val localLanguage: String = "de"

        override val hasSchoolVacations: Boolean = false

        override fun publicHolidays(year: Int): List<Holiday> = rules.holidaysIn(year, localLanguage)

        override fun schoolVacations(year: Int): List<Pair<ClosedRange<LocalDate>, Pair<String, String>>> =
            emptyList()
    }
}
