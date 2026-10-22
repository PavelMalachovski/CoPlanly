package com.coparently.app.presentation.calendar

import java.time.LocalDate
import java.time.temporal.ChronoUnit

/**
 * The days picked on the month grid for one day-swap offer (21 October 2026, Zuzana's test).
 *
 * **Any days, not a run.** A parent picks their own days to give and the co-parent's days to take
 * in the same offer — each day goes to the parent who does not have it (`CalendarScreen`'s
 * `toParentFor`), so one offer can give and take — and a tap on a picked day, the middle one of
 * three included, unpicks that day alone. The selection used to be one consecutive run that a tap
 * could only grow.
 *
 * Capped at [MAX_DAYS]: an offer is written one day at a time (Firestore Rules can validate a diff
 * naming only one date), so an unbounded selection is an unbounded number of writes on one tap.
 */
internal object SwapSelection {

    /** Most days a single offer may cover, so one gesture cannot commit a whole term. */
    const val MAX_DAYS = 14

    /**
     * The selection after a tap on [date]: the day leaves it if it was picked, and joins it
     * otherwise — unless the selection is already [MAX_DAYS] long, when the tap changes nothing.
     */
    fun toggled(selection: Set<LocalDate>, date: LocalDate): Set<LocalDate> = when {
        date in selection -> selection - date
        selection.size >= MAX_DAYS -> selection
        else -> selection + date
    }

    /**
     * The selection while a finger that long-pressed on [anchor] is over [date]: what was picked
     * before the press ([base]) plus every day from the anchor to the finger. A finger that
     * overshoots and comes back shortens the run again, which only the anchor can say. Over
     * [MAX_DAYS] the run is trimmed from the end away from the anchor, so the day the gesture
     * started on never leaves.
     */
    fun dragged(base: Set<LocalDate>, anchor: LocalDate, date: LocalDate): Set<LocalDate> {
        val step = if (date.isBefore(anchor)) -1L else 1L
        val length = ChronoUnit.DAYS.between(anchor, date).let { if (it < 0) -it else it } + 1
        val run = generateSequence(anchor) { it.plusDays(step) }.take(length.toInt())
        val result = base.toMutableSet()
        for (day in run) {
            if (result.size >= MAX_DAYS) break
            result += day
        }
        return result
    }
}
