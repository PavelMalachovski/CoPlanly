package com.coparently.app.domain.time

import com.coparently.app.domain.custody.CustodyTimestamp
import com.coparently.app.domain.events.EventTimestamp
import com.coparently.app.domain.export.RecordTimeText
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.UtcOffset
import kotlinx.datetime.asTimeZone
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/**
 * The exact text each writer of a date or a time puts on the wire or into the record.
 *
 * Every expected string was computed from the `java.time` code these writers replaced (MON-24,
 * Task 6) and is written here as a literal, so the iPhone is held to the bytes an Android phone and
 * an older build already write and read. `WireTimeParityTest` compares the two on random input; this
 * file is the part that also runs on iOS.
 *
 * The four instants are the brief's: zero seconds, non-zero seconds, milliseconds, and a day the
 * clocks change in Europe (29 March 2026).
 */
class WireTimeTextTest {

    @Test
    fun `an event's updatedAt always prints seconds and trims the fraction`() {
        assertEquals("2026-10-27T10:00:00", EventTimestamp.toWire(ZERO_SECONDS))
        assertEquals("2026-10-27T10:00:07", EventTimestamp.toWire(SECONDS))
        assertEquals("2026-10-27T10:00:07.123", EventTimestamp.toWire(MILLIS))
        assertEquals("2026-03-29T00:00:00", EventTimestamp.toWire(DST_DAY))
        assertEquals("2026-10-27T10:00:07.12", EventTimestamp.toWire(MILLIS - 3))
        assertEquals("2026-10-27T10:00:07.1", EventTimestamp.toWire(MILLIS - 23))
        assertEquals("1969-12-31T23:59:59.999", EventTimestamp.toWire(-1L))
    }

    @Test
    fun `a custody document's lastModifiedAt is the same text`() {
        assertEquals("2026-10-27T10:00:00", CustodyTimestamp.toWire(ZERO_SECONDS))
        assertEquals("2026-10-27T10:00:07", CustodyTimestamp.toWire(SECONDS))
        assertEquals("2026-10-27T10:00:07.123", CustodyTimestamp.toWire(MILLIS))
        assertEquals("2026-03-29T00:00:00", CustodyTimestamp.toWire(DST_DAY))
    }

    @Test
    fun `years outside four digits are written as ISO_LOCAL_DATE_TIME writes them`() {
        assertEquals("+10000-01-01T00:00:00", IsoText.dateTime(LocalDateTime(10_000, 1, 1, 0, 0)))
        assertEquals(
            "-0005-01-01T00:00:00.000005",
            IsoText.dateTime(LocalDateTime(-5, 1, 1, 0, 0, 0, 5_000))
        )
        assertEquals(
            "2026-10-27T10:00:07.123456789",
            IsoText.dateTime(LocalDateTime(2026, 10, 27, 10, 0, 7, 123_456_789))
        )
    }

    @Test
    fun `a toString-shaped date-time drops zero seconds and prints three - six or nine digits`() {
        assertEquals("2026-10-27T10:00", IsoText.dateTimeShortest(LocalDateTime(2026, 10, 27, 10, 0)))
        assertEquals("2026-10-27T10:00:07", IsoText.dateTimeShortest(LocalDateTime(2026, 10, 27, 10, 0, 7)))
        assertEquals(
            "2026-10-27T10:00:00.123",
            IsoText.dateTimeShortest(LocalDateTime(2026, 10, 27, 10, 0, 0, 123_000_000))
        )
        assertEquals(
            "2026-10-27T10:00:07.120",
            IsoText.dateTimeShortest(LocalDateTime(2026, 10, 27, 10, 0, 7, 120_000_000))
        )
        assertEquals(
            "2026-10-27T10:00:07.123400",
            IsoText.dateTimeShortest(LocalDateTime(2026, 10, 27, 10, 0, 7, 123_400_000))
        )
        assertEquals(
            "2026-10-27T10:00:07.000005",
            IsoText.dateTimeShortest(LocalDateTime(2026, 10, 27, 10, 0, 7, 5_000))
        )
        assertEquals(
            "2026-10-27T10:00:07.123456789",
            IsoText.dateTimeShortest(LocalDateTime(2026, 10, 27, 10, 0, 7, 123_456_789))
        )
        assertEquals(
            "2026-10-27T10:00:07.000000001",
            IsoText.dateTimeShortest(LocalDateTime(2026, 10, 27, 10, 0, 7, 1))
        )
        assertEquals("+10000-01-01T00:00", IsoText.dateTimeShortest(LocalDateTime(10_000, 1, 1, 0, 0)))
        assertEquals("-0005-01-01T23:59", IsoText.dateTimeShortest(LocalDateTime(-5, 1, 1, 23, 59)))
    }

    @Test
    fun `a contact window's time is hours and minutes - seconds dropped`() {
        assertEquals("10:00", IsoText.hourMinute(LocalTime(10, 0)))
        assertEquals("10:00", IsoText.hourMinute(LocalTime(10, 0, 7)))
        assertEquals("10:00", IsoText.hourMinute(LocalTime(10, 0, 7, 123_000_000)))
        assertEquals("00:00", IsoText.hourMinute(LocalTime(0, 0)))
        assertEquals("09:05", IsoText.hourMinute(LocalTime(9, 5)))
    }

    @Test
    fun `a seasonal layer's and a child override's dates are ISO dates`() {
        assertEquals("2026-10-27", IsoText.date(LocalDate(2026, 10, 27)))
        assertEquals("2026-03-29", IsoText.date(LocalDate(2026, 3, 29)))
        assertEquals("0000-01-01", IsoText.date(LocalDate(0, 1, 1)))
        assertEquals("+10000-01-01", IsoText.date(LocalDate(10_000, 1, 1)))
        assertEquals("-0001-01-01", IsoText.date(LocalDate(-1, 1, 1)))
    }

    @Test
    fun `a history layer's id date is the basic ISO date - four-digit years only`() {
        assertEquals("20261027", IsoText.basicDate(LocalDate(2026, 10, 27)))
        assertEquals("20260329", IsoText.basicDate(LocalDate(2026, 3, 29)))
        assertEquals("00000101", IsoText.basicDate(LocalDate(0, 1, 1)))
        // BASIC_ISO_DATE refuses to print these rather than widen the field.
        assertFailsWith<IllegalArgumentException> { IsoText.basicDate(LocalDate(10_000, 1, 1)) }
        assertFailsWith<IllegalArgumentException> { IsoText.basicDate(LocalDate(-1, 1, 1)) }
    }

    @Test
    fun `the record prints a wall clock to the minute`() {
        assertEquals("2026-10-27 10:00", RecordTimeText.wallClock(LocalDateTime(2026, 10, 27, 10, 0)))
        assertEquals("2026-10-27 10:00", RecordTimeText.wallClock(LocalDateTime(2026, 10, 27, 10, 0, 7)))
        assertEquals("2026-10-27 10:00", RecordTimeText.wallClock(LocalDateTime(2026, 10, 27, 10, 0, 7, 123_000_000)))
        assertEquals("2026-03-29 00:00", RecordTimeText.wallClock(LocalDateTime(2026, 3, 29, 0, 0)))
    }

    @Test
    fun `the record's year is the year of era - as the yyyy pattern prints it`() {
        assertEquals("+10000-01-01 00:00", RecordTimeText.wallClock(LocalDateTime(10_000, 1, 1, 0, 0)))
        assertEquals("0006-01-01 00:00", RecordTimeText.wallClock(LocalDateTime(-5, 1, 1, 0, 0)))
        assertEquals("0001-01-01 00:00", RecordTimeText.wallClock(LocalDateTime(0, 1, 1, 0, 0)))
    }

    @Test
    fun `the record prints an instant with its offset - and plus zero rather than Z`() {
        val utc = TimeZone.UTC
        assertEquals("2026-10-27 10:00:00 +00:00", RecordTimeText.instant(ZERO_SECONDS, utc))
        assertEquals("2026-10-27 10:00:07 +00:00", RecordTimeText.instant(SECONDS, utc))
        assertEquals("2026-10-27 10:00:07 +00:00", RecordTimeText.instant(MILLIS, utc))
        assertEquals("2026-03-29 00:00:00 +00:00", RecordTimeText.instant(DST_DAY, utc))

        val prague = TimeZone.of("Europe/Prague")
        assertEquals("2026-10-27 11:00:00 +01:00", RecordTimeText.instant(ZERO_SECONDS, prague))
        assertEquals("2026-10-27 11:00:07 +01:00", RecordTimeText.instant(MILLIS, prague))
        assertEquals("2026-03-29 01:00:00 +01:00", RecordTimeText.instant(DST_DAY, prague))
        assertEquals("2026-03-29 03:30:00 +02:00", RecordTimeText.instant(AFTER_SPRING_GAP, prague))

        assertEquals(
            "2026-10-28 00:00:00 +14:00",
            RecordTimeText.instant(ZERO_SECONDS, TimeZone.of("Pacific/Kiritimati"))
        )
        assertEquals(
            "2026-10-26 23:00:00 -11:00",
            RecordTimeText.instant(ZERO_SECONDS, TimeZone.of("Pacific/Pago_Pago"))
        )
        assertEquals("2026-10-27 15:30:00 +05:30", RecordTimeText.instant(ZERO_SECONDS, TimeZone.of("Asia/Kolkata")))
        assertEquals(
            "2026-10-27 07:30:00 -02:30",
            RecordTimeText.instant(ZERO_SECONDS, TimeZone.of("America/St_Johns"))
        )
    }

    @Test
    fun `the record's offset drops seconds - and a sub-minute offset prints as plus zero`() {
        // Only a local mean time before 1900 has such offsets, and `xxx` printed them this way.
        assertEquals(
            "2026-10-27 09:59:30 +00:00",
            RecordTimeText.instant(ZERO_SECONDS, UtcOffset(seconds = -30).asTimeZone())
        )
        assertEquals(
            "2026-10-27 10:00:30 +00:00",
            RecordTimeText.instant(ZERO_SECONDS, UtcOffset(seconds = 30).asTimeZone())
        )
        assertEquals(
            "2026-10-27 08:59:30 -01:00",
            RecordTimeText.instant(ZERO_SECONDS, UtcOffset(seconds = -3_630).asTimeZone())
        )
        assertEquals(
            "2026-10-27 10:57:44 +00:57",
            RecordTimeText.instant(ZERO_SECONDS, UtcOffset(seconds = 3_464).asTimeZone())
        )
    }

    @Test
    fun `the record prints a date as an ISO date`() {
        assertEquals("2026-10-27", RecordTimeText.date(LocalDate(2026, 10, 27)))
        assertEquals("2026-03-29", RecordTimeText.date(LocalDate(2026, 3, 29)))
    }

    private companion object {
        /** 2026-10-27T10:00Z. */
        const val ZERO_SECONDS = 1_793_095_200_000L

        /** 2026-10-27T10:00:07Z. */
        const val SECONDS = 1_793_095_207_000L

        /** 2026-10-27T10:00:07.123Z. */
        const val MILLIS = 1_793_095_207_123L

        /** 2026-03-29T00:00Z, the day Europe moves its clocks forward. */
        const val DST_DAY = 1_774_742_400_000L

        /** 2026-03-29T01:30Z: 03:30 in Prague, just past the spring gap. */
        const val AFTER_SPRING_GAP = 1_774_747_800_000L
    }
}
