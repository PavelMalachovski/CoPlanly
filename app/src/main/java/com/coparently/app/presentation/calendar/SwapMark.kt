package com.coparently.app.presentation.calendar

import com.coparently.app.domain.custody.receiverOf
import java.time.LocalDate

/**
 * A month cell's day-swap mark (day swap design, 28 October 2026, boards SW-2 and SW-4): the day
 * would go to [toSlot], in that parent's colour and in words ("→ Lana", "→ you").
 *
 * It replaced the two grey arrows a waiting day carried, which said a swap was being discussed and
 * nothing about which way. Drawn over the `DayCellFills` layers and in-month cells only: whose day
 * it is *now* stays the wash under it, and a borrowed cell takes no swap mark (design item 10).
 *
 * @property toSlot The slot the day would go to.
 * @property pending True for a day already offered and waiting for an answer, drawn lighter and
 *   dashed on both phones; false for a day being chosen on this one.
 */
data class SwapMark(val toSlot: String, val pending: Boolean)

/**
 * The marks the month grid draws: each chosen day with a known holder goes to the other parent
 * ([receiverOf]); each waiting day to whom it was offered. A waiting day chosen again shows the
 * choice being made.
 *
 * @param selection The days chosen on this phone.
 * @param holderOf The slot that has a day now, or null when nothing answers for it.
 * @param pendingTo Each waiting day and the slot it was offered to.
 */
internal fun swapMarks(
    selection: Set<LocalDate>,
    holderOf: (LocalDate) -> String?,
    pendingTo: Map<LocalDate, String>
): Map<LocalDate, SwapMark> {
    val chosen = selection.mapNotNull { day -> receiverOf(holderOf(day))?.let { day to SwapMark(it, pending = false) } }
    return pendingTo.mapValues { (_, slot) -> SwapMark(slot, pending = true) } + chosen
}
