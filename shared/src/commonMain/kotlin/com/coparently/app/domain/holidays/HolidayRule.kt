package com.coparently.app.domain.holidays

import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.plus

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
    val monthDay = IsoMonthDay.parse(isoMonthDay)
    return { year, _ -> monthDay.atYear(year) }
}

/** A holiday [days] after (or, negative, before) Western Easter Sunday. */
internal fun easter(days: Long): (Int, LocalDate) -> LocalDate =
    { _, easterSunday -> easterSunday.plus(days, DateTimeUnit.DAY) }

/**
 * A day of the year written as ISO `--MM-DD`, the form the tables use for fixed dates.
 *
 * It stands in for `java.time.MonthDay`, which the tables used until they moved to `:shared`
 * (MON-24), and behaves as the tables need it to: [parse] refuses text that is not `--MM-DD` or
 * names a day no year has, and [atYear] puts 29 February on the 28th in a common year. No table
 * has a 29 February today, so that rule is there for the next one that does.
 */
internal class IsoMonthDay private constructor(private val month: Int, private val day: Int) {

    /** This month and day in [year]; 29 February falls back to the 28th outside a leap year. */
    fun atYear(year: Int): LocalDate =
        if (month == FEBRUARY && day == LEAP_DAY && !isLeapYear(year)) {
            LocalDate(year, FEBRUARY, LEAP_DAY - 1)
        } else {
            LocalDate(year, month, day)
        }

    /** Parsing of the tables' `--MM-DD` text. */
    companion object {
        private val FORMAT = Regex("--([0-9]{2})-([0-9]{2})")

        /** The month-day [text] names; throws [IllegalArgumentException] on anything else. */
        fun parse(text: String): IsoMonthDay {
            val match = requireNotNull(FORMAT.matchEntire(text)) { "Not an ISO month-day: $text" }
            val month = match.groupValues[1].toInt()
            val day = match.groupValues[2].toInt()
            // A leap year accepts every day any year has, 29 February included, and refuses the rest.
            LocalDate(LEAP_REFERENCE_YEAR, month, day)
            return IsoMonthDay(month, day)
        }

        private fun isLeapYear(year: Int): Boolean =
            year % LEAP_CYCLE == 0 && (year % CENTURY != 0 || year % GREGORIAN_CYCLE == 0)

        private const val FEBRUARY = 2
        private const val LEAP_DAY = 29
        private const val LEAP_REFERENCE_YEAR = 2000
        private const val LEAP_CYCLE = 4
        private const val CENTURY = 100
        private const val GREGORIAN_CYCLE = 400
    }
}

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
