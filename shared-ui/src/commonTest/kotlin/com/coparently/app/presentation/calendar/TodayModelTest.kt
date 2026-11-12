package com.coparently.app.presentation.calendar

import com.coparently.app.domain.home.TodayAgenda
import com.coparently.app.domain.model.Event
import com.coparently.app.presentation.calendar.components.TodayModel
import com.coparently.app.presentation.calendar.components.steppedDay
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** What the calendar's Today card reads from today's agenda, and where its arrows step. */
class TodayModelTest {

    private val today = LocalDate(2026, 11, 12)
    private val morning = event("Dentist", LocalDateTime(2026, 11, 12, 9, 0), LocalDateTime(2026, 11, 12, 10, 0))
    private val afternoon = event("Dancing", LocalDateTime(2026, 11, 12, 16, 0), LocalDateTime(2026, 11, 12, 17, 0))
    private val allDay = event("Birthday", LocalDateTime(2026, 11, 12, 0, 0), LocalDateTime(2026, 11, 12, 23, 59))

    @Test
    fun `the next event is the first not yet ended - timed before all day`() {
        val model = model(listOf(allDay, morning, afternoon), now = LocalDateTime(2026, 11, 12, 12, 0))
        assertEquals("Dancing", model.nextEvent?.title)
    }

    @Test
    fun `an all-day event is next when no timed one is left`() {
        val model = model(listOf(allDay, morning), now = LocalDateTime(2026, 11, 12, 12, 0))
        assertEquals("Birthday", model.nextEvent?.title)
    }

    @Test
    fun `nothing more today is told apart from nothing scheduled`() {
        val done = model(listOf(morning), now = LocalDateTime(2026, 11, 12, 20, 0))
        assertNull(done.nextEvent)
        assertTrue(done.hasEvents)
        assertFalse(model(emptyList(), now = LocalDateTime(2026, 11, 12, 20, 0)).hasEvents)
    }

    @Test
    fun `children apart are not named together`() {
        val agenda = TodayAgenda(today, emptyList(), "mom")
        val now = LocalDateTime(2026, 11, 12, 8, 0)
        assertEquals(listOf("Emma", "Leo"), TodayModel.of(agenda, listOf("Emma", "Leo"), false, now).childNames)
        assertEquals(emptyList(), TodayModel.of(agenda, listOf("Emma", "Leo"), true, now).childNames)
    }

    @Test
    fun `the arrows step a week in Week view and a day in Day view`() {
        assertEquals(LocalDate(2026, 11, 19), steppedDay(CalendarViewMode.WEEK, today, 1))
        assertEquals(LocalDate(2026, 11, 11), steppedDay(CalendarViewMode.DAY, today, -1))
    }

    private fun model(events: List<Event>, now: LocalDateTime) =
        TodayModel.of(TodayAgenda(today, events, "mom"), listOf("Emma"), false, now)

    private fun event(title: String, start: LocalDateTime, end: LocalDateTime) = Event(
        id = title,
        title = title,
        startDateTime = start,
        endDateTime = end,
        eventType = "general",
        parentOwner = "mom",
        createdAt = start,
        updatedAt = start
    )
}
