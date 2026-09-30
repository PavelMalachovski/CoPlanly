package com.coparently.app.data.notification

import com.coparently.app.data.local.entity.EventEntity
import com.coparently.app.domain.events.EventTimestamp
import org.junit.Test
import java.time.LocalDateTime
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * [ReminderWorker.shouldFire]: a reminder is checked against the event as Room holds it when it
 * is due. Only this phone's own saves reschedule or cancel the work, so an event the co-parent
 * deleted or moved used to announce itself anyway, at the old time and under the old title.
 */
class ReminderWorkerTest {

    private val start = LocalDateTime.of(2026, 9, 14, 16, 0)

    @Test
    fun `a reminder for an event still as scheduled fires`() {
        assertTrue(ReminderWorker.shouldFire(event(), start.toString()))
    }

    @Test
    fun `a reminder for an event that is gone does not fire`() {
        assertFalse(ReminderWorker.shouldFire(null, start.toString()))
    }

    @Test
    fun `a reminder for an event deleted on this phone, still queued, does not fire`() {
        assertFalse(ReminderWorker.shouldFire(event().copy(deletedAtMillis = 1L), start.toString()))
    }

    @Test
    fun `a reminder for an event that has moved does not fire`() {
        assertFalse(ReminderWorker.shouldFire(event().copy(startDateTime = start.plusDays(1)), start.toString()))
    }

    @Test
    fun `work an older build enqueued, without a start, checks existence only`() {
        assertTrue(ReminderWorker.shouldFire(event(), null))
        assertTrue(ReminderWorker.shouldFire(event(), "not a date"))
        assertFalse(ReminderWorker.shouldFire(null, null))
    }

    @Test
    fun `a reminder for an event whose reminder was removed does not fire`() {
        assertFalse(ReminderWorker.shouldFire(event().copy(reminderMinutes = null), start.toString()))
    }

    @Test
    fun `a reminder for a later occurrence of a recurring event fires`() {
        val weekly = event().copy(isRecurring = true, recurrencePattern = "weekly")

        assertTrue(ReminderWorker.shouldFire(weekly, start.plusWeeks(3).toString()))
        // Not an occurrence: an hour off, or past a recurrence end set since.
        assertFalse(ReminderWorker.shouldFire(weekly, start.plusWeeks(3).plusHours(1).toString()))
        assertFalse(
            ReminderWorker.shouldFire(
                weekly.copy(recurrenceEndDate = start.toLocalDate().plusWeeks(2)),
                start.plusWeeks(3).toString()
            )
        )
    }

    private fun event() = EventEntity(
        id = "e1",
        title = "Dentist",
        startDateTime = start,
        endDateTime = start.plusHours(1),
        eventType = "medical",
        parentOwner = "mom",
        reminderMinutes = 30,
        createdAt = start.minusDays(3),
        updatedAt = start.minusDays(3),
        updatedAtMillis = EventTimestamp.ofWallClock(start.minusDays(3))
    )
}
