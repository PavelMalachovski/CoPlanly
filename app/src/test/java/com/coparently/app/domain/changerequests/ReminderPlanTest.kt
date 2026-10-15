package com.coparently.app.domain.changerequests

import org.junit.Test
import java.time.Instant
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZoneOffset
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

private val ZONE: ZoneId = ZoneId.of("Europe/Prague")
private val NEW_YORK: ZoneId = ZoneId.of("America/New_York")

/** [text], a local date-time in Prague, as epoch millis. */
private fun at(text: String): Long = LocalDateTime.parse(text).atZone(ZONE).toInstant().toEpochMilli()

/** [text], a date-time in UTC, as epoch millis. */
private fun utc(text: String): Long = LocalDateTime.parse(text).toInstant(ZoneOffset.UTC).toEpochMilli()

/** The time of day [millis] reads in [zone]. */
private fun timeIn(zone: ZoneId, millis: Long): LocalTime = Instant.ofEpochMilli(millis).atZone(zone).toLocalTime()

private const val HOUR = 3_600_000L
private const val MINUTE = 60_000L

/**
 * When the answering parent's phone reminds them of a request that waits on them (phase 3a, spec
 * §4.3): up to three reminders by the length of the window, none between 21:00 and 08:00 on their own
 * clock, and none that the sending of the request or the phone's receiving of it has already passed.
 *
 * Saturday 10 October 2026 is still on summer time in Prague: the clocks go back on the 25th.
 */
class ReminderPlanTest {

    @Test
    fun `a day or more gets three, at a third, two thirds and two hours before`() {
        val sent = at("2026-10-10T10:00")
        val answerBy = at("2026-10-13T10:00")

        assertEquals(
            listOf(at("2026-10-11T10:00"), at("2026-10-12T10:00"), at("2026-10-13T08:00")),
            ReminderPlan.plan(sent, answerBy, sent, ZONE)
        )
    }

    @Test
    fun `six to twenty-four hours gets two, at the midpoint and an hour before`() {
        val sent = at("2026-10-10T10:00")
        val answerBy = at("2026-10-10T20:00")

        assertEquals(
            listOf(at("2026-10-10T15:00"), at("2026-10-10T19:00")),
            ReminderPlan.plan(sent, answerBy, sent, ZONE)
        )
    }

    @Test
    fun `under six hours gets one at the midpoint`() {
        val sent = at("2026-10-10T10:00")
        val answerBy = at("2026-10-10T14:00")

        assertEquals(listOf(at("2026-10-10T12:00")), ReminderPlan.plan(sent, answerBy, sent, ZONE))
    }

    @Test
    fun `a reminder at night moves to eight in the morning`() {
        // Night is 21:00 up to 08:00 on the answering parent's own clock; the evening before is 20:30.
        assertEquals(LocalTime.of(21, 0), ReminderPlan.QUIET_FROM)
        assertEquals(LocalTime.of(8, 0), ReminderPlan.QUIET_UNTIL)
        assertEquals(LocalTime.of(20, 30), ReminderPlan.EVENING_BEFORE)

        // 24 hours: a third is 02:00 on the 11th, two thirds 10:00, and two hours before is 16:00.
        val sent = at("2026-10-10T18:00")
        val answerBy = at("2026-10-11T18:00")

        assertEquals(
            listOf(at("2026-10-11T08:00"), at("2026-10-11T10:00"), at("2026-10-11T16:00")),
            ReminderPlan.plan(sent, answerBy, sent, ZONE)
        )
    }

    @Test
    fun `when eight is too late it moves to half past eight the evening before, and equal times merge`() {
        // 17 hours: the midpoint is 22:30 and an hour before the deadline is 06:00. Both are at night, and
        // 08:00 on the 11th is after the 07:00 deadline, so both go to 20:30 on the 10th: one time.
        val sent = at("2026-10-10T14:00")
        val answerBy = at("2026-10-11T07:00")

        assertEquals(listOf(at("2026-10-10T20:30")), ReminderPlan.plan(sent, answerBy, sent, ZONE))

        // 21:00 is already night: here the midpoint falls on it exactly (18 hours), and goes the same way.
        val noon = at("2026-10-10T12:00")
        val earlyMorning = at("2026-10-11T06:00")

        assertEquals(listOf(at("2026-10-10T20:30")), ReminderPlan.plan(noon, earlyMorning, noon, ZONE))
    }

    @Test
    fun `a time moved before the request was sent is dropped`() {
        // 9.5 hours: the midpoint (02:15) and the hour before the deadline (06:00) both move back to 20:30
        // on the 10th, an hour before the request was sent.
        val sent = at("2026-10-10T21:30")
        val answerBy = at("2026-10-11T07:00")

        assertEquals(emptyList(), ReminderPlan.plan(sent, answerBy, sent, ZONE))

        // A time moved to the very moment of sending is dropped too: a reminder comes after the request.
        val sentAtHalfPast = at("2026-10-10T20:30")

        assertEquals(emptyList(), ReminderPlan.plan(sentAtHalfPast, answerBy, sentAtHalfPast, ZONE))
    }

    @Test
    fun `reminders already past when the phone receives it are not planned`() {
        val sent = at("2026-10-10T10:00")
        val answerBy = at("2026-10-13T10:00")

        assertEquals(
            listOf(at("2026-10-13T08:00")),
            ReminderPlan.plan(sent, answerBy, at("2026-10-12T12:00"), ZONE)
        )

        // Only a time before the moment of receiving is past: one at that moment is still planned.
        assertEquals(
            listOf(at("2026-10-12T10:00"), at("2026-10-13T08:00")),
            ReminderPlan.plan(sent, answerBy, at("2026-10-12T10:00"), ZONE)
        )
    }

    @Test
    fun `night is the answering parent's own`() {
        // 08:00 on the 10th to 08:00 on the 11th in New York, on summer time. A third of the way is 16:00
        // there, which is 22:00 in Prague.
        val sent = utc("2026-10-10T12:00")
        val answerBy = utc("2026-10-11T12:00")

        val plan = ReminderPlan.plan(sent, answerBy, sent, NEW_YORK)

        // Two thirds is midnight there, and two hours before the deadline is 06:00. Both are night, and the
        // next 08:00 is the deadline itself, so both go to 20:30 on the 10th: the plan is 16:00 and 20:30.
        assertEquals(listOf(utc("2026-10-10T20:00"), utc("2026-10-11T00:30")), plan)
        plan.forEach { millis ->
            val time = timeIn(NEW_YORK, millis)
            assertTrue(time >= LocalTime.of(8, 0) && time < LocalTime.of(21, 0), "$time")
        }

        // The same two instants on a Prague phone are a different plan: there 20:00Z is 22:00, night.
        val inPrague = ReminderPlan.plan(sent, answerBy, sent, ZONE)

        assertFalse(utc("2026-10-10T20:00") in inPrague)
        assertEquals(listOf(utc("2026-10-11T06:00"), utc("2026-10-11T10:00")), inPrague)
    }

    @Test
    fun `nothing lands at night across the October clock change`() {
        // The clocks go back at 03:00 on Sunday the 25th: 49 hours from Saturday noon to Monday noon.
        // A third of the way is 03:20 on the 25th, night on winter time, so it goes to 08:00 that morning.
        val sent = at("2026-10-24T12:00")
        val answerBy = at("2026-10-26T12:00")

        val plan = ReminderPlan.plan(sent, answerBy, sent, ZONE)

        assertEquals(listOf(at("2026-10-25T08:00"), at("2026-10-25T19:40"), at("2026-10-26T10:00")), plan)
        plan.forEach { millis -> assertTrue(timeIn(ZONE, millis).hour in 8..20, "${timeIn(ZONE, millis)}") }

        // A night that ends after the change: a third of 36 hours is 23:00 on Saturday, and the next 08:00
        // is ten hours on, not nine, because the night is an hour longer. Counting hours would land at 07:00.
        val lateSent = at("2026-10-24T11:00")
        val lateAnswerBy = at("2026-10-25T22:00")

        val latePlan = ReminderPlan.plan(lateSent, lateAnswerBy, lateSent, ZONE)

        assertEquals(listOf(at("2026-10-25T08:00"), at("2026-10-25T10:00"), at("2026-10-25T20:00")), latePlan)
        latePlan.forEach { millis -> assertTrue(timeIn(ZONE, millis).hour in 8..20, "${timeIn(ZONE, millis)}") }
    }

    @Test
    fun `the count follows the window`() {
        assertEquals(3, ReminderPlan.countFor(24 * HOUR))
        assertEquals(2, ReminderPlan.countFor(24 * HOUR - MINUTE))
        assertEquals(2, ReminderPlan.countFor(6 * HOUR))
        assertEquals(1, ReminderPlan.countFor(6 * HOUR - MINUTE))
        assertEquals(1, ReminderPlan.countFor(1))
        assertEquals(0, ReminderPlan.countFor(0))
        assertEquals(0, ReminderPlan.countFor(-HOUR))

        // A deadline at or before the sending has no window, so nothing is planned.
        val sent = at("2026-10-10T10:00")

        assertEquals(emptyList(), ReminderPlan.plan(sent, sent, sent, ZONE))
        assertEquals(emptyList(), ReminderPlan.plan(sent, sent - HOUR, sent, ZONE))
    }
}
