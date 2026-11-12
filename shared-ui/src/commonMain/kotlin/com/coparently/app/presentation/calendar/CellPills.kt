package com.coparently.app.presentation.calendar

import com.coparently.app.domain.model.Event

/**
 * What a month cell shows of its day's events: the first [max] as pills, and how many more there
 * are, which the cell writes as "+n" (November 2026 redesign).
 *
 * @property shown The events drawn as pills, in the day's order.
 * @property more How many events the cell does not draw; zero when it draws them all.
 */
internal data class CellPills(val shown: List<Event>, val more: Int)

/**
 * Splits a day's events into the pills a month cell draws and the count it leaves to "+n".
 *
 * One pill by default, whatever the cell's height: a cell that sometimes had room for two would
 * make two similar days look different, and the titles are one tap away in Day view.
 */
internal fun pillsFor(events: List<Event>, max: Int = 1): CellPills {
    val shown = events.take(max.coerceAtLeast(0))
    return CellPills(shown = shown, more = events.size - shown.size)
}
