package com.coparently.app.utils

import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.YearMonth

/**
 * Dates and times as the reader writes them, for the shared screens (MON-24 phase 2, Task 11):
 * the app's one set of rules (docs/AUDIT-2026-10-design.md D-18, release audit R-9) over
 * kotlinx-datetime values.
 *
 * On Android each function is the `java.time` code the screens called before they moved
 * (`LocalizedDates.kt`: `localizedDate`, `shortTime`, `dateWithTime`, `hourLabel`, `answerByText`,
 * `isoDateText`), in the language the app speaks, so the text is byte for byte what it was. On the
 * iPhone it is Foundation's, in the phone's current locale, as `rememberReaderDates` is.
 *
 * A skeleton names the fields ("EEEd", "MMMMEEEEd", "yMMMM"); the platform orders and punctuates
 * them for the language, and a month inside a date takes its declined form ([monthInFormatForm]).
 * Time is never part of a skeleton: [time] writes it, on the reader's clock ([ClockFormat]).
 */
expect object ReaderDateText {

    /** [date] for [skeleton]: "Wed 23" for "EEEd" in English, "St 23." in Czech. */
    fun date(date: LocalDate, skeleton: String): String

    /**
     * [month] for [skeleton], which names no day: "October 2026" for "yMMMM", "říjen 2026" (the
     * month's first day formatted, as the mini month and the month review wrote it).
     */
    fun month(month: YearMonth, skeleton: String): String

    /**
     * [date] in a fixed [pattern] (Unicode date field letters, the same on both platforms), for the
     * few places that name their own fields rather than a skeleton: "EEE" over a week's columns,
     * "d MMMM" in the support schedule.
     */
    fun pattern(date: LocalDate, pattern: String): String

    /**
     * [month] in a fixed [pattern]: the month's own form "LLLL yyyy" ("říjen 2026", stand-alone,
     * not declined) is how the money screens title a month.
     */
    fun pattern(month: YearMonth, pattern: String): String

    /** [time] on the reader's clock: "15:30" or "3:30 PM". */
    fun time(time: LocalTime, is24Hour: Boolean = ClockFormat.is24Hour): String

    /** The time of [dateTime] on the reader's clock, as [time] writes it. */
    fun time(dateTime: LocalDateTime, is24Hour: Boolean = ClockFormat.is24Hour): String

    /**
     * [dateTime]'s date for [skeleton], then [separator], then its time on the reader's clock:
     * "Wed, Sep 2 · 3:30 PM", "st 2. 9. · 15:30".
     */
    fun dateWithTime(
        dateTime: LocalDateTime,
        skeleton: String,
        separator: String = " · ",
        is24Hour: Boolean = ClockFormat.is24Hour
    ): String

    /**
     * The label of one hour row in the Day and Week views' gutter (L-7): the bare two-digit hour on
     * a 24-hour clock ("09"), the hour with its day-period marker on a 12-hour one ("9 AM").
     *
     * @param hour The hour of the day, 0–23
     */
    fun hour(hour: Int, is24Hour: Boolean = ClockFormat.is24Hour): String

    /**
     * A request's deadline as its reader says it (phase 3a): weekday, day and month, then the time
     * on the reader's clock — "Tue, Oct 13, 10:00".
     *
     * @param millis The deadline, epoch milliseconds
     * @param zone The zone the reader's day is counted in; the device's by default
     */
    fun answerBy(
        millis: Long,
        zone: TimeZone = TimeZone.currentSystemDefault(),
        is24Hour: Boolean = ClockFormat.is24Hour
    ): String

    /**
     * An ISO date (`2026-10-03`, the form dates travel in) for [skeleton], or [iso] unchanged when
     * it is not a date — a payload from another build must still say something.
     */
    fun isoDate(iso: String?, skeleton: String): String
}

/**
 * [date] in the language's own medium style ("12 Aug 2026", "12. 8. 2026"), the form a chat
 * thread's day separators take: a style, not a skeleton, so the platform's medium date as it is.
 * Beside [ReaderDateText] rather than in it, which holds the skeleton and pattern rules.
 */
expect fun readerMediumDate(date: LocalDate): String

/** [date] in the language's own short style ("8/12/26", "12.08.26"), as [readerMediumDate] is the medium. */
expect fun readerShortDate(date: LocalDate): String

/** [date] in the language's own long style ("August 12, 2026"), as [readerMediumDate] is the medium. */
expect fun readerLongDate(date: LocalDate): String

/** [date] in the language's own full style ("Wednesday, August 12, 2026"), as [readerMediumDate] is the medium. */
expect fun readerFullDate(date: LocalDate): String
