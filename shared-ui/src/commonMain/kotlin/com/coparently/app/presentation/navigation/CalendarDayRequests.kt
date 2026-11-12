package com.coparently.app.presentation.navigation

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.datetime.LocalDate

/**
 * A request, from outside the calendar, to open its Day view on a date — Home's week tiles
 * (redesign, November 2026, variant A).
 *
 * Numbered for the reason [CalendarTabTaps] counts: the Calendar screen may not be composed when
 * the request is made, so it compares the newest request with the last one it answered, which it
 * keeps in its saved state, rather than waiting for an event it could miss.
 */
object CalendarDayRequests {

    /**
     * One request.
     *
     * @property id Increases with every request in this process
     * @property date The day to open
     */
    data class DayRequest(val id: Int, val date: LocalDate)

    private val _request = MutableStateFlow<DayRequest?>(null)

    /** The newest request, or null when none was made in this process. */
    val request: StateFlow<DayRequest?> = _request.asStateFlow()

    /** Asks the calendar to show [date] in Day view. */
    fun open(date: LocalDate) {
        _request.update { DayRequest(id = (it?.id ?: 0) + 1, date = date) }
    }
}
