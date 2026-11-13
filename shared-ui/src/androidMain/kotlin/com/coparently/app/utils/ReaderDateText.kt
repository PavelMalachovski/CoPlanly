package com.coparently.app.utils

import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.YearMonth
import kotlinx.datetime.toJavaLocalDate
import kotlinx.datetime.toJavaLocalDateTime
import kotlinx.datetime.toJavaYearMonth
import kotlinx.datetime.toJavaZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

/**
 * `LocalizedDates.kt`'s `java.time` code, unchanged, in `Locale.getDefault()` — the language the
 * app speaks (`AppLocale.follow`), which is what each of those functions defaulted to.
 */
actual object ReaderDateText {

    actual fun date(date: LocalDate, skeleton: String): String = date.format(localizedDate(skeleton))

    actual fun month(month: YearMonth, skeleton: String): String =
        month.toJavaYearMonth().atDay(1).format(localizedDate(skeleton))

    actual fun pattern(date: LocalDate, pattern: String): String =
        date.toJavaLocalDate().format(DateTimeFormatter.ofPattern(pattern, Locale.getDefault()))

    actual fun pattern(month: YearMonth, pattern: String): String =
        month.toJavaYearMonth().format(DateTimeFormatter.ofPattern(pattern, Locale.getDefault()))

    actual fun time(time: LocalTime, is24Hour: Boolean): String = time.format(shortTime(is24Hour = is24Hour))

    actual fun time(dateTime: LocalDateTime, is24Hour: Boolean): String =
        dateTime.toJavaLocalDateTime().format(shortTime(is24Hour = is24Hour))

    actual fun dateWithTime(
        dateTime: LocalDateTime,
        skeleton: String,
        separator: String,
        is24Hour: Boolean
    ): String = date(dateTime.date, skeleton) + separator + time(dateTime, is24Hour)

    actual fun hour(hour: Int, is24Hour: Boolean): String = hourLabel(hour, is24Hour = is24Hour)

    actual fun answerBy(millis: Long, zone: TimeZone, is24Hour: Boolean): String =
        answerByText(millis, zone.toJavaZoneId(), is24Hour = is24Hour)

    actual fun isoDate(iso: String?, skeleton: String): String = isoDateText(iso, skeleton)
}

/** `FormatStyle.MEDIUM` in `Locale.getDefault()`, the chat's day separator as it was formatted. */
actual fun readerMediumDate(date: LocalDate): String =
    date.toJavaLocalDate().format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM))

/** `FormatStyle.SHORT` in `Locale.getDefault()`, as the seasonal layer's suggestions were formatted. */
actual fun readerShortDate(date: LocalDate): String =
    date.toJavaLocalDate().format(DateTimeFormatter.ofLocalizedDate(FormatStyle.SHORT))

/**
 * `FormatStyle.LONG` in `Locale.getDefault()`, as a guest's or a grant's end was formatted (slice
 * 11g): the screens formatted the instant's date in the device's zone, which is this date.
 */
actual fun readerLongDate(date: LocalDate): String =
    date.toJavaLocalDate().format(DateTimeFormatter.ofLocalizedDate(FormatStyle.LONG))

/** `FormatStyle.FULL` in `Locale.getDefault()`, as the custody schedule's start date was formatted. */
actual fun readerFullDate(date: LocalDate): String =
    date.toJavaLocalDate().format(DateTimeFormatter.ofLocalizedDate(FormatStyle.FULL))
