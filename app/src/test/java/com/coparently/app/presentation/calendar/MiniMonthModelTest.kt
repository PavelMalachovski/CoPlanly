package com.coparently.app.presentation.calendar

import com.coparently.app.domain.events.EventAcceptance
import com.coparently.app.domain.model.Event
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.YearMonth
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The mini month's layout and inputs (phase 2, spec §5.3): whole weeks from Monday, the custody
 * slot and the first event of every cell, borrowed days included, and the mark on the day of the
 * event waiting for an answer.
 */
class MiniMonthModelTest {

    @Suppress("LongParameterList") // one event's fields, all defaulted
    private fun event(
        id: String,
        start: LocalDateTime,
        end: LocalDateTime? = start.plusHours(1),
        owner: String = "mom",
        acceptance: EventAcceptance = EventAcceptance.NOT_REQUIRED,
        friend: String? = null,
        recurrence: String? = null
    ) = Event(
        id = id,
        title = id,
        startDateTime = start,
        endDateTime = end,
        eventType = "other",
        parentOwner = owner,
        isRecurring = recurrence != null,
        recurrencePattern = recurrence,
        createdAt = start,
        updatedAt = start,
        acceptance = acceptance,
        friendParticipates = friend
    )

    @Test
    fun `a month starting on Friday borrows the days before it back to Monday`() {
        val weeks = MiniMonthGrid.weeks(YearMonth.of(2026, 5))

        assertEquals(5, weeks.size)
        assertEquals(LocalDate.of(2026, 4, 27), weeks.first().first())
        assertEquals(LocalDate.of(2026, 5, 31), weeks.last().last())
        assertTrue(weeks.all { it.size == 7 && it.first().dayOfWeek == DayOfWeek.MONDAY })
    }

    @Test
    fun `a February that starts on Monday is exactly four weeks`() {
        val weeks = MiniMonthGrid.weeks(YearMonth.of(2027, 2))

        assertEquals(4, weeks.size)
        assertEquals(LocalDate.of(2027, 2, 1), weeks.first().first())
        assertEquals(LocalDate.of(2027, 2, 28), weeks.last().last())
    }

    @Test
    fun `a leap February keeps its 29th`() {
        val weeks = MiniMonthGrid.weeks(YearMonth.of(2028, 2))

        assertEquals(5, weeks.size)
        assertEquals(LocalDate.of(2028, 1, 31), weeks.first().first())
        assertEquals(LocalDate.of(2028, 3, 5), weeks.last().last())
        assertTrue(LocalDate.of(2028, 2, 29) in weeks.flatten())
    }

    @Test
    fun `a month can need six weeks`() {
        val weeks = MiniMonthGrid.weeks(YearMonth.of(2026, 8))

        assertEquals(6, weeks.size)
        assertEquals(LocalDate.of(2026, 7, 27), weeks.first().first())
        assertEquals(LocalDate.of(2026, 9, 6), weeks.last().last())
    }

    @Test
    fun `custody and each day's first event are taken for every cell, borrowed days included`() {
        val overnight = event(
            "overnight",
            start = LocalDateTime.of(2026, 4, 29, 22, 0),
            end = LocalDateTime.of(2026, 4, 30, 9, 0),
            owner = "dad"
        )
        val later = event("later", LocalDateTime.of(2026, 5, 13, 9, 0), owner = "mom")
        val earlier = event("earlier", LocalDateTime.of(2026, 5, 13, 8, 0), owner = "dad", friend = "f1")

        val model = MiniMonthModel.build(
            month = YearMonth.of(2026, 5),
            custodyFor = { date -> if (date.dayOfMonth % 2 == 0) "mom" else null },
            events = listOf(later, overnight, earlier),
            marks = emptyMap()
        )

        // The grid runs 27 April to 31 May: April's 28th and 30th, then May's even days.
        assertEquals(17, model.custody.size)
        assertEquals("mom", model.custody[LocalDate.of(2026, 4, 28)])
        assertEquals("mom", model.custody[LocalDate.of(2026, 4, 30)])
        assertEquals("mom", model.custody[LocalDate.of(2026, 5, 30)])
        assertFalse(LocalDate.of(2026, 5, 31) in model.custody)
        assertFalse(LocalDate.of(2026, 6, 2) in model.custody)
        // An overnight event dots both days; on a day with two, the earlier one decides.
        assertEquals(
            mapOf(
                LocalDate.of(2026, 4, 29) to MiniMonthDot(parentOwner = "dad", friendParticipates = false),
                LocalDate.of(2026, 4, 30) to MiniMonthDot(parentOwner = "dad", friendParticipates = false),
                LocalDate.of(2026, 5, 13) to MiniMonthDot(parentOwner = "dad", friendParticipates = true)
            ),
            model.dots
        )
    }

    @Test
    fun `an awaiting event is marked on its start day and draws no dot of its own`() {
        val awaiting = event(
            "swimming",
            start = LocalDateTime.of(2026, 10, 21, 16, 0),
            end = LocalDateTime.of(2026, 10, 21, 17, 30),
            acceptance = EventAcceptance.PENDING
        )

        val model = MiniMonthModel.forAwaitingEvent(awaiting, familyEvents = listOf(awaiting), custodyFor = { null })

        assertEquals(YearMonth.of(2026, 10), model.month)
        assertEquals(mapOf(LocalDate.of(2026, 10, 21) to MiniMonthMark.NEW_EVENT), model.marks)
        assertEquals(emptyMap(), model.dots)
    }

    @Test
    fun `declined events draw no dot and a weekly event draws one on each occurrence`() {
        val awaiting = event("swimming", LocalDateTime.of(2026, 10, 21, 16, 0), acceptance = EventAcceptance.PENDING)
        val declined = event("declined", LocalDateTime.of(2026, 10, 14, 10, 0), acceptance = EventAcceptance.DECLINED)
        val weekly = event("weekly", LocalDateTime.of(2026, 10, 5, 17, 0), recurrence = "weekly")

        val model = MiniMonthModel.forAwaitingEvent(awaiting, listOf(awaiting, declined, weekly), custodyFor = { null })

        // October 2026's grid runs 28 September to 1 November, so the 2 November occurrence is out.
        assertEquals(
            setOf(
                LocalDate.of(2026, 10, 5),
                LocalDate.of(2026, 10, 12),
                LocalDate.of(2026, 10, 19),
                LocalDate.of(2026, 10, 26)
            ),
            model.dots.keys
        )
    }
}
