package com.coparently.app.domain.export

import com.coparently.app.domain.time.IsoText
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.number
import kotlinx.datetime.offsetAt
import kotlinx.datetime.toLocalDateTime
import kotlin.math.absoluteValue
import kotlin.time.Instant

/**
 * The times printed in the communication record (MON-3), in common code so the iPhone's export
 * prints the bytes the Android export prints.
 *
 * Each function reproduces exactly what `DateTimeFormatter.ofPattern(…, Locale.ROOT)` printed for
 * the record's fixed patterns, as documented for those pattern letters and confirmed by black-box
 * runs of `java.time`: a record a parent filed from one phone has to read the same as one filed from
 * the other, and a verifier hashing an export (MON-16) needs the text itself to be stable.
 */
object RecordTimeText {

    /**
     * [millis] as the wall clock of [zone] in the pattern `yyyy-MM-dd HH:mm:ss xxx`: the year of era
     * (see [wallClock]), the date and the time to the second, a space, and the zone's offset at that
     * instant as `xxx` prints it — always `+HH:MM` or `-HH:MM`, never `Z`, the offset's seconds
     * dropped, and an offset under a minute either way printed `+00:00`.
     */
    fun instant(millis: Long, zone: TimeZone): String {
        val instant = Instant.fromEpochMilliseconds(millis)
        val local = instant.toLocalDateTime(zone)
        return buildString {
            appendDateAndMinute(local)
            append(':')
            appendTwoDigits(local.second)
            append(' ')
            appendOffset(zone.offsetAt(instant).totalSeconds)
        }
    }

    /**
     * [value] in the pattern `yyyy-MM-dd HH:mm`. `yyyy` is the year of era, not the proleptic year:
     * year 0 prints as 0001 and year -5 as 0006, at least four digits, with a `+` once there are
     * more than four, as the pattern letter `y` documents.
     */
    fun wallClock(value: LocalDateTime): String = buildString { appendDateAndMinute(value) }

    /** [value] as an ISO date, the record's form of a day; the same text as [IsoText.date]. */
    fun date(value: LocalDate): String = IsoText.date(value)
}

/** Appends `yyyy-MM-dd HH:mm` for [value], the year as its year of era. */
private fun StringBuilder.appendDateAndMinute(value: LocalDateTime) {
    val year = value.year
    val yearOfEra = if (year >= 1) year.toLong() else 1L - year
    if (yearOfEra > MAX_UNSIGNED_YEAR) append('+')
    append(yearOfEra.toString().padStart(YEAR_DIGITS, '0'))
    append('-')
    appendTwoDigits(value.month.number)
    append('-')
    appendTwoDigits(value.day)
    append(' ')
    appendTwoDigits(value.hour)
    append(':')
    appendTwoDigits(value.minute)
}

/** Appends an offset of [totalSeconds] as the pattern `xxx` prints it. */
private fun StringBuilder.appendOffset(totalSeconds: Int) {
    val magnitude = totalSeconds.absoluteValue
    val hours = magnitude / SECONDS_PER_HOUR
    val minutes = magnitude / SECONDS_PER_MINUTE % MINUTES_PER_HOUR
    val negative = totalSeconds < 0 && (hours != 0 || minutes != 0)
    append(if (negative) '-' else '+')
    appendTwoDigits(hours)
    append(':')
    appendTwoDigits(minutes)
}

/** Appends [value] (0–99) as two digits. */
private fun StringBuilder.appendTwoDigits(value: Int) {
    append(value.toString().padStart(2, '0'))
}

/** The least number of digits `yyyy` prints. */
private const val YEAR_DIGITS = 4

/** The last year of era `yyyy` prints without a sign. */
private const val MAX_UNSIGNED_YEAR = 9999L

/** Seconds in an hour. */
private const val SECONDS_PER_HOUR = 3600

/** Seconds in a minute. */
private const val SECONDS_PER_MINUTE = 60

/** Minutes in an hour. */
private const val MINUTES_PER_HOUR = 60
