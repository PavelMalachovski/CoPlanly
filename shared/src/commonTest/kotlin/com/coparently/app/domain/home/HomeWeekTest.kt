package com.coparently.app.domain.home

import com.coparently.app.domain.custody.ContactWindow
import com.coparently.app.domain.model.Event
import com.coparently.app.domain.time.minus
import com.coparently.app.domain.time.plus
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.atTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The window, the filtering and the keys behind the home screen's week.
 *
 * Kept pure and tested here rather than through the ViewModel because all three are arithmetic:
 * a seven-day window that starts on the wrong day, a private event that leaks, or two occurrences
 * of one series that collapse into one row are each a wrong answer with no exception to notice.
 */
class HomeWeekTest {

    private val now: LocalDateTime = LocalDateTime(2026, 8, 21, 14, 0)

    @Test
    fun `the window is seven days from today - not the calendar week`() {
        // 21 August 2026 is a Friday. A Monday-to-Sunday week would end in two days; this one
        // has to reach the following Thursday, because that is what "the next seven days" means
        // to a parent opening the app on a Friday.
        val entries = HomeWeek.of(
            events = listOf(
                event("today-later", now.plus(2, DateTimeUnit.HOUR)),
                event("seventh-day", LocalDateTime(2026, 8, 27, 23, 30)),
                event("eighth-day", LocalDateTime(2026, 8, 28, 0, 30))
            ),
            now = now,
            userId = "uid-mom",
            custodyFor = { null }
        )

        assertEquals(listOf("today-later", "seventh-day"), entries.map { it.event.id })
    }

    @Test
    fun `an event that already started today is behind the parent - not ahead of them`() {
        val entries = HomeWeek.of(
            events = listOf(
                event("this-morning", now.minus(3, DateTimeUnit.HOUR)),
                event("this-evening", now.plus(3, DateTimeUnit.HOUR))
            ),
            now = now,
            userId = "uid-mom",
            custodyFor = { null }
        )

        assertEquals(listOf("this-evening"), entries.map { it.event.id })
    }

    @Test
    fun `two occurrences of one series get keys that do not collide`() {
        // Recurring occurrences share the master event's id — that is the contract
        // `RecurrenceExpander` works to, and it is why the id alone cannot be the list key.
        val master = event("weekly-football", now.plus(1, DateTimeUnit.DAY))
        val entries = HomeWeek.of(
            events = listOf(master, master.copy(startDateTime = now.plus(3, DateTimeUnit.DAY))),
            now = now,
            userId = "uid-mom",
            custodyFor = { null }
        )

        assertEquals(2, entries.size)
        assertEquals(2, entries.map { it.key }.toSet().size)
        assertTrue(entries.all { it.event.id == "weekly-football" })
    }

    @Test
    fun `a private event of the co-parent's never appears`() {
        val entries = HomeWeek.of(
            events = listOf(
                event("theirs", now.plus(1, DateTimeUnit.DAY)).copy(isPrivate = true, createdByFirebaseUid = "uid-dad"),
                event("mine", now.plus(1, DateTimeUnit.DAY)).copy(isPrivate = true, createdByFirebaseUid = "uid-mom")
            ),
            now = now,
            userId = "uid-mom",
            custodyFor = { null }
        )

        assertEquals(listOf("mine"), entries.map { it.event.id })
    }

    @Test
    fun `a private event with no creator stamped stays on its own owner's screen`() {
        // Nothing distinguishes this user's un-stamped row from anybody else's, and every such
        // row is on this device — hiding it would take a parent's own private event away from
        // their own week.
        val entries = HomeWeek.of(
            events = listOf(event("unstamped", now.plus(1, DateTimeUnit.DAY)).copy(isPrivate = true)),
            now = now,
            userId = "uid-mom",
            custodyFor = { null }
        )

        assertEquals(listOf("unstamped"), entries.map { it.event.id })
    }

    @Test
    fun `each row carries the parent whose day it falls on - not the event's owner`() {
        val tuesday = LocalDate(2026, 8, 25)
        val entries = HomeWeek.of(
            // The event belongs to slot 2, but it falls on a day slot 1 has the child.
            events = listOf(event("dentist", tuesday.atTime(9, 0)).copy(parentOwner = "dad")),
            now = now,
            userId = "uid-mom",
            custodyFor = { date -> if (date == tuesday) "mom" else "dad" }
        )

        assertEquals("mom", entries.single().dayParent)
    }

    @Test
    fun `a date no arrangement answers for leaves the day's parent unknown`() {
        // Null rather than a guess: an unanswered day is not "the event owner's day", and
        // saying so would put a name on the row that nobody agreed to.
        val entries = HomeWeek.of(
            events = listOf(event("dentist", now.plus(1, DateTimeUnit.DAY)).copy(parentOwner = "dad")),
            now = now,
            userId = "uid-mom",
            custodyFor = { null }
        )

        assertNull(entries.single().dayParent)
    }

    @Test
    fun `the week is ordered earliest first`() {
        val entries = HomeWeek.of(
            events = listOf(
                event("thursday", now.plus(6, DateTimeUnit.DAY)),
                event("tomorrow", now.plus(1, DateTimeUnit.DAY)),
                event("sunday", now.plus(2, DateTimeUnit.DAY))
            ),
            now = now,
            userId = "uid-mom",
            custodyFor = { null }
        )

        assertEquals(listOf("tomorrow", "sunday", "thursday"), entries.map { it.event.id })
    }

    @Test
    fun `today's agenda covers the whole day - past events included`() {
        // Unlike the week, whose window opens at now: the today card answers "what is today",
        // and a 9:00 appointment does not stop having happened at 9:05.
        val agenda = HomeWeek.todayOf(
            events = listOf(
                event("this-morning", now.minus(3, DateTimeUnit.HOUR)),
                event("this-evening", now.plus(3, DateTimeUnit.HOUR)),
                event("tomorrow", now.plus(1, DateTimeUnit.DAY))
            ),
            today = now.date,
            userId = "uid-mom",
            custodyFor = { null }
        )

        assertEquals(listOf("this-morning", "this-evening"), agenda.events.map { it.id })
    }

    @Test
    fun `a multi-day event still running today is part of today`() {
        // Overlap, not start date — the same rule the calendar's day queries follow.
        val agenda = HomeWeek.todayOf(
            events = listOf(
                event("camp", now.minus(2, DateTimeUnit.DAY)).copy(endDateTime = now.plus(1, DateTimeUnit.DAY)),
                event("last-week", now.minus(5, DateTimeUnit.DAY)).copy(endDateTime = now.minus(4, DateTimeUnit.DAY))
            ),
            today = now.date,
            userId = "uid-mom",
            custodyFor = { null }
        )

        assertEquals(listOf("camp"), agenda.events.map { it.id })
    }

    @Test
    fun `today's agenda names the day's custody parent`() {
        val agenda = HomeWeek.todayOf(
            events = emptyList(),
            today = now.date,
            userId = "uid-mom",
            custodyFor = { date -> if (date == now.date) "dad" else null }
        )

        assertEquals("dad", agenda.dayParent)
    }

    @Test
    fun `today's agenda carries today's contact windows and no other day's`() {
        val window = ContactWindow(
            dayIndex = 3,
            start = LocalTime.parse("15:00"),
            end = LocalTime.parse("19:00"),
            parent = ContactWindow.SLOT_TWO
        )
        val agenda = HomeWeek.todayOf(
            events = emptyList(),
            today = now.date,
            userId = "uid-mom",
            custodyFor = { ContactWindow.SLOT_ONE },
            contactWindowsFor = { date -> if (date == now.date) listOf(window) else emptyList() }
        )

        assertEquals(listOf(window), agenda.contactWindows)
    }

    @Test
    fun `today's agenda has no contact windows when nothing supplies them`() {
        val agenda = HomeWeek.todayOf(
            events = emptyList(),
            today = now.date,
            userId = "uid-mom",
            custodyFor = { null }
        )

        assertTrue(agenda.contactWindows.isEmpty())
    }

    @Test
    fun `a co-parent's private event never appears on today's agenda`() {
        val agenda = HomeWeek.todayOf(
            events = listOf(
                event("theirs", now.plus(1, DateTimeUnit.HOUR))
                    .copy(isPrivate = true, createdByFirebaseUid = "uid-dad")
            ),
            today = now.date,
            userId = "uid-mom",
            custodyFor = { null }
        )

        assertTrue(agenda.events.isEmpty())
    }

    private fun event(id: String, start: LocalDateTime) = Event(
        id = id,
        title = id,
        startDateTime = start,
        eventType = "general",
        parentOwner = "mom",
        createdAt = start,
        updatedAt = start
    )
}
