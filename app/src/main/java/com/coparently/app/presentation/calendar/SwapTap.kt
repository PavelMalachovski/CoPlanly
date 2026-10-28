package com.coparently.app.presentation.calendar

import java.time.LocalDate
import java.time.YearMonth

/**
 * What a tap on a month cell does (day swap design, 28 October 2026).
 *
 * While the parent is choosing days to swap, a tap only chooses — never opens Day view, which is
 * what the owner hit on a day of the next month shown at the end of the grid. Such a borrowed day
 * pages to its own month and is chosen there, where its cell can show whom it goes to (a borrowed
 * cell takes no swap mark, design item 10).
 */
internal sealed interface SwapTap {
    /** Choose or unchoose [date] on the month on screen. */
    data class Toggle(val date: LocalDate) : SwapTap

    /** Page to [month], then choose or unchoose [date] there. */
    data class PageAndToggle(val month: YearMonth, val date: LocalDate) : SwapTap

    /** Not choosing: select [date] and open Day view, as a tap always did. */
    data class OpenDay(val date: LocalDate) : SwapTap
}

/** The [SwapTap] for a tap on [date] while [shownMonth] is on screen. */
internal fun swapTapFor(date: LocalDate, shownMonth: YearMonth, selecting: Boolean): SwapTap = when {
    !selecting -> SwapTap.OpenDay(date)
    YearMonth.from(date) == shownMonth -> SwapTap.Toggle(date)
    else -> SwapTap.PageAndToggle(YearMonth.from(date), date)
}

/**
 * How a swap selection is saved with the screen, so a rotation or a process death keeps the days
 * chosen across months: ISO strings, an unreadable one dropped.
 */
internal object LocalDateSetSaver {
    /** [dates] as ISO strings. */
    fun save(dates: Set<LocalDate>): ArrayList<String> = ArrayList(dates.map(LocalDate::toString))

    /** The readable dates of [saved]. */
    fun restore(saved: List<String>): Set<LocalDate> =
        saved.mapNotNull { runCatching { LocalDate.parse(it) }.getOrNull() }.toSet()
}
