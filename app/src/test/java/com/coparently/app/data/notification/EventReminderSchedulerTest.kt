package com.coparently.app.data.notification

import android.app.Application
import android.content.Context
import com.coparently.app.data.local.dao.EventDao
import com.coparently.app.data.local.entity.EventEntity
import com.coparently.app.domain.events.EventTimestamp
import com.coparently.app.domain.model.Event
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import java.util.TimeZone
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Reminders on alarms (9 November 2026): the owner's 30-minute reminder came 25 minutes before
 * the event, because WorkManager's delay is a job Doze holds back.
 *
 * What is pinned here: the alarm is armed for the reminder's wall clock in the system zone — across
 * a daylight-saving change too, where the old `Duration` between two wall clocks was an hour off —
 * every armed event is remembered and forgotten with it, [EventReminderScheduler.cancelAll] cancels
 * each one, and [ReminderRearm] arms again exactly what was armed, moving an older build's queued
 * work over once. Whether a phone fires the alarm on the minute is `DEVICE-CHECKLIST.md` §3.23.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class EventReminderSchedulerTest {

    private val context: Context get() = RuntimeEnvironment.getApplication()
    private val alarms = FakeAlarms()
    private lateinit var armed: ArmedReminders
    private lateinit var scheduler: EventReminderScheduler
    private lateinit var defaultZone: TimeZone

    @Before
    fun setUp() {
        defaultZone = TimeZone.getDefault()
        armed = ArmedReminders(context)
        armed.takeAll()
        armed.legacyWorkMoved = false
        scheduler = EventReminderScheduler(context, alarms, armed)
    }

    @After
    fun tearDown() {
        TimeZone.setDefault(defaultZone)
    }

    @Test
    fun `an event's reminder is armed for its lead time before the start, and remembered`() {
        val start = LocalDateTime.now().plusHours(3).truncatedTo(ChronoUnit.MINUTES)

        scheduler.schedule(event(start = start, reminderMinutes = 30))

        val due = alarms.armed.getValue("e1")
        assertEquals(start.minusMinutes(30).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli(), due.second)
        assertEquals(start, due.first.occurrenceStart)
        assertTrue(armed.contains("e1"))
    }

    @Test
    fun `a reminder across a daylight-saving change fires at its wall clock`() {
        val prague = ZoneId.of("Europe/Prague")
        TimeZone.setDefault(TimeZone.getTimeZone(prague))
        // Two days after Prague's next change, so exactly one change lies between now and the
        // reminder: waiting the wall clocks' difference would land an hour off.
        val change = prague.rules.nextTransition(Instant.now()).instant.atZone(prague).toLocalDate()
        val start = change.plusDays(2).atTime(10, 0)

        scheduler.schedule(event(start = start, reminderMinutes = 30))

        val expected = change.plusDays(2).atTime(9, 30).atZone(prague)
        assertEquals(expected.toInstant().toEpochMilli(), alarms.armed.getValue("e1").second)
    }

    @Test
    fun `an event without a reminder, or one whose reminder has passed, is cancelled and forgotten`() {
        val start = LocalDateTime.now().plusHours(3)
        scheduler.schedule(event(start = start, reminderMinutes = 30))

        scheduler.schedule(event(start = start, reminderMinutes = null))

        assertNull(alarms.armed["e1"])
        assertFalse(armed.contains("e1"))

        scheduler.schedule(event(start = LocalDateTime.now().plusMinutes(10), reminderMinutes = 30))
        assertNull(alarms.armed["e1"])
        assertFalse(armed.contains("e1"))
    }

    @Test
    fun `cancelAll cancels every armed event and the older builds' work, and forgets them`() {
        val start = LocalDateTime.now().plusHours(3)
        scheduler.schedule(event(id = "a", start = start, reminderMinutes = 30))
        scheduler.schedule(event(id = "b", start = start, reminderMinutes = 60))

        scheduler.cancelAll()

        assertTrue(alarms.armed.isEmpty())
        assertTrue(alarms.legacyWorkCancelled)
        assertTrue(armed.ids().isEmpty())
    }

    @Test
    fun `a re-arm arms what was armed again, and cancels an event that is gone`() = runTest {
        val start = LocalDateTime.now().plusHours(3).truncatedTo(ChronoUnit.MINUTES)
        scheduler.schedule(event(id = "kept", start = start, reminderMinutes = 30))
        scheduler.schedule(event(id = "gone", start = start, reminderMinutes = 30))
        armed.legacyWorkMoved = true
        // After a reboot the platform has no alarm left; the list is what says what to arm.
        alarms.armed.clear()
        val moved = start.plusHours(1)
        val eventDao = mockk<EventDao> {
            coEvery { getEventById("kept") } returns entity("kept", moved)
            coEvery { getEventById("gone") } returns null
        }

        ReminderRearm(eventDao, scheduler, alarms, armed).rearm()

        assertEquals(moved, alarms.armed.getValue("kept").first.occurrenceStart)
        assertNull(alarms.armed["gone"])
        assertEquals(setOf("kept"), armed.ids())
    }

    @Test
    fun `the first re-arm moves an older build's queued work onto alarms, and only once`() = runTest {
        val start = LocalDateTime.now().plusHours(3).truncatedTo(ChronoUnit.MINUTES)
        alarms.legacyQueued = setOf("old")
        val eventDao = mockk<EventDao> {
            coEvery { getEventIdsWithReminders() } returns listOf("old", "never-armed")
            coEvery { getEventById("old") } returns entity("old", start)
        }
        val rearm = ReminderRearm(eventDao, scheduler, alarms, armed)

        rearm.rearm()

        assertEquals(setOf("old"), alarms.armed.keys)
        assertTrue(alarms.legacyWorkCancelled)
        assertTrue(armed.legacyWorkMoved)

        alarms.legacyQueued = setOf("never-armed")
        rearm.rearm()
        assertEquals(setOf("old"), alarms.armed.keys)
    }

    private fun event(id: String = "e1", start: LocalDateTime, reminderMinutes: Int?) = Event(
        id = id,
        title = "Dentist",
        startDateTime = start,
        endDateTime = start.plusHours(1),
        eventType = "medical",
        parentOwner = "mom",
        createdAt = start.minusDays(3),
        updatedAt = start.minusDays(3),
        reminderMinutes = reminderMinutes
    )

    private fun entity(id: String, start: LocalDateTime) = EventEntity(
        id = id,
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

    /** Records what would be armed, by event id. */
    private class FakeAlarms : ReminderAlarms {
        val armed = mutableMapOf<String, Pair<DueReminder, Long>>()
        var legacyWorkCancelled = false
        var legacyQueued: Set<String> = emptySet()

        override fun set(due: DueReminder, triggerAtMillis: Long) {
            armed[due.eventId] = due to triggerAtMillis
        }

        override fun cancel(eventId: String) {
            armed.remove(eventId)
        }

        override fun cancelLegacyWork() {
            legacyWorkCancelled = true
        }

        override suspend fun takeLegacyWork(eventIds: Collection<String>): Set<String> =
            eventIds.filterTo(mutableSetOf()) { it in legacyQueued }.also { legacyQueued = legacyQueued - it }
    }
}
