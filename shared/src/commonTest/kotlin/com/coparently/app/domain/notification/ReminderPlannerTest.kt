package com.coparently.app.domain.notification

import com.coparently.app.domain.model.Event
import com.coparently.app.domain.time.minus
import com.coparently.app.domain.time.plus
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * [ReminderPlanner]: which occurrence the next reminder is for. The scheduler used to take the
 * master start minus the lead time and give up when that had passed, so a weekly event reminded
 * before its first occurrence at most, and never again.
 */
class ReminderPlannerTest {

    /** A Monday. */
    private val now = LocalDateTime(2026, 9, 14, 12, 0)

    @Test
    fun `a single event in the future is reminded of at its start minus the lead time`() {
        val start = now.plus(1, DateTimeUnit.DAY)

        val plan = ReminderPlanner.nextReminder(event(start, reminderMinutes = 30), now)

        assertEquals(ReminderPlan(occurrenceStart = start, remindAt = start.minus(30, DateTimeUnit.MINUTE)), plan)
    }

    @Test
    fun `a single event whose reminder time has passed has no reminder`() {
        assertNull(ReminderPlanner.nextReminder(event(now.minus(1, DateTimeUnit.DAY), reminderMinutes = 30), now))
        // Starts in twenty minutes, reminds thirty before: already late.
        assertNull(ReminderPlanner.nextReminder(event(now.plus(20, DateTimeUnit.MINUTE), reminderMinutes = 30), now))
        // Due exactly now counts as passed, as the scheduler always treated a zero delay.
        assertNull(ReminderPlanner.nextReminder(event(now.plus(30, DateTimeUnit.MINUTE), reminderMinutes = 30), now))
    }

    @Test
    fun `an event without a reminder has none`() {
        assertNull(ReminderPlanner.nextReminder(event(now.plus(1, DateTimeUnit.DAY), reminderMinutes = null), now))
        assertNull(ReminderPlanner.nextReminder(event(now.plus(1, DateTimeUnit.DAY), reminderMinutes = -5), now))
    }

    @Test
    fun `a lead time of zero reminds at the start`() {
        val start = now.plus(2, DateTimeUnit.HOUR)

        assertEquals(start, ReminderPlanner.nextReminder(event(start, reminderMinutes = 0), now)?.remindAt)
    }

    @Test
    fun `a weekly event whose first occurrence has passed is reminded of next week's`() {
        // Every Monday at 16:00 since three weeks ago; today's occurrence is still ahead.
        val first = LocalDateTime(2026, 8, 24, 16, 0)
        val weekly = event(first, reminderMinutes = 60, pattern = "weekly")

        assertEquals(LocalDateTime(2026, 9, 14, 16, 0), ReminderPlanner.nextReminder(weekly, now)?.occurrenceStart)
        // Once today's reminder time (15:00) has gone, the next one is a week later.
        assertEquals(
            LocalDateTime(2026, 9, 21, 16, 0),
            ReminderPlanner.nextReminder(weekly, now.withHour(15))?.occurrenceStart
        )
    }

    @Test
    fun `an occurrence already under way is not the next one`() {
        // Daily 11:00-13:00: at noon today's is running, and tomorrow's is the next to remind of.
        val daily = event(LocalDateTime(2026, 9, 1, 11, 0), reminderMinutes = 10, pattern = "daily")
            .copy(endDateTime = LocalDateTime(2026, 9, 1, 13, 0))

        assertEquals(LocalDateTime(2026, 9, 15, 11, 0), ReminderPlanner.nextReminder(daily, now)?.occurrenceStart)
    }

    @Test
    fun `the recurrence end date is respected`() {
        val first = LocalDateTime(2026, 8, 24, 16, 0)
        val endedLastWeek = event(first, reminderMinutes = 60, pattern = "weekly", endDate = LocalDate(2026, 9, 7))
        val endsToday = endedLastWeek.copy(recurrenceEndDate = LocalDate(2026, 9, 14))

        assertNull(ReminderPlanner.nextReminder(endedLastWeek, now))
        assertEquals(
            LocalDateTime(2026, 9, 14, 16, 0),
            ReminderPlanner.nextReminder(endsToday, now)?.occurrenceStart
        )
        assertNull(ReminderPlanner.nextReminder(endsToday, now.withHour(15)))
    }

    @Test
    fun `a monthly event is found across the month's gap`() {
        val monthly = event(LocalDateTime(2026, 1, 31, 9, 0), reminderMinutes = 1440, pattern = "monthly")

        // September has no 31st: the occurrence clamps to the 30th, as the calendar draws it.
        assertEquals(LocalDateTime(2026, 9, 30, 9, 0), ReminderPlanner.nextReminder(monthly, now)?.occurrenceStart)
    }

    @Test
    fun `an unrecognised pattern is reminded of as a single event`() {
        val start = now.plus(2, DateTimeUnit.DAY)

        assertEquals(start, ReminderPlanner.nextReminder(event(start, 30, pattern = "yearly"), now)?.occurrenceStart)
        assertNull(ReminderPlanner.nextReminder(event(now.minus(2, DateTimeUnit.DAY), 30, pattern = "yearly"), now))
    }

    @Test
    fun `an occurrence is recognised by its start`() {
        val first = LocalDateTime(2026, 8, 24, 16, 0)
        val weekly = event(first, reminderMinutes = 60, pattern = "weekly", endDate = LocalDate(2026, 9, 14))

        assertTrue(ReminderPlanner.isOccurrence(weekly, first))
        assertTrue(ReminderPlanner.isOccurrence(weekly, LocalDateTime(2026, 9, 7, 16, 0)))
        assertFalse(ReminderPlanner.isOccurrence(weekly, LocalDateTime(2026, 9, 7, 17, 0)))
        // Past the end date.
        assertFalse(ReminderPlanner.isOccurrence(weekly, LocalDateTime(2026, 9, 21, 16, 0)))

        val single = event(first, reminderMinutes = 60)
        assertTrue(ReminderPlanner.isOccurrence(single, first))
        assertFalse(ReminderPlanner.isOccurrence(single, first.plus(1, DateTimeUnit.WEEK)))
    }

    private fun LocalDateTime.withHour(hour: Int) = LocalDateTime(date, LocalTime(hour, minute, second, nanosecond))

    private fun event(
        start: LocalDateTime,
        reminderMinutes: Int?,
        pattern: String? = null,
        endDate: LocalDate? = null
    ) = Event(
        id = "e1",
        title = "Swimming",
        startDateTime = start,
        endDateTime = start.plus(1, DateTimeUnit.HOUR),
        eventType = "activity",
        parentOwner = "mom",
        isRecurring = pattern != null,
        recurrencePattern = pattern,
        recurrenceEndDate = endDate,
        reminderMinutes = reminderMinutes,
        createdAt = start,
        updatedAt = start
    )
}
