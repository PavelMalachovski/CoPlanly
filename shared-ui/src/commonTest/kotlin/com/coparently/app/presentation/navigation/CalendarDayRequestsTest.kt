package com.coparently.app.presentation.navigation

import kotlinx.datetime.LocalDate
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** A day opened from Home waits for the Calendar, is opened once, and the newest one wins. */
class CalendarDayRequestsTest {

    @AfterTest
    fun clear() {
        CalendarDayRequests.consume()
    }

    @Test
    fun `a request made before the calendar exists is still there to open`() {
        CalendarDayRequests.open(LocalDate(2026, 11, 16))

        assertEquals(LocalDate(2026, 11, 16), CalendarDayRequests.pending.value)
        assertEquals(LocalDate(2026, 11, 16), CalendarDayRequests.consume())
    }

    @Test
    fun `a request is opened once`() {
        CalendarDayRequests.open(LocalDate(2026, 11, 16))
        CalendarDayRequests.consume()

        assertNull(CalendarDayRequests.consume())
        assertNull(CalendarDayRequests.pending.value)
    }

    @Test
    fun `a newer request replaces one not yet opened`() {
        CalendarDayRequests.open(LocalDate(2026, 11, 16))
        CalendarDayRequests.open(LocalDate(2026, 11, 18))

        assertEquals(LocalDate(2026, 11, 18), CalendarDayRequests.consume())
    }
}
