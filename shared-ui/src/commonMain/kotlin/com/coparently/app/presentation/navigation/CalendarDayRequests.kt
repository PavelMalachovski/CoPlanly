package com.coparently.app.presentation.navigation

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.getAndUpdate
import kotlinx.datetime.LocalDate

/**
 * A request, from outside the calendar, to open its Day view on a date — Home's week tiles
 * (redesign, November 2026, variant A).
 *
 * Held until the Calendar screen [consume]s it, rather than numbered against a count the screen
 * remembers: the screen may never have been composed in this process when the request is made,
 * and a count seeded from the first composition would take the pending request as already answered.
 */
object CalendarDayRequests {

    private val _pending = MutableStateFlow<LocalDate?>(null)

    /** The day waiting to be opened, or null when nothing waits. */
    val pending: StateFlow<LocalDate?> = _pending.asStateFlow()

    /** Asks the calendar to show [date] in Day view; a newer request replaces one not yet opened. */
    fun open(date: LocalDate) {
        _pending.value = date
    }

    /** The day waiting to be opened, once: it is cleared as it is returned. */
    fun consume(): LocalDate? = _pending.getAndUpdate { null }
}
