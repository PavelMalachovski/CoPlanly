package com.coparently.app.presentation.calendar

import com.coparently.app.domain.model.Event
import com.coparently.app.domain.time.plus
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import kotlin.test.Test
import kotlin.test.assertEquals

/** Day and Week view open where the day's events are, not at the current hour alone (second audit). */
class OpeningHourTest {

    private val today = LocalDate(2026, 10, 24)
    private val fourPm = LocalTime(16, 0)

    private fun event(start: LocalDateTime, end: LocalDateTime? = start.plus(1, DateTimeUnit.HOUR)) = Event(
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
        val pickup = event(LocalDateTime(today, LocalTime(15, 30)))

        assertEquals(14, OpeningHour.of(listOf(pickup), listOf(today), today, fourPm))
    }

    @Test
    fun `today keeps the current hour in view when every event is later`() {
        val dinner = event(LocalDateTime(today, LocalTime(19, 0)))

        assertEquals(15, OpeningHour.of(listOf(dinner), listOf(today), today, fourPm))
    }

    @Test
    fun `another day opens an hour before its first event whatever the time now`() {
        val tomorrow = today.plus(1, DateTimeUnit.DAY)
        val school = event(LocalDateTime(tomorrow, LocalTime(8, 0)))

        assertEquals(7, OpeningHour.of(listOf(school), listOf(tomorrow), today, fourPm))
    }

    @Test
    fun `a week opens at the earliest event of its days and ignores days it does not show`() {
        val week = (0..6).map { today.minus(5, DateTimeUnit.DAY).plus(it, DateTimeUnit.DAY) }
        val early = event(LocalDateTime(today.minus(3, DateTimeUnit.DAY), LocalTime(9, 0)))
        val outside = event(LocalDateTime(today.plus(10, DateTimeUnit.DAY), LocalTime(6, 0)))

        assertEquals(8, OpeningHour.of(listOf(early, outside), week, today, fourPm))
    }

    @Test
    fun `an all-day event does not pull the view to midnight`() {
        val tomorrow = today.plus(1, DateTimeUnit.DAY)
        val birthday = event(LocalDateTime(tomorrow, LocalTime(0, 0)), LocalDateTime(tomorrow, LocalTime(23, 59)))

        assertEquals(15, OpeningHour.of(listOf(birthday), listOf(tomorrow), today, fourPm))
    }

    @Test
    fun `no events opens an hour before now as it always did`() {
        assertEquals(15, OpeningHour.of(emptyList(), listOf(today), today, fourPm))
    }
}
