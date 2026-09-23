package com.coparently.app.domain.holidays

import java.time.LocalDate
import java.time.MonthDay

/**
 * The building blocks the MON-13 country tables are written in.
 *
 * Each table is a list of [HolidayRule]s — a date rule, both names, and the years the day is a
 * public holiday — so a provider reads as the statute it transcribes rather than as date
 * arithmetic. Fixed dates are ISO month-day strings (`"--12-25"`), which say what they mean and
 * keep the tables clear of detekt's MagicNumber without a suppression; movable feasts are named
 * offsets from [gregorianEasterSunday].
 *
 * Every table built from these is checked, date by date and name by name, against the Python
 * `holidays` library by `HolidayReferenceTest` (see `tools/generate-holiday-fixture.py`).
 */
internal class HolidayRule(
    val nameEn: String,
    val nameLocal: String,
    val date: (year: Int, easterSunday: LocalDate) -> LocalDate,
    val inYear: (year: Int) -> Boolean = { true }
)

/** A holiday on the same calendar day every year; [isoMonthDay] is ISO `--MM-DD`. */
internal fun fixed(isoMonthDay: String): (Int, LocalDate) -> LocalDate {
    val monthDay = MonthDay.parse(isoMonthDay)
    return { year, _ -> monthDay.atYear(year) }
}

/** A holiday [days] after (or, negative, before) Western Easter Sunday. */
internal fun easter(days: Long): (Int, LocalDate) -> LocalDate =
    { _, easterSunday -> easterSunday.plusDays(days) }

/** Good Friday: two days before Easter Sunday. */
internal const val GOOD_FRIDAY = -2L

/** Easter Monday. */
internal const val EASTER_MONDAY = 1L

/** Ascension Day: the fortieth day of Easter, counting Easter Sunday as the first. */
internal const val ASCENSION_DAY = 39L

/** Whit Monday (Pentecost Monday). */
internal const val WHIT_MONDAY = 50L

/** Corpus Christi: the Thursday after Trinity Sunday. */
internal const val CORPUS_CHRISTI = 60L

/**
 * The public holidays this table yields in [year], each named in English and in [language].
 *
 * Easter is computed once per call and only handed to the rules; a table with no movable feast
 * (Russia's) simply never reads it.
 */
internal fun List<HolidayRule>.holidaysIn(year: Int, language: String): List<Holiday> {
    val easterSunday = gregorianEasterSunday(year)
    return filter { it.inYear(year) }
        .map { rule ->
            Holiday(
                date = rule.date(year, easterSunday),
                nameEn = rule.nameEn,
                nameLocal = rule.nameLocal,
                localLanguage = language
            )
        }
        .sortedBy { it.date }
}
