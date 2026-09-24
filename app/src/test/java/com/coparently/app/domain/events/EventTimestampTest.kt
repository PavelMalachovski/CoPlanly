package com.coparently.app.domain.events

import org.junit.Test
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/**
 * The events `updatedAt` wire form (MON-4): an instant, written as offset-free UTC text so an
 * older build — which parses the field with `ISO_LOCAL_DATE_TIME` — still reads every document.
 */
class EventTimestampTest {

    private val noonZulu = Instant.parse("2026-09-23T12:00:00Z").toEpochMilli()

    @Test
    fun `the wire form is UTC with no offset`() {
        assertEquals("2026-09-23T12:00:00", EventTimestamp.toWire(noonZulu))
    }

    @Test
    fun `an older build can still parse what this one writes`() {
        // The whole reason the field carries no `Z`: the old reader would throw on it and skip
        // the event, so a co-parent who has not updated would never see it.
        val written = EventTimestamp.toWire(noonZulu + 123)
        LocalDateTime.parse(written, DateTimeFormatter.ISO_LOCAL_DATE_TIME)
    }

    @Test
    fun `wire text round-trips to the same instant`() {
        val millis = noonZulu + 456
        assertEquals(millis, EventTimestamp.fromWire(EventTimestamp.toWire(millis)))
    }

    @Test
    fun `a legacy naive value is read as UTC`() {
        // An older build wrote its own wall clock. The offset it was written in is not stored, so
        // UTC is the reading — wrong by that writer's offset, and irreducibly so.
        assertEquals(noonZulu, EventTimestamp.fromWire("2026-09-23T12:00:00"))
        assertEquals(noonZulu + 789, EventTimestamp.fromWire("2026-09-23T12:00:00.789"))
    }

    @Test
    fun `a value that carries an offset is honoured`() {
        assertEquals(noonZulu, EventTimestamp.fromWire("2026-09-23T14:00:00+02:00"))
        assertEquals(noonZulu, EventTimestamp.fromWire("2026-09-23T12:00:00Z"))
    }

    @Test
    fun `an unreadable value throws rather than inventing a time`() {
        assertFailsWith<DateTimeParseException> { EventTimestamp.fromWire("yesterday") }
    }

    @Test
    fun `one wall clock in two zones is two instants`() {
        // Prague at noon (UTC+2 in September) and New York at noon (UTC-4) — the comparison the
        // naive `LocalDateTime` called a tie.
        val wallClock = LocalDateTime.of(2026, 9, 23, 12, 0)
        val prague = EventTimestamp.ofWallClock(wallClock, ZoneId.of("Europe/Prague"))
        val newYork = EventTimestamp.ofWallClock(wallClock, ZoneId.of("America/New_York"))
        assertEquals(Instant.parse("2026-09-23T10:00:00Z").toEpochMilli(), prague)
        assertEquals(Instant.parse("2026-09-23T16:00:00Z").toEpochMilli(), newYork)
    }

    @Test
    fun `the displayed wall clock is the instant in the viewer's zone`() {
        val plusTwo = ZoneOffset.ofHours(2)
        assertEquals(
            LocalDateTime.of(2026, 9, 23, 14, 0),
            EventTimestamp.toWallClock(noonZulu, plusTwo)
        )
        assertEquals(noonZulu, EventTimestamp.ofWallClock(EventTimestamp.toWallClock(noonZulu, plusTwo), plusTwo))
    }
}
