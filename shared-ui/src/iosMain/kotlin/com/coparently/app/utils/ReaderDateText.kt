package com.coparently.app.utils

import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.YearMonth
import kotlinx.datetime.toLocalDateTime
import kotlinx.datetime.toNSDateComponents
import platform.Foundation.NSCalendar
import platform.Foundation.NSCalendarIdentifierGregorian
import platform.Foundation.NSDate
import platform.Foundation.NSDateFormatter
import platform.Foundation.NSDateFormatterFullStyle
import platform.Foundation.NSDateFormatterLongStyle
import platform.Foundation.NSDateFormatterMediumStyle
import platform.Foundation.NSDateFormatterNoStyle
import platform.Foundation.NSDateFormatterShortStyle
import platform.Foundation.NSDateFormatterStyle
import platform.Foundation.NSTimeZone
import platform.Foundation.timeZoneWithName
import kotlin.time.Instant

/**
 * Foundation's wording in [readerLocale] (the owner's language rule), on the Gregorian calendar
 * `java.time`'s ISO dates use on Android (as `rememberReaderDates` does): a skeleton is a date
 * format template, which Foundation orders, punctuates and declines for the language itself. Dates
 * and times here are zone-less wall clocks, so every formatter and the calendar run in UTC: the
 * phone's zone, and a daylight-saving gap in it, never shifts what was written.
 */
actual object ReaderDateText {

    actual fun date(date: LocalDate, skeleton: String): String {
        val instant = nsDate(LocalDateTime(date, LocalTime(0, 0))) ?: return date.toString()
        return formatter(skeleton).stringFromDate(instant)
    }

    actual fun month(month: YearMonth, skeleton: String): String =
        date(LocalDate(month.year, month.month, 1), skeleton)

    actual fun pattern(date: LocalDate, pattern: String): String {
        val instant = nsDate(LocalDateTime(date, LocalTime(0, 0))) ?: return date.toString()
        return fixedPattern(pattern).stringFromDate(instant)
    }

    actual fun pattern(month: YearMonth, pattern: String): String =
        this.pattern(LocalDate(month.year, month.month, 1), pattern)

    actual fun time(time: LocalTime, is24Hour: Boolean): String =
        time(LocalDateTime(REFERENCE_DATE, time), is24Hour)

    actual fun time(dateTime: LocalDateTime, is24Hour: Boolean): String {
        val instant = nsDate(dateTime) ?: return dateTime.time.toString()
        return fixedPattern(if (is24Hour) PATTERN_24_HOUR else PATTERN_12_HOUR).stringFromDate(instant)
    }

    actual fun dateWithTime(
        dateTime: LocalDateTime,
        skeleton: String,
        separator: String,
        is24Hour: Boolean
    ): String = date(dateTime.date, skeleton) + separator + time(dateTime, is24Hour)

    actual fun hour(hour: Int, is24Hour: Boolean): String =
        if (is24Hour) {
            hour.toString().padStart(2, '0')
        } else {
            val at = nsDate(LocalDateTime(REFERENCE_DATE, LocalTime(hour, 0))) ?: return hour.toString()
            fixedPattern(PATTERN_12_HOUR_GUTTER).stringFromDate(at)
        }

    actual fun answerBy(millis: Long, zone: TimeZone, is24Hour: Boolean): String {
        val moment = Instant.fromEpochMilliseconds(millis).toLocalDateTime(zone)
        return date(moment.date, ANSWER_BY_DAY) + ", " + time(moment, is24Hour)
    }

    actual fun isoDate(iso: String?, skeleton: String): String {
        val text = iso.orEmpty()
        val parsed = runCatching { LocalDate.parse(text) }.getOrNull() ?: return text
        return date(parsed, skeleton)
    }

    /** Any day: a time's text does not depend on it. */
    private val REFERENCE_DATE = LocalDate(2026, 1, 1)
    private const val PATTERN_24_HOUR = "HH:mm"
    private const val PATTERN_12_HOUR = "h:mm a"
    private const val PATTERN_12_HOUR_GUTTER = "h a"
    private const val ANSWER_BY_DAY = "EEEMMMd"
}

/** Foundation's medium date style in the phone's locale, on the Gregorian calendar. */
actual fun readerMediumDate(date: LocalDate): String = styledDate(date, NSDateFormatterMediumStyle)

/** Foundation's short date style in the phone's locale, on the Gregorian calendar. */
actual fun readerShortDate(date: LocalDate): String = styledDate(date, NSDateFormatterShortStyle)

/** Foundation's long date style in the phone's locale, on the Gregorian calendar. */
actual fun readerLongDate(date: LocalDate): String = styledDate(date, NSDateFormatterLongStyle)

/** Foundation's full date style in the phone's locale, on the Gregorian calendar. */
actual fun readerFullDate(date: LocalDate): String = styledDate(date, NSDateFormatterFullStyle)

/** [date] in one of Foundation's date [style]s, with no time. */
private fun styledDate(date: LocalDate, style: NSDateFormatterStyle): String {
    val instant = nsDate(LocalDateTime(date, LocalTime(0, 0))) ?: return date.toString()
    val formatter = NSDateFormatter().apply {
        locale = readerLocale()
        calendar = gregorian
        timeZone = utc
        dateStyle = style
        timeStyle = NSDateFormatterNoStyle
    }
    return formatter.stringFromDate(instant)
}

/** The calendar `java.time`'s ISO dates are on. */
private val gregorian = NSCalendar(calendarIdentifier = NSCalendarIdentifierGregorian).apply { timeZone = utc }

/** Wall clocks carry no zone, so they are read and written in UTC. */
private val utc = NSTimeZone.timeZoneWithName("UTC")!!

/** [dateTime] as the moment it names in the phone's zone, on the Gregorian calendar. */
private fun nsDate(dateTime: LocalDateTime): NSDate? = gregorian.dateFromComponents(dateTime.toNSDateComponents())

/** A formatter for [skeleton] in the reader's locale: Foundation's own order for the fields. */
private fun formatter(skeleton: String): NSDateFormatter = NSDateFormatter().apply {
    locale = readerLocale()
    calendar = gregorian
    setLocalizedDateFormatFromTemplate(skeleton)
}

/** A formatter for a fixed [pattern], with the reader's month, weekday and day-period words. */
private fun fixedPattern(pattern: String): NSDateFormatter = NSDateFormatter().apply {
    locale = readerLocale()
    calendar = gregorian
    dateFormat = pattern
}

/**
 * Whether the phone's locale writes its hours 0–23: its preferred hour pattern (Foundation's `j`
 * template) is `H` or `k`.
 */
internal actual fun languageUses24Hour(): Boolean {
    val pattern = NSDateFormatter.dateFormatFromTemplate("j", 0u, readerLocale()) ?: return true
    return pattern.contains('H') || pattern.contains('k')
}
