package com.coparently.app.presentation.changerequests

import com.coparently.app.presentation.calendar.MiniMonthGrid
import com.coparently.app.presentation.calendar.MiniMonthMark
import com.coparently.app.presentation.calendar.MiniMonthModel
import kotlinx.datetime.LocalDate
import kotlinx.datetime.YearMonth
import kotlinx.datetime.yearMonth

/**
 * One month of a proposed custody schedule as [SchedulePreviewWithChanges] draws it (November 2026,
 * board HomeScheduleDialog): the month in the proposed pattern or in the one agreed now, and — in
 * the proposed one — each day of the month whose parent would change, ringed in the colour of the
 * parent it would go to. Borrowed days of the neighbouring months carry the band, never a ring.
 *
 * Pure, so which days change is tested without a composition. No count of days per parent is
 * derived here or anywhere from it: no score between the parents.
 *
 * @param month The month shown.
 * @param proposed Whose day a date is under the proposal.
 * @param current Whose day a date is now.
 * @param showProposed Whether the month is drawn in the proposed pattern (true) or the current one.
 */
fun scheduleChangeMonth(
    month: YearMonth,
    proposed: (LocalDate) -> String?,
    current: (LocalDate) -> String?,
    showProposed: Boolean
): MiniMonthModel {
    val dates = MiniMonthGrid.weeks(month).flatten()
    val changed = if (showProposed) changedDays(month, proposed, current) else emptyMap()
    val shown = if (showProposed) proposed else current
    return MiniMonthModel(
        month = month,
        custody = dates.mapNotNull { date -> shown(date)?.let { date to it } }.toMap(),
        dots = emptyMap(),
        marks = changed.mapValues { MiniMonthMark.CUSTODY_CHANGE },
        markSlots = changed
    )
}

/**
 * The days of [month] whose parent the proposal changes, each with the slot it would go to. A day
 * the proposal leaves to nobody is not a change anybody receives, so it is left out.
 */
fun changedDays(
    month: YearMonth,
    proposed: (LocalDate) -> String?,
    current: (LocalDate) -> String?
): Map<LocalDate, String> =
    MiniMonthGrid.weeks(month).flatten()
        .filter { it.yearMonth == month }
        .mapNotNull { date ->
            val to = proposed(date)
            if (to != null && to != current(date)) date to to else null
        }
        .toMap()
