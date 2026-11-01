package com.coparently.app.domain.time

import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.number
import kotlin.time.Instant

/**
 * The ISO 8601 text this app stores in Firestore and in its custody codecs, written and read in
 * common code so that an Android phone and an iPhone produce and accept the same bytes (MON-24).
 *
 * Every function reproduces, exactly, the documented output or acceptance of the `java.time` call
 * the Android build has always used for the same field — `DateTimeFormatter.ISO_LOCAL_DATE_TIME`,
 * `OffsetDateTime.parse`, `LocalDate.toString`/`LocalDate.parse`, `DateTimeFormatter.BASIC_ISO_DATE`
 * and the pattern `HH:mm` — because the text is shared state: a co-parent on an older Android build
 * reads what this code writes and writes what this code reads, and a single differing character (a
 * dropped `:00`, a year sign, a tolerated separator) would make the two phones disagree about a
 * document. The rules were written from ISO 8601, the `java.time` javadoc and black-box runs of
 * `java.time`; `WireTimeParityTest` holds them to `java.time` on seeded random and mutated text.
 *
 * Every parser throws [IllegalArgumentException] on text the corresponding `java.time` parse
 * refuses, and accepts all text it accepts. Digits are ASCII only, as `java.time`'s standard
 * decimal style reads them.
 */
object IsoText {

    /**
     * [value] as `ISO_LOCAL_DATE_TIME` formats it: the [date] text, `T`, two-digit hour, minute and
     * second (the second is always printed), then — only when the nanosecond is not zero — a decimal
     * point and the nanoseconds with trailing zeros removed (one to nine digits).
     */
    fun dateTime(value: LocalDateTime): String = buildString {
        appendIsoDate(value.date)
        append('T')
        appendTwoDigits(value.hour)
        append(':')
        appendTwoDigits(value.minute)
        append(':')
        appendTwoDigits(value.second)
        appendTrimmedFraction(value.nanosecond)
    }

    /**
     * Reads [text] as `LocalDateTime.parse(text, ISO_LOCAL_DATE_TIME)` does: a [parseDate] date, a
     * `T` in either case, `HH:mm`, optionally `:ss` and then optionally a decimal point followed by
     * zero to nine digits, and nothing after. Field values are checked strictly (no hour 24, no
     * second 60, no 29 February outside a leap year).
     *
     * @throws IllegalArgumentException when `java.time` would refuse the text.
     */
    fun parseDateTime(text: String): LocalDateTime {
        val reader = IsoReader(text)
        val date = reader.date()
        reader.dateTimeSeparator()
        val time = reader.time()
        reader.end()
        return LocalDateTime(date, time)
    }

    /**
     * Reads [text] as `OffsetDateTime.parse(text)` (`ISO_OFFSET_DATE_TIME`) does and returns the
     * instant it names: a [parseDateTime] date-time followed directly by an offset, which is `Z` in
     * either case or a sign with two-digit hours, optionally `:mm` and then optionally `:ss`, at most
     * 18 hours in total, and nothing after.
     *
     * An instant beyond the range [Instant] can hold is clamped to that range; every such instant is
     * also beyond the epoch milliseconds a [Long] holds, so [toEpochMillisExact] fails on it as
     * `Instant.toEpochMilli` failed on the unclamped one.
     *
     * @throws IllegalArgumentException when `java.time` would refuse the text.
     */
    fun parseOffsetDateTime(text: String): Instant {
        val reader = IsoReader(text)
        val date = reader.date()
        reader.dateTimeSeparator()
        val time = reader.time()
        val offsetSeconds = reader.offsetSeconds()
        reader.end()
        val epochSeconds = date.toEpochDays() * SECONDS_PER_DAY + time.toSecondOfDay() - offsetSeconds
        return Instant.fromEpochSeconds(epochSeconds, time.nanosecond)
    }

    /**
     * [value] as `LocalDate.toString()` and `ISO_LOCAL_DATE` write it: the year as four digits for
     * years 0 to 9999, with a `+` and every digit above 9999, with a `-` and at least four digits
     * below 0; then `-MM-dd`.
     */
    fun date(value: LocalDate): String = buildString { appendIsoDate(value) }

    /**
     * Reads [text] as `LocalDate.parse(text)` does: an unsigned year of exactly four digits, a `+`
     * year of five to ten digits, or a `-` year of four to ten digits that is not zero, in either
     * case at most 999 999 999; then `-`, a two-digit month, `-`, a two-digit day valid for that
     * month and year, and nothing after.
     *
     * @throws IllegalArgumentException when `java.time` would refuse the text.
     */
    fun parseDate(text: String): LocalDate {
        val reader = IsoReader(text)
        val date = reader.date()
        reader.end()
        return date
    }

    /**
     * [value] as `BASIC_ISO_DATE` formats a date: `yyyyMMdd`, the year in exactly four digits.
     *
     * @throws IllegalArgumentException for a year below 0 or above 9999, which `BASIC_ISO_DATE`
     * refuses to print rather than widen the field.
     */
    fun basicDate(value: LocalDate): String {
        require(value.year in 0..MAX_FOUR_DIGIT_YEAR) { "BASIC_ISO_DATE prints four-digit years only: ${value.year}" }
        return buildString {
            append(value.year.toString().padStart(YEAR_PAD, '0'))
            appendTwoDigits(value.month.number)
            appendTwoDigits(value.day)
        }
    }

    /** [value] as the pattern `HH:mm` formats it: two-digit hour and minute, seconds dropped. */
    fun hourMinute(value: LocalTime): String = buildString {
        appendTwoDigits(value.hour)
        append(':')
        appendTwoDigits(value.minute)
    }

    /**
     * Reads [text] as `LocalTime.parse(text, DateTimeFormatter.ofPattern("HH:mm"))` does: exactly
     * two digits, a colon and two digits, hour 0–23 and minute 0–59, except that `24:00` reads as
     * midnight (the pattern's smart resolver).
     *
     * @throws IllegalArgumentException when `java.time` would refuse the text.
     */
    fun parseHourMinute(text: String): LocalTime {
        val reader = IsoReader(text)
        val hour = reader.twoDigits()
        reader.literal(':')
        val minute = reader.twoDigits()
        reader.end()
        return when {
            hour == HOURS_PER_DAY && minute == 0 -> LocalTime(0, 0)
            hour < HOURS_PER_DAY && minute <= MAX_MINUTE -> LocalTime(hour, minute)
            else -> throw IllegalArgumentException("Not a time of day: $text")
        }
    }
}

/**
 * This instant in epoch milliseconds, exactly as `java.time.Instant.toEpochMilli` documents it: the
 * nanoseconds below the millisecond are dropped by flooring (so one nanosecond before the epoch is
 * -1, not 0), and an instant whose milliseconds do not fit in a [Long] throws rather than saturates.
 *
 * Unlike [Instant.toEpochMilliseconds], which clamps, this is what a stored timestamp may rely on:
 * a clamped value would silently compare equal to every other out-of-range one.
 *
 * @throws ArithmeticException when the result does not fit in a [Long].
 */
internal fun Instant.toEpochMillisExact(): Long {
    val seconds = epochSeconds
    val milliOfSecond = (nanosecondsOfSecond / NANOS_PER_MILLI).toLong()
    val fits = when {
        seconds > MAX_MILLIS_SECONDS || seconds < MIN_MILLIS_SECONDS -> false
        seconds == MAX_MILLIS_SECONDS -> milliOfSecond <= MAX_MILLIS_REMAINDER
        seconds == MIN_MILLIS_SECONDS -> milliOfSecond >= MIN_MILLIS_REMAINDER
        else -> true
    }
    if (!fits) throw ArithmeticException("Instant does not fit in epoch milliseconds: $this")
    // At MIN_MILLIS_SECONDS the product wraps, but the true sum fits in a Long, so the two's
    // complement sum is exactly it.
    return seconds * MILLIS_PER_SECOND + milliOfSecond
}

/**
 * A cursor over one piece of ISO text. Each step consumes what it reads and throws
 * [IllegalArgumentException] naming the whole text when the next characters are not what the
 * grammar allows there.
 */
private class IsoReader(private val text: String) {

    /** The index of the next unread character. */
    private var at = 0

    /** An ISO calendar date: year, `-`, month, `-`, day, checked to exist. */
    fun date(): LocalDate {
        val year = year()
        literal('-')
        val month = twoDigits()
        literal('-')
        val day = twoDigits()
        if (month !in 1..MONTHS_PER_YEAR || day !in 1..daysInMonth(year, month)) fail("no such date")
        return LocalDate(year, month, day)
    }

    /** The `T` between date and time, which `ISO_LOCAL_DATE_TIME` reads in either case. */
    fun dateTimeSeparator() {
        val c = text.getOrNull(at)
        if (c != 'T' && c != 't') fail("expected T")
        at++
    }

    /** `HH:mm`, then optionally `:ss`, then optionally `.` and zero to nine fraction digits. */
    fun time(): LocalTime {
        val hour = twoDigits()
        literal(':')
        val minute = twoDigits()
        var second = 0
        var nanosecond = 0
        if (skip(':')) {
            second = twoDigits()
            if (skip('.')) nanosecond = fraction()
        }
        if (hour >= HOURS_PER_DAY || minute > MAX_MINUTE || second > MAX_SECOND) fail("time out of range")
        return LocalTime(hour, minute, second, nanosecond)
    }

    /**
     * An offset in total seconds: `Z`/`z` for zero, or `+`/`-`, two-digit hours, then optionally
     * `:mm` and then optionally `:ss`, no more than 18 hours either way.
     */
    fun offsetSeconds(): Int {
        val sign = text.getOrNull(at)
        if (sign == 'Z' || sign == 'z') {
            at++
            return 0
        }
        if (sign != '+' && sign != '-') fail("expected an offset")
        at++
        val hours = twoDigits()
        var minutes = 0
        var seconds = 0
        if (skip(':')) {
            minutes = twoDigits()
            if (skip(':')) seconds = twoDigits()
        }
        val total = hours * SECONDS_PER_HOUR + minutes * SECONDS_PER_MINUTE + seconds
        if (minutes > MAX_MINUTE || seconds > MAX_SECOND || total > MAX_OFFSET_SECONDS) fail("offset out of range")
        return if (sign == '-') -total else total
    }

    /** Requires that every character has been read. */
    fun end() {
        if (at != text.length) fail("unexpected text after the value")
    }

    /** Requires [expected] next. */
    fun literal(expected: Char) {
        if (!skip(expected)) fail("expected '$expected'")
    }

    /** Exactly two ASCII digits, as a number. */
    fun twoDigits(): Int {
        val first = digitAt(at)
        val second = digitAt(at + 1)
        if (first < 0 || second < 0) fail("expected two digits")
        at += 2
        return first * DECIMAL + second
    }

    /**
     * A year as `ISO_LOCAL_DATE` reads it: unsigned, exactly four digits; after `+`, five to ten;
     * after `-`, four to ten and not zero; at most 999 999 999 either way.
     */
    private fun year(): Int {
        val sign = text.getOrNull(at)
        val signed = sign == '+' || sign == '-'
        if (signed) at++
        val start = at
        while (at - start < MAX_YEAR_DIGITS && digitAt(at) >= 0) at++
        val digits = text.substring(start, at)
        val minimum = if (sign == '+') YEAR_PAD + 1 else YEAR_PAD
        val maximum = if (signed) MAX_YEAR_DIGITS else YEAR_PAD
        if (digits.length !in minimum..maximum) fail("year has the wrong number of digits")
        val magnitude = digits.toLong()
        if (magnitude > MAX_YEAR || (sign == '-' && magnitude == 0L)) fail("year out of range")
        return if (sign == '-') -magnitude.toInt() else magnitude.toInt()
    }

    /** Zero to nine ASCII digits after the decimal point, as nanoseconds. */
    private fun fraction(): Int {
        var nanosecond = 0
        var scale = NANOS_PER_SECOND
        while (scale > 1 && digitAt(at) >= 0) {
            scale /= DECIMAL
            nanosecond += digitAt(at) * scale
            at++
        }
        return nanosecond
    }

    /** Consumes [expected] when it is next, and says whether it was. */
    private fun skip(expected: Char): Boolean {
        if (text.getOrNull(at) != expected) return false
        at++
        return true
    }

    /** The value of the ASCII digit at [index], or -1 when there is none. */
    private fun digitAt(index: Int): Int {
        val c = text.getOrNull(index) ?: return -1
        return if (c in '0'..'9') c - '0' else -1
    }

    /** Throws the parse failure for this text. */
    private fun fail(reason: String): Nothing =
        throw IllegalArgumentException("Not ISO text ($reason at index $at): $text")
}

/** Appends [date]'s ISO calendar date text, as [IsoText.date] describes it. */
private fun StringBuilder.appendIsoDate(date: LocalDate) {
    val year = date.year
    when {
        year < 0 -> append('-').append((-year).toString().padStart(YEAR_PAD, '0'))
        year > MAX_FOUR_DIGIT_YEAR -> append('+').append(year)
        else -> append(year.toString().padStart(YEAR_PAD, '0'))
    }
    append('-')
    appendTwoDigits(date.month.number)
    append('-')
    appendTwoDigits(date.day)
}

/** Appends [value] (0–99) as two digits. */
private fun StringBuilder.appendTwoDigits(value: Int) {
    append(value.toString().padStart(2, '0'))
}

/** Appends `.` and [nanosecond] with trailing zeros removed, or nothing when it is zero. */
private fun StringBuilder.appendTrimmedFraction(nanosecond: Int) {
    if (nanosecond == 0) return
    append('.')
    append(nanosecond.toString().padStart(FRACTION_DIGITS, '0').trimEnd('0'))
}

/** The number of days in [month] of the proleptic Gregorian [year]. */
private fun daysInMonth(year: Int, month: Int): Int = when (month) {
    FEBRUARY -> if (isLeapYear(year)) LEAP_FEBRUARY_DAYS else FEBRUARY_DAYS
    APRIL, JUNE, SEPTEMBER, NOVEMBER -> SHORT_MONTH_DAYS
    else -> LONG_MONTH_DAYS
}

/** Whether the proleptic Gregorian [year] is a leap year; year 0 is one. */
private fun isLeapYear(year: Int): Boolean =
    year % LEAP_CYCLE == 0 && (year % CENTURY != 0 || year % GREGORIAN_CYCLE == 0)

/** Digits printed and required for a year inside 0..9999. */
private const val YEAR_PAD = 4

/** The widest year text `ISO_LOCAL_DATE` reads, sign excluded. */
private const val MAX_YEAR_DIGITS = 10

/** The largest year magnitude `java.time` holds. */
private const val MAX_YEAR = 999_999_999L

/** The last year printed without a sign. */
private const val MAX_FOUR_DIGIT_YEAR = 9999

/** Digits of a full nanosecond fraction. */
private const val FRACTION_DIGITS = 9

/** Base of the digits read. */
private const val DECIMAL = 10

/** Hours in a day; also the hour `HH:mm` reads as midnight when followed by `:00`. */
private const val HOURS_PER_DAY = 24

/** The largest minute of an hour. */
private const val MAX_MINUTE = 59

/** The largest second of a minute; `java.time` reads no leap second. */
private const val MAX_SECOND = 59

/** Months in a year. */
private const val MONTHS_PER_YEAR = 12

/** Seconds in a minute. */
private const val SECONDS_PER_MINUTE = 60

/** Seconds in an hour. */
private const val SECONDS_PER_HOUR = 3600

/** Seconds in a day. */
private const val SECONDS_PER_DAY = 86_400L

/** The widest offset `java.time` accepts, 18 hours, in seconds. */
private const val MAX_OFFSET_SECONDS = 18 * SECONDS_PER_HOUR

/** Nanoseconds in a second. */
private const val NANOS_PER_SECOND = 1_000_000_000

/** Nanoseconds in a millisecond. */
private const val NANOS_PER_MILLI = 1_000_000

/** Milliseconds in a second. */
private const val MILLIS_PER_SECOND = 1_000L

/** The largest epoch second whose milliseconds can start in a [Long]. */
private val MAX_MILLIS_SECONDS: Long = Long.MAX_VALUE.floorDiv(MILLIS_PER_SECOND)

/** The milliseconds past [MAX_MILLIS_SECONDS] that still fit. */
private val MAX_MILLIS_REMAINDER: Long = Long.MAX_VALUE.mod(MILLIS_PER_SECOND)

/** The smallest epoch second whose milliseconds can fit in a [Long]. */
private val MIN_MILLIS_SECONDS: Long = Long.MIN_VALUE.floorDiv(MILLIS_PER_SECOND)

/** The milliseconds past [MIN_MILLIS_SECONDS] needed to fit. */
private val MIN_MILLIS_REMAINDER: Long = Long.MIN_VALUE.mod(MILLIS_PER_SECOND)

/** February's month number. */
private const val FEBRUARY = 2

/** April's month number. */
private const val APRIL = 4

/** June's month number. */
private const val JUNE = 6

/** September's month number. */
private const val SEPTEMBER = 9

/** November's month number. */
private const val NOVEMBER = 11

/** Days in February outside a leap year. */
private const val FEBRUARY_DAYS = 28

/** Days in February in a leap year. */
private const val LEAP_FEBRUARY_DAYS = 29

/** Days in April, June, September and November. */
private const val SHORT_MONTH_DAYS = 30

/** Days in the other months. */
private const val LONG_MONTH_DAYS = 31

/** Years between leap years. */
private const val LEAP_CYCLE = 4

/** Years in a century, which is not a leap year unless … */
private const val CENTURY = 100

/** … it is a multiple of 400. */
private const val GREGORIAN_CYCLE = 400
