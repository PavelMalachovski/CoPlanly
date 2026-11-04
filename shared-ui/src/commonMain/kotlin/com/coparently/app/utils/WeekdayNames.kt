package com.coparently.app.utils

import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.isoDayNumber
import kotlinx.datetime.plus

/**
 * This day's name in the reader's language, in a fixed [pattern] of day-of-week letters: "EEE" the
 * short name ("Wed", "st"), "EEEE" the full one, "EEEEE" the narrow one ("W").
 *
 * On Android this is `java.time`'s `DayOfWeek.getDisplayName(TextStyle.SHORT, FULL or NARROW, …)`,
 * which the custody screens called before they moved (MON-24 phase 2, slice 11d): both format the
 * day of the week through `DateTimeFormatterBuilder.appendText` with the same text style, so the
 * name is the same. The pattern formats a date that falls on this day.
 */
fun DayOfWeek.readerName(pattern: String): String =
    ReaderDateText.pattern(REFERENCE_MONDAY.plus(isoDayNumber - 1, DateTimeUnit.DAY), pattern)

/** Monday 1 January 2024: any Monday will do, since only the day of the week is formatted. */
private val REFERENCE_MONDAY = LocalDate(2024, 1, 1)
