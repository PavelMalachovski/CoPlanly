package com.coparently.app.presentation.widget

import com.coparently.app.domain.custody.ContactWindow
import com.coparently.app.domain.custody.DayOverride
import com.coparently.app.domain.custody.DayOverrideStatus
import com.coparently.app.domain.model.CustodyModel
import com.coparently.app.domain.model.CustodyModelType
import com.coparently.app.domain.model.Event
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

/**
 * What the Today widget computes, before any of it is worded.
 *
 * The widget must never disagree with Home about the same day, so these pin that it answers
 * through the same resolver: a swap moves whose day it is *and* the next handover, a contact
 * window naming the day's own parent is dropped as the grid drops it, and the co-parent's private
 * event never reaches a home screen.
 */
class TodayWidgetModelTest {

    // Week on, week off from Monday 31 August 2026: slot 1 to 6 September, slot 2 to the 13th.
    private val pattern = CustodyModel(
        id = "m1",
        modelType = CustodyModelType.WEEK_ON_WEEK_OFF,
        patternDays = 14,
        momDayIndices = (0..6).toSet(),
        startDate = LocalDate.of(2026, 8, 31)
    )
    private val today: LocalDate = LocalDate.of(2026, 9, 2) // cycle day 2, slot 1's

    @Test
    fun `whose day it is and the next handover come from the pattern`() {
        val model = TodayWidgetModel.of(today, "uid-mom", emptyList(), pattern, emptyMap())

        assertTrue(model.signedIn)
        assertEquals("mom", model.dayParent)
        assertEquals(LocalDate.of(2026, 9, 7), model.handover?.date)
        assertEquals(5L, model.handover?.daysUntil)
        assertEquals("dad", model.handover?.toParent)
    }

    @Test
    fun `an accepted swap moves the handover, as it does on Home`() {
        val swap = DayOverride(
            toParent = "dad",
            requestedBy = "uid-mom",
            requestedAt = "2026-08-30T10:00:00",
            status = DayOverrideStatus.ACCEPTED,
            decidedBy = "uid-dad",
            decidedAt = "2026-08-30T11:00:00"
        )

        val model = TodayWidgetModel.of(today, "uid-mom", emptyList(), pattern, mapOf("2026-09-03" to swap))

        assertEquals(LocalDate.of(2026, 9, 3), model.handover?.date)
        assertEquals(1L, model.handover?.daysUntil)
    }

    @Test
    fun `a contact window with the other parent is listed, one naming the day's own parent is not`() {
        val withDad = ContactWindow(dayIndex = 2, LocalTime.of(15, 0), LocalTime.of(19, 0), parent = "dad")
        val withMom = ContactWindow(dayIndex = 2, LocalTime.of(9, 0), LocalTime.of(10, 0), parent = "mom")

        val model = TodayWidgetModel.of(
            today,
            "uid-mom",
            emptyList(),
            pattern.copy(contactWindows = listOf(withMom, withDad)),
            emptyMap()
        )

        assertEquals(listOf(withDad), model.contactWindows)
    }

    @Test
    fun `today's events cover the whole day, earliest first, and never the co-parent's private one`() {
        val events = listOf(
            event("evening", today.atTime(18, 0)),
            event("theirs", today.atTime(12, 0)).copy(isPrivate = true, createdByFirebaseUid = "uid-dad"),
            event("mine", today.atTime(11, 0)).copy(isPrivate = true, createdByFirebaseUid = "uid-mom"),
            event("overnight", today.minusDays(1).atTime(20, 0)).copy(endDateTime = today.atTime(8, 0)),
            event("morning", today.atTime(9, 0))
        )

        val model = TodayWidgetModel.of(today, "uid-mom", events, pattern, emptyMap())

        assertEquals(listOf("overnight", "morning", "mine", "evening"), model.events.map { it.id })
    }

    @Test
    fun `with no schedule there is no custody line and no handover, and the events still show`() {
        val dentist = event("dentist", today.atTime(9, 0))

        val model = TodayWidgetModel.of(today, "uid-mom", listOf(dentist), model = null, overrides = emptyMap())

        assertNull(model.dayParent)
        assertNull(model.handover)
        assertEquals(listOf("dentist"), model.events.map { it.id })
    }

    @Test
    fun `signed out says nothing about anybody's day`() {
        val model = TodayWidgetModel.signedOut(today)

        assertFalse(model.signedIn)
        assertNull(model.dayParent)
        assertTrue(model.events.isEmpty())
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
