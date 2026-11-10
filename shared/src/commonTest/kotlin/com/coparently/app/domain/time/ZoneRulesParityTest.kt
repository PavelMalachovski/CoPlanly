package com.coparently.app.domain.time

import com.coparently.app.domain.events.EventTimestamp
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The zone rules the wall-clock conversions rest on, held to what `java.time` answered.
 *
 * On Android kotlinx-datetime asks `java.time` itself; on iOS it reads the time-zone database on
 * its own, so these literals — computed once from `java.time` — are what keeps the two phones of
 * one family agreeing about a gap, an overlap and the date line (Pacific/Kiritimati at UTC+14 and
 * Pacific/Pago_Pago at UTC−11, the zones the two-parent chat test runs across).
 */
class ZoneRulesParityTest {

    private val prague = TimeZone.of("Europe/Prague")

    @Test
    fun `a wall clock in the spring gap moves forward by the gap`() {
        // 02:30 does not exist in Prague on 29 March 2026; `atZone` reads it as 03:30 CEST, 01:30Z.
        assertEquals(1_774_747_800_000L, EventTimestamp.ofWallClock(LocalDateTime(2026, 3, 29, 2, 30), prague))
    }

    @Test
    fun `a wall clock in the autumn overlap takes the earlier offset`() {
        // 02:30 happens twice in Prague on 25 October 2026; `atZone` picks CEST, 00:30Z.
        assertEquals(1_792_888_200_000L, EventTimestamp.ofWallClock(LocalDateTime(2026, 10, 25, 2, 30), prague))
    }

    @Test
    fun `one instant is a different date on either side of the date line`() {
        val instant = 1_793_095_200_000L
        val kiritimati = EventTimestamp.toWallClock(instant, TimeZone.of("Pacific/Kiritimati"))
        val pagoPago = EventTimestamp.toWallClock(instant, TimeZone.of("Pacific/Pago_Pago"))

        assertEquals(LocalDateTime(2026, 10, 28, 0, 0), kiritimati)
        assertEquals(LocalDateTime(2026, 10, 26, 23, 0), pagoPago)
        assertEquals(LocalDate(2026, 10, 28), kiritimati.date)
        assertEquals(LocalDate(2026, 10, 26), pagoPago.date)
        assertEquals(
            LocalDateTime(2026, 10, 27, 23, 59, 59, 999_000_000),
            EventTimestamp.toWallClock(instant - 1, TimeZone.of("Pacific/Kiritimati"))
        )
        assertEquals(instant, EventTimestamp.ofWallClock(kiritimati, TimeZone.of("Pacific/Kiritimati")))
        assertEquals(instant, EventTimestamp.ofWallClock(pagoPago, TimeZone.of("Pacific/Pago_Pago")))
    }

    @Test
    fun `the default zone is the device's`() {
        val wallClock = LocalDateTime(2026, 10, 27, 10, 0)
        assertEquals(
            EventTimestamp.ofWallClock(wallClock, TimeZone.currentSystemDefault()),
            EventTimestamp.ofWallClock(wallClock)
        )
        assertEquals(
            EventTimestamp.toWallClock(1_793_095_200_000L, TimeZone.currentSystemDefault()),
            EventTimestamp.toWallClock(1_793_095_200_000L)
        )
    }
}
