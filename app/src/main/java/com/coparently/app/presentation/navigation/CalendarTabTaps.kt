package com.coparently.app.presentation.navigation

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * Every tap on the Calendar tab, counted (tester feedback, 21 October 2026: "when I press Calendar
 * it should take me back to the month view").
 *
 * The tab keeps its state between visits — `navigateToTab` restores it — so a parent who left the
 * calendar in Day view came back to Day view. The bar and the rail count each tap here, the one
 * made on the tab already on screen included, and `CalendarScreen` answers a count it has not seen
 * yet by showing this month in Month view. A count rather than an event, so a tap made before the
 * screen is composed is not lost: the screen compares it with the last count it answered, which it
 * keeps in its saved state.
 */
object CalendarTabTaps {
    private val _count = MutableStateFlow(0)

    /** How many times the Calendar tab has been tapped in this process. */
    val count: StateFlow<Int> = _count.asStateFlow()

    /** Records one tap. */
    fun tap() {
        _count.update { it + 1 }
    }
}
