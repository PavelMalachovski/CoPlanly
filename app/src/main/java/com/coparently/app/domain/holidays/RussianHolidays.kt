package com.coparently.app.domain.holidays

import java.time.LocalDate

/**
 * Russian public holidays — the non-working days of Labour Code art. 112, **and not the days the
 * government moves around them each year.**
 *
 * Art. 112 fixes fourteen days: 1–6 and 8 January (the New Year holidays), 7 January (Orthodox
 * Christmas — a fixed Gregorian date, so no Julian computus is needed), 23 February, 8 March,
 * 1 May, 9 May, 12 June and 4 November. Those are computable for any year and are all this table
 * draws.
 *
 * What it deliberately does not draw is the **annual transfer decree**: each autumn the
 * government moves days off to build long weekends (and turns a Saturday into a working day to
 * pay for it), and it assigns the day in lieu of a holiday that fell on a weekend — which art. 112
 * says moves to the next working day, but which the decree routinely sends somewhere else
 * (23 February 2025, a Sunday, became 8 May). None of that can be computed for a year whose
 * decree has not been published, so a Russian family will see an ordinary weekday where their
 * bridge day is. The reference library carries those days only for years already decreed, as a
 * per-year list, and `tools/generate-holiday-fixture.py` filters them out of the comparison for
 * the same reason. Encoding a decreed year here would make this table right for one year and
 * silently incomplete for the next, which is worse than being consistently statutory.
 *
 * **No school vacations.** Russian school holidays are set by each region and often each school.
 *
 * Names and dates are the Python `holidays` library's (v0.105), and `HolidayReferenceTest` holds
 * this table to it for every year of its fixture (2020–2035).
 */
object RussianHolidays : HolidayProvider {

    override val localLanguage: String = "ru"

    override val hasSchoolVacations: Boolean = false

    private const val NEW_YEAR_HOLIDAYS_EN = "New Year Holidays"
    private const val NEW_YEAR_HOLIDAYS_RU = "Новогодние каникулы"

    private val table = listOf(
        HolidayRule(NEW_YEAR_HOLIDAYS_EN, NEW_YEAR_HOLIDAYS_RU, fixed("--01-01")),
        HolidayRule(NEW_YEAR_HOLIDAYS_EN, NEW_YEAR_HOLIDAYS_RU, fixed("--01-02")),
        HolidayRule(NEW_YEAR_HOLIDAYS_EN, NEW_YEAR_HOLIDAYS_RU, fixed("--01-03")),
        HolidayRule(NEW_YEAR_HOLIDAYS_EN, NEW_YEAR_HOLIDAYS_RU, fixed("--01-04")),
        HolidayRule(NEW_YEAR_HOLIDAYS_EN, NEW_YEAR_HOLIDAYS_RU, fixed("--01-05")),
        HolidayRule(NEW_YEAR_HOLIDAYS_EN, NEW_YEAR_HOLIDAYS_RU, fixed("--01-06")),
        HolidayRule("Christmas Day", "Рождество Христово", fixed("--01-07")),
        HolidayRule(NEW_YEAR_HOLIDAYS_EN, NEW_YEAR_HOLIDAYS_RU, fixed("--01-08")),
        HolidayRule("Defender of the Fatherland Day", "День защитника Отечества", fixed("--02-23")),
        HolidayRule("International Women's Day", "Международный женский день", fixed("--03-08")),
        HolidayRule("Holiday of Spring and Labor", "Праздник Весны и Труда", fixed("--05-01")),
        HolidayRule("Victory Day", "День Победы", fixed("--05-09")),
        HolidayRule("Russia Day", "День России", fixed("--06-12")),
        HolidayRule("Unity Day", "День народного единства", fixed("--11-04"))
    )

    override fun publicHolidays(year: Int): List<Holiday> = table.holidaysIn(year, localLanguage)

    override fun schoolVacations(year: Int): List<Pair<ClosedRange<LocalDate>, Pair<String, String>>> =
        emptyList()
}
