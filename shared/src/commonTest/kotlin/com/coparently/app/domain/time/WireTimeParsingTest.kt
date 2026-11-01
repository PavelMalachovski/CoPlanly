package com.coparently.app.domain.time

import com.coparently.app.domain.custody.CustodyTimestamp
import com.coparently.app.domain.events.EventTimestamp
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/**
 * What each reader of a wire time makes of the text it is handed: the same value the `java.time`
 * reader it replaced returned, or a failure where that reader threw.
 *
 * Every caller maps a failure the same way whatever its type — the events, child and pet readers
 * skip the document (`runCatching` per document), [CustodyTimestamp] answers `UNDATED`, and the
 * custody codecs drop or keep the entry as unreadable — so a failure is asserted as the common
 * `IllegalArgumentException` (`DateTimeParseException` before) or, for a value too far from 1970 to
 * be milliseconds, the `ArithmeticException` `Instant.toEpochMilli` threw. The expected values were
 * computed from the old readers and are literals, so iOS is held to them too.
 */
class WireTimeParsingTest {

    @Test
    fun `the brief's inputs, as an event's updatedAt`() {
        assertEquals(ZERO_SECONDS, EventTimestamp.fromWire("2026-10-27T10:00"))
        assertEquals(ZERO_SECONDS, EventTimestamp.fromWire("2026-10-27T10:00:00.000"))
        // An offset is honoured: 10:00 at +02:00 is 08:00Z.
        assertEquals(ZERO_SECONDS - TWO_HOURS, EventTimestamp.fromWire("2026-10-27T10:00:00+02:00"))
        assertEquals(ZERO_SECONDS, EventTimestamp.fromWire("2026-10-27T10:00:00Z"))
        assertFailsWith<IllegalArgumentException> { EventTimestamp.fromWire("") }
        assertFailsWith<IllegalArgumentException> { EventTimestamp.fromWire("27.10.2026") }
    }

    @Test
    fun `the brief's inputs, as a custody document's lastModifiedAt`() {
        assertEquals(ZERO_SECONDS, CustodyTimestamp.fromWire("2026-10-27T10:00"))
        assertEquals(ZERO_SECONDS, CustodyTimestamp.fromWire("2026-10-27T10:00:00.000"))
        // The custody reader never took an offset: an offset is undated, not shifted.
        assertEquals(CustodyTimestamp.UNDATED, CustodyTimestamp.fromWire("2026-10-27T10:00:00+02:00"))
        assertEquals(CustodyTimestamp.UNDATED, CustodyTimestamp.fromWire("2026-10-27T10:00:00Z"))
        assertEquals(CustodyTimestamp.UNDATED, CustodyTimestamp.fromWire(""))
        assertEquals(CustodyTimestamp.UNDATED, CustodyTimestamp.fromWire("27.10.2026"))
        assertEquals(CustodyTimestamp.UNDATED, CustodyTimestamp.fromWire(null))
    }

    @Test
    fun `ISO_LOCAL_DATE_TIME's own leniencies are kept`() {
        // A lower-case T, and a decimal point with no digits after it, both parse.
        assertEquals(ZERO_SECONDS, EventTimestamp.fromWire("2026-10-27t10:00:00"))
        assertEquals(ZERO_SECONDS, EventTimestamp.fromWire("2026-10-27T10:00:00."))
        assertEquals(ZERO_SECONDS + 500, EventTimestamp.fromWire("2026-10-27T10:00:00.5"))
        // Nanoseconds are truncated to the millisecond, never rounded.
        assertEquals(ZERO_SECONDS + 123, EventTimestamp.fromWire("2026-10-27T10:00:00.123456789"))
        // A five-digit year needs its sign; a negative year is allowed.
        assertEquals(317_362_615_200_000L, EventTimestamp.fromWire("+12026-10-27T10:00"))
        assertEquals(-62_172_885_600_000L, EventTimestamp.fromWire("-0001-10-27T10:00"))
    }

    @Test
    fun `ISO_LOCAL_DATE_TIME's refusals are kept`() {
        listOf(
            "2026-10-27T24:00:00",
            "2026-10-27T23:59:60",
            "2026-10-27T10:60",
            "2026-02-29T10:00:00",
            "2026-10-27T10:00:00.1234567890",
            "2026-10-27 10:00:00",
            "2026-10-27T10:00:",
            "2026-10-27T10",
            "+2026-10-27T10:00",
            "12026-10-27T10:00",
            "-0000-10-27T10:00",
            "+1000000000-10-27T10:00",
            "2026-10-27T10:00:00+0200",
            "2026-10-27T10:00:00+18:01",
            "2026-10-27T10:00:00 Z",
            "2026-10-27T10:00:00+02:00[Europe/Prague]"
        ).forEach { text ->
            assertFailsWith<IllegalArgumentException>(text) { EventTimestamp.fromWire(text) }
            assertEquals(CustodyTimestamp.UNDATED, CustodyTimestamp.fromWire(text), text)
        }
    }

    @Test
    fun `the offset reader's leniencies are kept`() {
        assertEquals(ZERO_SECONDS, EventTimestamp.fromWire("2026-10-27T10:00:00z"))
        assertEquals(ZERO_SECONDS, EventTimestamp.fromWire("2026-10-27T10:00:00-00:00"))
        assertEquals(ZERO_SECONDS, EventTimestamp.fromWire("2026-10-27T10:00:00.Z"))
        assertEquals(ZERO_SECONDS - TWO_HOURS, EventTimestamp.fromWire("2026-10-27T10:00:00+02"))
        assertEquals(1_793_087_970_000L, EventTimestamp.fromWire("2026-10-27T10:00:00+02:00:30"))
        assertEquals(1_793_030_400_000L, EventTimestamp.fromWire("2026-10-27T10:00:00+18:00"))
        assertEquals(1_793_160_000_000L, EventTimestamp.fromWire("2026-10-27T10:00:00-18:00"))
    }

    @Test
    fun `a time too far from 1970 to be milliseconds fails as toEpochMilli failed`() {
        // Without an offset the old reader fell through to the offset parse, which refused the text;
        // with one, the offset parse succeeded and `toEpochMilli` threw.
        assertFailsWith<IllegalArgumentException> { EventTimestamp.fromWire("+999999999-12-31T23:59:59") }
        assertFailsWith<ArithmeticException> { EventTimestamp.fromWire("+999999999-12-31T23:59:59Z") }
        assertEquals(CustodyTimestamp.UNDATED, CustodyTimestamp.fromWire("+999999999-12-31T23:59:59"))
        assertFailsWith<ArithmeticException> {
            EventTimestamp.ofWallClock(LocalDateTime(999_999_999, 12, 31, 23, 59), kotlinx.datetime.TimeZone.UTC)
        }
    }

    @Test
    fun `a contact window's time reads two digits, a colon and two digits`() {
        assertEquals(LocalTime(10, 0), IsoText.parseHourMinute("10:00"))
        assertEquals(LocalTime(23, 59), IsoText.parseHourMinute("23:59"))
        // The pattern's smart resolver reads 24:00 as midnight.
        assertEquals(LocalTime(0, 0), IsoText.parseHourMinute("24:00"))
        listOf(
            "24:01", "23:60", "9:00", "09:5", "10:00:00", "1000", " 10:00", "10:00 ", "+1:00", "", "27.10.2026",
            "2026-10-27T10:00"
        ).forEach { text -> assertFailsWith<IllegalArgumentException>(text) { IsoText.parseHourMinute(text) } }
    }

    @Test
    fun `a layer's or an override's date reads as LocalDate parse did`() {
        assertEquals(LocalDate(2026, 10, 27), IsoText.parseDate("2026-10-27"))
        assertEquals(LocalDate(2024, 2, 29), IsoText.parseDate("2024-02-29"))
        assertEquals(LocalDate(0, 1, 1), IsoText.parseDate("0000-01-01"))
        assertEquals(LocalDate(12_026, 10, 27), IsoText.parseDate("+12026-10-27"))
        assertEquals(LocalDate(-1, 1, 1), IsoText.parseDate("-0001-01-01"))
        listOf(
            "", "27.10.2026", "2026-02-29", "2026-02-30", "2026-13-01", "2026-1-01", "+2026-10-27", "2026-10-27T10:00",
            "2026-10-27T"
        ).forEach { text -> assertFailsWith<IllegalArgumentException>(text) { IsoText.parseDate(text) } }
    }

    @Test
    fun `the date-time reader returns the wall clock it read`() {
        assertEquals(LocalDateTime(2026, 10, 27, 10, 0), IsoText.parseDateTime("2026-10-27T10:00"))
        assertEquals(
            LocalDateTime(2026, 10, 27, 10, 0, 7, 120_000_000),
            IsoText.parseDateTime("2026-10-27T10:00:07.12")
        )
        assertFailsWith<IllegalArgumentException> { IsoText.parseDateTime("2026-10-27T10:00Z") }
    }

    private companion object {
        /** 2026-10-27T10:00Z. */
        const val ZERO_SECONDS = 1_793_095_200_000L
        const val TWO_HOURS = 7_200_000L
    }
}
