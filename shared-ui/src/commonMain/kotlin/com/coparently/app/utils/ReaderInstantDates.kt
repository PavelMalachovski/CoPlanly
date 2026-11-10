package com.coparently.app.utils

import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Instant

/*
 * An instant's day in the reader's styles (MON-24 phase 2, slice 11g), over the date seam's
 * [readerLongDate] and [readerMediumDate]. A file of its own, not `ReaderDateText.kt`: a top-level
 * function there would make a `ReaderDateTextKt` class in common code beside the Android actuals' of
 * the same name, which the JVM refuses.
 */

/**
 * The day the instant [millis] (epoch milliseconds) falls on in the device's zone, in the language's
 * long style: when a guest's or a grant's access ends, as the screens wrote it with
 * `ofLocalizedDate(FormatStyle.LONG)` over the instant in `ZoneId.systemDefault()` (slice 11g).
 */
fun readerLongDateAt(millis: Long): String =
    readerLongDate(Instant.fromEpochMilliseconds(millis).toLocalDateTime(TimeZone.currentSystemDefault()).date)

/**
 * The day the instant [millis] falls on in the device's zone, in the language's medium style: when a
 * family friend's or a professional's access ends, as `ofLocalizedDate(FormatStyle.MEDIUM)` over the
 * instant's day in `ZoneId.systemDefault()` wrote it (slice 11g).
 */
fun readerMediumDateAt(millis: Long): String =
    readerMediumDate(Instant.fromEpochMilliseconds(millis).toLocalDateTime(TimeZone.currentSystemDefault()).date)
