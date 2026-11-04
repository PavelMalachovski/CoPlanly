package com.coparently.app.domain.time

import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.plus
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock
import kotlin.time.Duration

/*
 * Wall-clock arithmetic on a LocalDateTime (MON-24).
 *
 * The event code was written on java.time's `LocalDateTime.plusDays`, `plusMinutes`, `plus(Duration)`
 * and `Duration.between`, which work on the date and the time of day alone: there is no time zone,
 * so a day is always 24 hours and no daylight-saving change is skipped or repeated. A weekly event at
 * 02:30 keeps 02:30 across the spring gap, and a reminder 30 minutes before it is due at 02:00 on the
 * same wall clock. kotlinx-datetime leaves this arithmetic out on purpose, because a LocalDateTime has
 * no zone to add a duration in; these functions put back exactly the java.time behaviour, so the
 * calendar, the reminders and the recurrence expansion do not change.
 *
 * Inside, UTC serves as a zone with no transitions: in it, adding to the instant is adding to the
 * wall clock. Date-based units move the date and keep the time of day, which is what java.time's
 * `plusDays`/`plusWeeks`/`plusMonths` do, month-end clamping included.
 */

/**
 * This wall-clock time moved by [value] [unit]s: `plusDays`, `plusWeeks`, `plusMonths`, `plusHours`,
 * `plusMinutes` and the rest of `java.time.LocalDateTime`'s `plusX` family, in one function.
 */
fun LocalDateTime.plus(value: Long, unit: DateTimeUnit): LocalDateTime = when (unit) {
    is DateTimeUnit.DateBased -> LocalDateTime(date.plus(value, unit), time)
    is DateTimeUnit.TimeBased -> toInstant(TimeZone.UTC).plus(value, unit).toLocalDateTime(TimeZone.UTC)
}

/** [plus] for an [Int] amount. */
fun LocalDateTime.plus(value: Int, unit: DateTimeUnit): LocalDateTime = plus(value.toLong(), unit)

/** This wall-clock time moved back by [value] [unit]s: `java.time.LocalDateTime`'s `minusX` family. */
fun LocalDateTime.minus(value: Long, unit: DateTimeUnit): LocalDateTime = plus(-value, unit)

/** [minus] for an [Int] amount. */
fun LocalDateTime.minus(value: Int, unit: DateTimeUnit): LocalDateTime = plus(-value.toLong(), unit)

/** This wall-clock time moved by [duration], as `java.time.LocalDateTime.plus(Duration)` moves it. */
operator fun LocalDateTime.plus(duration: Duration): LocalDateTime =
    (toInstant(TimeZone.UTC) + duration).toLocalDateTime(TimeZone.UTC)

/** This wall-clock time moved back by [duration], as `java.time.LocalDateTime.minus(Duration)` does. */
operator fun LocalDateTime.minus(duration: Duration): LocalDateTime =
    (toInstant(TimeZone.UTC) - duration).toLocalDateTime(TimeZone.UTC)

/**
 * The wall-clock time from this to [other], as `java.time.Duration.between(this, other)` gives it:
 * negative when [other] is earlier.
 */
fun LocalDateTime.durationUntil(other: LocalDateTime): Duration =
    other.toInstant(TimeZone.UTC) - toInstant(TimeZone.UTC)

/**
 * The wall-clock time now in [zone], by default the device's current zone: what
 * `java.time.LocalDateTime.now()` reads. One function, so every record stamped "now" reads the clock
 * the same way.
 */
fun wallClockNow(zone: TimeZone = TimeZone.currentSystemDefault()): LocalDateTime =
    Clock.System.now().toLocalDateTime(zone)

/**
 * The last moment of a day, 23:59:59.999999999: `java.time.LocalTime.MAX`, which ends the day in a
 * query that runs to the end of a date.
 */
val LAST_INSTANT_OF_DAY: LocalTime = LocalTime(23, 59, 59, 999_999_999)
