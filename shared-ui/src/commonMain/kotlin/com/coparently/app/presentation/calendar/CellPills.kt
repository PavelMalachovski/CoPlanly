package com.coparently.app.presentation.calendar

import com.coparently.app.domain.model.Event

/** At most this many of a day's events are drawn as pills in a month cell (calendar K1). */
internal const val MAX_CELL_PILLS = 2

/** A pill's title takes at most this many lines (calendar K1). */
internal const val MAX_PILL_LINES = 2

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
 * Two pills at most since calendar K1 (November 2026), where the grid took the screen; how many a
 * cell actually draws is what fits ([pillLines]), so a short cell draws fewer, never a title cut in
 * half. The titles are one tap away in Day view.
 */
internal fun pillsFor(events: List<Event>, max: Int = MAX_CELL_PILLS): CellPills {
    val shown = events.take(max.coerceAtLeast(0))
    return CellPills(shown = shown, more = events.size - shown.size)
}

/**
 * How many lines each of a cell's pills may take, for at most [wanted] pills in a space [height]
 * tall, where a line is [lineHeight] and two stacked pills are [gap] apart; the list holds one
 * entry per pill that fits, so its size is how many pills the cell draws. Pure, so the fit rule is
 * tested without a composition.
 *
 * A pill is drawn only where at least its first line fits. The lines left are shared out from the
 * first pill on, each pill taking at most [MAX_PILL_LINES]: in five lines two pills take two each,
 * in three the first takes two and the second one, and in one line a single pill takes it. An empty
 * list means no pill fits, and the cell draws its bar instead.
 *
 * @param wanted How many pills the day would draw ([pillsFor]'s count, at most [MAX_CELL_PILLS])
 * @param height The space under the day number, in any unit [lineHeight] and [gap] share
 */
internal fun pillLines(wanted: Int, height: Float, lineHeight: Float, gap: Float): List<Int> {
    if (lineHeight <= 0f) return emptyList()
    for (pills in wanted.coerceAtMost(MAX_CELL_PILLS) downTo 1) {
        val lines = ((height - gap * (pills - 1)) / lineHeight).toInt()
        if (lines >= pills) {
            val budget = lines.coerceAtMost(pills * MAX_PILL_LINES)
            return List(pills) { index -> budget / pills + if (index < budget % pills) 1 else 0 }
        }
    }
    return emptyList()
}
