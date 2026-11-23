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
 * An accepted proposal changes only the future: the days before [effectiveFrom] keep the schedule
 * agreed now as history (`CustodyHistory`, CLAUDE.md item 30), so in the proposed month they are
 * drawn in the current pattern and never ringed. Pass `CustodyHistory.effectiveFrom` — the later of
 * the proposal's start and the reader's today, the date an accept today would use.
 *
 * Pure, so which days change is tested without a composition. No count of days per parent is
 * derived here or anywhere from it: no score between the parents.
 *
 * @param month The month shown.
 * @param proposed Whose day a date is under the proposal.
 * @param current Whose day a date is now.
 * @param showProposed Whether the month is drawn in the proposed pattern (true) or the current one.
 * @param effectiveFrom The first day the proposal would decide if it were accepted now.
 */
fun scheduleChangeMonth(
    month: YearMonth,
    proposed: (LocalDate) -> String?,
    current: (LocalDate) -> String?,
    showProposed: Boolean,
    effectiveFrom: LocalDate
): MiniMonthModel {
    val dates = MiniMonthGrid.weeks(month).flatten()
    val changed = if (showProposed) changedDays(month, proposed, current, effectiveFrom) else emptyMap()
    val shown: (LocalDate) -> String? = if (showProposed) {
        { date -> if (date < effectiveFrom) current(date) else proposed(date) }
    } else {
        current
    }
    return MiniMonthModel(
        month = month,
        custody = dates.mapNotNull { date -> shown(date)?.let { date to it } }.toMap(),
        dots = emptyMap(),
        marks = changed.mapValues { MiniMonthMark.CUSTODY_CHANGE },
        markSlots = changed
    )
}

/**
 * The days of [month] from [effectiveFrom] on whose parent the proposal changes, each with the slot
 * it would go to. A day before [effectiveFrom] keeps the current schedule, so it never changes; a
 * day the proposal leaves to nobody is not a change anybody receives, so it is left out.
 */
fun changedDays(
    month: YearMonth,
    proposed: (LocalDate) -> String?,
    current: (LocalDate) -> String?,
    effectiveFrom: LocalDate
): Map<LocalDate, String> =
    MiniMonthGrid.weeks(month).flatten()
        .filter { it.yearMonth == month && it >= effectiveFrom }
        .mapNotNull { date ->
            val to = proposed(date)
            if (to != null && to != current(date)) date to to else null
        }
        .toMap()
