package com.coparently.app.domain.usecase

import com.coparently.app.domain.events.AllDayEvent
import com.coparently.app.domain.model.Event
import com.coparently.app.domain.notification.ReminderPlanner
import com.coparently.app.domain.time.durationUntil
import com.coparently.app.domain.time.minus
import com.coparently.app.domain.time.plus
import com.coparently.app.domain.usecase.EventTimeOracle.OldEvent
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.toJavaLocalDateTime
import kotlinx.datetime.toKotlinLocalDate
import kotlinx.datetime.toKotlinLocalDateTime
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit
import kotlin.random.Random
import kotlin.test.assertEquals
import kotlin.time.toJavaDuration
import kotlin.time.toKotlinDuration

/**
 * The event time code on kotlinx-datetime against the `java.time` code it replaced
 * ([EventTimeOracle]), on seeded random events (`Random(20261102)`) — MON-24, Task 7 slice 2.
 *
 * Recurrence expansion, reminder planning and the all-day shape must give the same answer for every
 * event the old code saw: same occurrences (start and end), same reminder, same all-day reading.
 * The random starts lean on what broke before or could break now: the 29th to 31st of a month,
 * midnight and 23:59, the 02:00–03:00 hour a daylight-saving change skips or repeats, seconds and
 * nanoseconds, negative and multi-day durations, and ranges years away from the start. The
 * wall-clock arithmetic in `domain/time/WallClock.kt` is held to `java.time` on its own.
 */
class EventTimeParityTest {

    private val random = Random(20261102)

    @Test
    fun expansionMatchesJavaTime() {
        repeat(ITERATIONS) {
            val old = randomEvent()
            val rangeStart = randomRangeStart(old.start)
            val rangeEnd = rangeStart.plusMinutes(random.nextLong(0, RANGE_MINUTES))
            val expected = EventTimeOracle.expand(old, rangeStart, rangeEnd)
            val actual = RecurrenceExpander.expand(old.toEvent(), rangeStart.kx(), rangeEnd.kx())
                .map { it.startDateTime.toJavaLocalDateTime() to it.endDateTime?.toJavaLocalDateTime() }
            assertEquals(expected, actual, "expand($old, $rangeStart, $rangeEnd)")
        }
    }

    @Test
    fun remindersMatchJavaTime() {
        repeat(ITERATIONS) {
            val old = randomEvent()
            val now = randomRangeStart(old.start)
            val expected = EventTimeOracle.nextReminder(old, now)
            val actual = ReminderPlanner.nextReminder(old.toEvent(), now.kx())
            assertEquals(
                expected?.let { it.occurrenceStart to it.remindAt },
                actual?.let { it.occurrenceStart.toJavaLocalDateTime() to it.remindAt.toJavaLocalDateTime() },
                "nextReminder($old, $now)"
            )
            val probe = expected?.occurrenceStart ?: now
            assertEquals(
                EventTimeOracle.isOccurrence(old, probe),
                ReminderPlanner.isOccurrence(old.toEvent(), probe.kx()),
                "isOccurrence($old, $probe)"
            )
        }
    }

    @Test
    fun allDayShapeMatchesJavaTime() {
        repeat(ITERATIONS) {
            val old = randomEvent()
            val event = old.toEvent()
            assertEquals(EventTimeOracle.isAllDay(old.start, old.end), AllDayEvent.isAllDay(event), "$old")
            assertEquals(EventTimeOracle.lastDay(old.start, old.end).toKotlinLocalDate(), AllDayEvent.lastDay(event))
        }
    }

    @Test
    fun wallClockArithmeticMatchesJavaTime() {
        val units = listOf(
            DateTimeUnit.NANOSECOND to ChronoUnit.NANOS,
            DateTimeUnit.SECOND to ChronoUnit.SECONDS,
            DateTimeUnit.MINUTE to ChronoUnit.MINUTES,
            DateTimeUnit.HOUR to ChronoUnit.HOURS,
            DateTimeUnit.DAY to ChronoUnit.DAYS,
            DateTimeUnit.WEEK to ChronoUnit.WEEKS,
            DateTimeUnit.MONTH to ChronoUnit.MONTHS,
            DateTimeUnit.YEAR to ChronoUnit.YEARS
        )
        repeat(ITERATIONS) {
            val a = randomDateTime()
            val b = randomDateTime()
            for ((unit, chrono) in units) {
                val amount = random.nextLong(-AMOUNT, AMOUNT)
                assertEquals(a.plus(amount, chrono).kx(), a.kx().plus(amount, unit), "$a + $amount $unit")
                assertEquals(a.minus(amount, chrono).kx(), a.kx().minus(amount, unit), "$a - $amount $unit")
            }
            val between = java.time.Duration.between(a, b)
            assertEquals(between, a.kx().durationUntil(b.kx()).toJavaDuration(), "$a until $b")
            assertEquals(a.plus(between).kx(), a.kx() + between.toKotlinDuration(), "$a + $between")
            assertEquals(b.minus(between).kx(), b.kx() - between.toKotlinDuration(), "$b - $between")
        }
    }

    private fun randomEvent(): OldEvent {
        val start = randomDateTime()
        val end = when (random.nextInt(6)) {
            0 -> null
            1 -> start.minusMinutes(random.nextLong(1, DAY_MINUTES)) // malformed: ends before it starts
            2 -> start.toLocalDate().plusDays(random.nextLong(0, 4)).atTime(23, 59) // all-day shape
            3 -> start.toLocalDate().plusDays(random.nextLong(1, 4)).atStartOfDay()
            else -> start.plusMinutes(random.nextLong(0, 4 * DAY_MINUTES))
        }
        val pattern = PATTERNS[random.nextInt(PATTERNS.size)]
        val recurrenceEnd = when (random.nextInt(3)) {
            0 -> null
            1 -> start.toLocalDate().plusDays(random.nextLong(0, 30))
            else -> start.toLocalDate().plusDays(random.nextLong(-5, 900))
        }
        return OldEvent(
            start = start,
            end = end,
            isRecurring = pattern != null && random.nextInt(8) != 0,
            pattern = pattern,
            recurrenceEndDate = recurrenceEnd,
            reminderMinutes = REMINDERS[random.nextInt(REMINDERS.size)]
        )
    }

    private fun randomRangeStart(start: LocalDateTime): LocalDateTime = when (random.nextInt(3)) {
        0 -> start.plusMinutes(random.nextLong(-3 * DAY_MINUTES, 3 * DAY_MINUTES))
        1 -> start.plusDays(random.nextLong(-40, 400)).withSecond(0).withNano(0)
        else -> start.plusDays(random.nextLong(0, 5000)).plusMinutes(random.nextLong(0, DAY_MINUTES))
    }

    private fun randomDateTime(): LocalDateTime {
        val year = random.nextInt(1990, 2070)
        val month = random.nextInt(1, 13)
        val length = LocalDate.of(year, month, 1).lengthOfMonth()
        val day = if (random.nextBoolean()) random.nextInt(28, length + 1) else random.nextInt(1, length + 1)
        val date = LocalDate.of(year, month, day)
        return when (random.nextInt(7)) {
            0 -> date.atStartOfDay()
            1 -> date.atTime(23, 59)
            6 -> date.atTime(23, 59, 30)
            2 -> date.atTime(2, random.nextInt(60))
            3 -> date.atTime(random.nextInt(24), random.nextInt(60), random.nextInt(60), random.nextInt(1_000_000_000))
            else -> date.atTime(random.nextInt(24), random.nextInt(4) * 15)
        }
    }

    private fun OldEvent.toEvent() = Event(
        id = "e",
        title = "Parity",
        startDateTime = start.kx(),
        endDateTime = end?.kx(),
        eventType = "other",
        parentOwner = "mom",
        isRecurring = isRecurring,
        recurrencePattern = pattern,
        recurrenceEndDate = recurrenceEndDate?.toKotlinLocalDate(),
        reminderMinutes = reminderMinutes,
        createdAt = start.kx(),
        updatedAt = start.kx()
    )

    private fun LocalDateTime.kx() = toKotlinLocalDateTime()

    private companion object {
        const val ITERATIONS = 5_000
        const val DAY_MINUTES = 24L * 60
        const val RANGE_MINUTES = 70L * DAY_MINUTES
        const val AMOUNT = 100_000L
        val PATTERNS = listOf(null, "daily", "weekly", "biweekly", "monthly", "yearly", "")
        val REMINDERS = listOf(null, -5, 0, 10, 30, 60, 1440, 10_080)
    }
}
