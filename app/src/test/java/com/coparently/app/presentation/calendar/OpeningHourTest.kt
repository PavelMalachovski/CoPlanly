package com.coparently.app.presentation.calendar

import com.coparently.app.domain.model.Event
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import kotlin.test.assertEquals

/** Day and Week view open where the day's events are, not at the current hour alone (second audit). */
class OpeningHourTest {

    private val today = LocalDate.of(2026, 10, 24)
    private val fourPm = LocalTime.of(16, 0)

    private fun event(start: LocalDateTime, end: LocalDateTime? = start.plusHours(1)) = Event(
        id = start.toString(),
        title = "e",
        startDateTime = start,
        endDateTime = end,
        eventType = "general",
        parentOwner = "mom",
        createdAt = start,
        updatedAt = start
    )

    @Test
    fun `today opens an hour before its first event when that is earlier than now`() {
        val pickup = event(today.atTime(15, 30))

        assertEquals(14, OpeningHour.of(listOf(pickup), listOf(today), today, fourPm))
    }

    @Test
    fun `today keeps the current hour in view when every event is later`() {
        val dinner = event(today.atTime(19, 0))

        assertEquals(15, OpeningHour.of(listOf(dinner), listOf(today), today, fourPm))
    }

    @Test
    fun `another day opens an hour before its first event, whatever the time now`() {
        val tomorrow = today.plusDays(1)
        val school = event(tomorrow.atTime(8, 0))

        assertEquals(7, OpeningHour.of(listOf(school), listOf(tomorrow), today, fourPm))
    }

    @Test
    fun `a week opens at the earliest event of its days, and ignores days it does not show`() {
        val week = (0L..6L).map { today.minusDays(5).plusDays(it) }
        val early = event(today.minusDays(3).atTime(9, 0))
        val outside = event(today.plusDays(10).atTime(6, 0))

        assertEquals(8, OpeningHour.of(listOf(early, outside), week, today, fourPm))
    }

    @Test
    fun `an all-day event does not pull the view to midnight`() {
        val tomorrow = today.plusDays(1)
        val birthday = event(tomorrow.atStartOfDay(), tomorrow.atTime(23, 59))

        assertEquals(15, OpeningHour.of(listOf(birthday), listOf(tomorrow), today, fourPm))
    }

    @Test
    fun `no events opens an hour before now, as it always did`() {
        assertEquals(15, OpeningHour.of(emptyList(), listOf(today), today, fourPm))
    }
}
