/*
 * Adapted from google-http-java-client 1.42.3
 * (https://github.com/googleapis/google-http-java-client),
 * google-http-client/src/main/java/com/google/api/client/util/DateTime.java.
 * Copyright (c) 2010 Google Inc.
 *
 * Licensed under the Apache License, Version 2.0; the licence is in
 * third_party/google-http-client-java/LICENSE.
 *
 * Modified for CoPlanly (MON-24): `parseRfc3339`'s pattern and rules rewritten in Kotlin over
 * kotlinx-datetime, without `java.util.Calendar`, for both the JVM and iOS.
 */
package com.coparently.app.data.remote.google

import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.plus

/**
 * RFC 3339 text read as google-http-client 1.42.3's `DateTime.parseRfc3339` read it for the
 * Calendar import before MON-24, so an event lands where it landed (`GoogleDateTimeTest`).
 *
 * The same pattern for both of an event's times: `yyyy-MM-dd`, then optionally `T` (or `t`)
 * `HH:mm:ss` with up to nine fraction digits, then optionally `Z` (or `z`) or `±HH:mm`. A date with
 * no time is midnight UTC; a time with no offset is UTC; an offset with no time is refused. Fields
 * out of range roll over (`GregorianCalendar` was lenient: the 30th of February is the 2nd of
 * March), and a fraction is cut to the millisecond. Text that does not match is an
 * [IllegalArgumentException] (google-http-client's `NumberFormatException`), which failed the
 * whole page, and still does. Dates before the Gregorian calendar's start (1582) are counted in
 * it, where Java's calendar switched to the Julian one; no calendar sends them.
 */
internal object GoogleDateTime {
    private val PATTERN = Regex(
        "([0-9]{4})-([0-9]{2})-([0-9]{2})" +
            "([Tt]([0-9]{2}):([0-9]{2}):([0-9]{2})(\\.[0-9]{1,9})?)?" +
            "([Zz]|([+-])([0-9]{2}):([0-9]{2}))?"
    )

    // The pattern's groups, numbered as in google-http-client's regex.
    private const val YEAR = 1
    private const val MONTH = 2
    private const val DAY = 3
    private const val TIME = 4
    private const val HOUR = 5
    private const val MINUTE = 6
    private const val SECOND = 7
    private const val FRACTION = 8
    private const val ZONE = 9
    private const val SHIFT_SIGN = 10
    private const val SHIFT_HOURS = 11
    private const val SHIFT_MINUTES = 12

    private const val MONTHS_PER_YEAR = 12
    private const val SECONDS_PER_DAY = 86_400L
    private const val SECONDS_PER_HOUR = 3_600L
    private const val SECONDS_PER_MINUTE = 60L
    private const val MILLIS_PER_SECOND = 1_000L
    private const val FRACTION_DIGITS = 9
    private const val NANOS_PER_MILLI = 1_000_000

    /** [text] as milliseconds since the epoch, as `DateTime.getValue()` gave it. */
    fun epochMillis(text: String): Long {
        val match = PATTERN.matchEntire(text) ?: throw IllegalArgumentException("Invalid date/time format: $text")
        val group = { i: Int -> match.groups[i]?.value }
        val timeGiven = group(TIME) != null
        val zone = group(ZONE)
        require(zone == null || timeGiven) {
            "Invalid date/time format, cannot specify time zone shift without specifying time: $text"
        }
        val number = { i: Int -> group(i)?.toInt() ?: 0 }
        val month = number(MONTH) - 1
        val firstOfMonth = LocalDate(number(YEAR) + month.floorDiv(MONTHS_PER_YEAR), month.mod(MONTHS_PER_YEAR) + 1, 1)
        val days = firstOfMonth.plus(number(DAY) - 1, DateTimeUnit.DAY).toEpochDays()
        var seconds = days * SECONDS_PER_DAY + number(HOUR) * SECONDS_PER_HOUR +
            number(MINUTE) * SECONDS_PER_MINUTE + number(SECOND)
        if (zone != null && zone.uppercase() != "Z") {
            val shiftMinutes = number(SHIFT_HOURS) * SECONDS_PER_MINUTE + number(SHIFT_MINUTES)
            seconds -= (if (group(SHIFT_SIGN) == "-") -shiftMinutes else shiftMinutes) * SECONDS_PER_MINUTE
        }
        val nanos = group(FRACTION)?.substring(1)?.padEnd(FRACTION_DIGITS, '0')?.toInt() ?: 0
        return seconds * MILLIS_PER_SECOND + nanos / NANOS_PER_MILLI
    }
}
