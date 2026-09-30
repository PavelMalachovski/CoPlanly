package com.coparently.app.presentation.event

import com.coparently.app.domain.model.Event
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * [formEndDateTime] and [endDaySpanOf]: the event form edits one date, and saving the end time
 * on that date used to cut a multi-day event to one day, refuse an overnight one and give an
 * event with no end one.
 */
class EventFormEndTest {

    private fun event(start: LocalDateTime, end: LocalDateTime?) = Event(
        id = "e1",
        title = "Weekend",
        startDateTime = start,
        endDateTime = end,
        eventType = "general",
        parentOwner = "mom",
        createdAt = start,
        updatedAt = start
    )

    /** What the form saves for [loaded] when only its title was edited. */
    private fun savedEndOf(loaded: Event): LocalDateTime? {
        val start = loaded.startDateTime
        val endTime = loaded.endDateTime?.toLocalTime() ?: start.toLocalTime().plusHours(1)
        return formEndDateTime(start.toLocalDate(), endTime, endDaySpanOf(loaded), loaded.endDateTime == null)
    }

    @Test
    fun `a same-day event ends on its start date`() {
        val loaded = event(LocalDateTime.of(2026, 7, 20, 16, 0), LocalDateTime.of(2026, 7, 20, 17, 0))
        assertEquals(0, endDaySpanOf(loaded))
        assertEquals(loaded.endDateTime, savedEndOf(loaded))
    }

    @Test
    fun `a multi-day event keeps its end date when only the title changes`() {
        val loaded = event(LocalDateTime.of(2026, 7, 17, 18, 0), LocalDateTime.of(2026, 7, 19, 18, 0))
        assertEquals(2, endDaySpanOf(loaded))
        assertEquals(loaded.endDateTime, savedEndOf(loaded))
    }

    @Test
    fun `an overnight event ends the next day, after its start`() {
        val loaded = event(LocalDateTime.of(2026, 7, 20, 22, 0), LocalDateTime.of(2026, 7, 21, 2, 0))
        assertEquals(loaded.endDateTime, savedEndOf(loaded))
    }

    @Test
    fun `moving a multi-day event's start date moves its end with it`() {
        assertEquals(
            LocalDateTime.of(2026, 8, 3, 18, 0),
            formEndDateTime(LocalDate.of(2026, 8, 1), LocalTime.of(18, 0), 2, keepNoEnd = false)
        )
    }

    @Test
    fun `an event with no end keeps none until an end time is set`() {
        val loaded = event(LocalDateTime.of(2026, 7, 20, 0, 0), null)
        assertEquals(0, endDaySpanOf(loaded))
        assertNull(savedEndOf(loaded))
        assertEquals(
            LocalDateTime.of(2026, 7, 20, 3, 0),
            formEndDateTime(LocalDate.of(2026, 7, 20), LocalTime.of(3, 0), 0, keepNoEnd = false)
        )
    }
}
