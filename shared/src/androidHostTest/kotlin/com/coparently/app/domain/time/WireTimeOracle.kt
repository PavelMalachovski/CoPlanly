package com.coparently.app.domain.time

import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * The `java.time` code the wire and record times were written and read with until MON-24's Task 6,
 * kept verbatim as the judge `WireTimeParityTest` holds the kotlinx-datetime code to.
 *
 * Each function is the body it replaced, with the file it came from named; none is called by
 * production code. Do not "fix" one: a difference between this and the new code is the finding.
 */
internal object WireTimeOracle {

    private val isoLocalDateTime: DateTimeFormatter = DateTimeFormatter.ISO_LOCAL_DATE_TIME

    /** `EventTimestamp.toWire` and `CustodyTimestamp.toWire` (identical bodies). */
    fun toWire(millis: Long): String =
        Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDateTime().format(isoLocalDateTime)

    /** `EventTimestamp.fromWire`. */
    fun eventFromWire(iso: String): Long =
        runCatching { LocalDateTime.parse(iso, isoLocalDateTime).toInstant(ZoneOffset.UTC).toEpochMilli() }
            .getOrElse { OffsetDateTime.parse(iso).toInstant().toEpochMilli() }

    /** `EventTimestamp.ofWallClock`. */
    fun ofWallClock(local: LocalDateTime, zone: ZoneId): Long = local.atZone(zone).toInstant().toEpochMilli()

    /** `EventTimestamp.toWallClock`. */
    fun toWallClock(millis: Long, zone: ZoneId): LocalDateTime =
        Instant.ofEpochMilli(millis).atZone(zone).toLocalDateTime()

    /** `CustodyTimestamp.fromWire`. */
    fun custodyFromWire(iso: String?): Long {
        val text = iso?.takeIf { it.isNotBlank() } ?: return 0L
        return runCatching {
            LocalDateTime.parse(text, isoLocalDateTime).toInstant(ZoneOffset.UTC).toEpochMilli()
        }.getOrDefault(0L)
    }

    /** `LocalDateTime.parse(text, ISO_LOCAL_DATE_TIME)`, as `EventDocument` and the readers above call it. */
    fun parseDateTime(text: String): LocalDateTime = LocalDateTime.parse(text, isoLocalDateTime)

    /** `LocalDateTime.format(ISO_LOCAL_DATE_TIME)`. */
    fun dateTime(value: LocalDateTime): String = value.format(isoLocalDateTime)

    private val hourMinute: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")

    /** `ContactWindowCodec.encode`'s time. */
    fun hourMinute(value: LocalTime): String = value.format(hourMinute)

    /** `ContactWindowCodec.decode`'s time. */
    fun parseHourMinute(text: String): LocalTime = LocalTime.parse(text, hourMinute)

    /** `SeasonalLayerCodec`'s and `ChildOverrideCodec`'s dates, and `CustodyHistory.layerName`'s. */
    fun date(value: LocalDate): String = value.toString()

    /** `SeasonalLayerCodec.decode`'s and `ChildOverrideCodec.decode`'s dates. */
    fun parseDate(text: String): LocalDate = LocalDate.parse(text)

    /** `CustodyHistory`'s history-layer id date. */
    fun basicDate(value: LocalDate): String = value.format(DateTimeFormatter.BASIC_ISO_DATE)

    private val recordInstant = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss xxx", Locale.ROOT)
    private val recordWallClock = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm", Locale.ROOT)

    /** `RecordFormat.instant`. */
    fun recordInstant(millis: Long, zone: ZoneId): String =
        Instant.ofEpochMilli(millis).atZone(zone).format(recordInstant)

    /** `RecordFormat.wallClock`. */
    fun recordWallClock(value: LocalDateTime?): String = value?.format(recordWallClock).orEmpty()

    /** `RecordFormat.date`. */
    fun recordDate(value: LocalDate?): String = value?.toString().orEmpty()
}
