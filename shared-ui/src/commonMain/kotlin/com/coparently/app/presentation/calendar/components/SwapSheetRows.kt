package com.coparently.app.presentation.calendar.components

import com.coparently.app.domain.custody.SwapSides
import com.coparently.app.domain.custody.receiverOf
import kotlinx.datetime.LocalDate

/**
 * One line of the day swap sheet: some days, and the slot they would go to.
 *
 * @property receiverSlot The parent slot the days would go to.
 * @property dates The days, soonest first.
 */
data class SwapSheetRow(val receiverSlot: String, val dates: List<LocalDate>)

/**
 * The rows the compact day swap sheet draws for [sides]: the days this parent gives away first,
 * then the days they take, a side with no days left out. Each day goes to whoever does not hold it
 * ([receiverOf]), as everywhere else (day swap design, 28 October 2026).
 *
 * @param sides The chosen days, from this parent's side.
 * @param me The reading parent's own slot.
 */
internal fun swapSheetRows(sides: SwapSides, me: String): List<SwapSheetRow> = listOfNotNull(
    receiverOf(me)?.let { other -> SwapSheetRow(other, sides.toOther.sorted()).takeIf { sides.toOther.isNotEmpty() } },
    SwapSheetRow(me, sides.toOfferer.sorted()).takeIf { sides.toOfferer.isNotEmpty() }
)
