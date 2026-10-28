package com.coparently.app.domain.custody

import java.time.LocalDate

/**
 * Which way each day of a day swap goes (day swap design, 28 October 2026): the days the offering
 * parent gives to the other one, and the days they ask for.
 *
 * Decided once, here, and read by everything that shows a swap — the selection bar, the review
 * sheet, the grid's marks, the push and the answer card — so no two surfaces can word one offer two
 * ways. The owner's complaint was that nothing said which way a day went.
 *
 * @property toOther Days the offerer holds now, which would go to the other parent; soonest first.
 * @property toOfferer Days the other parent holds now, which would go to the offerer; soonest first.
 * @property unknown Days nobody is recorded for, which cannot be swapped and are counted out loud.
 */
data class SwapSides(
    val toOther: List<LocalDate>,
    val toOfferer: List<LocalDate>,
    val unknown: List<LocalDate> = emptyList()
) {
    /** Every day that can be offered, soonest first. */
    val offerable: List<LocalDate> get() = (toOther + toOfferer).sorted()

    /** True when no day can be offered. */
    val isEmpty: Boolean get() = toOther.isEmpty() && toOfferer.isEmpty()

    /** Builders for [SwapSides]. */
    companion object {
        /**
         * The sides of a selection the offering parent is still making: each day goes to whoever
         * does not hold it ([receiverOf]).
         *
         * @param dates The chosen days, in any order; duplicates collapse.
         * @param holderOf The slot that has a day now, or null when nothing answers for it.
         * @param offererSlot The offering parent's own slot.
         */
        fun of(dates: Collection<LocalDate>, holderOf: (LocalDate) -> String?, offererSlot: String): SwapSides {
            val days = dates.distinct().sorted()
            val holders = days.associateWith(holderOf)
            return SwapSides(
                toOther = days.filter { holders[it] != null && holders[it] == offererSlot },
                toOfferer = days.filter { holders[it] != null && holders[it] != offererSlot },
                unknown = days.filter { holders[it] == null }
            )
        }

        /**
         * The sides of an offer already stored: a day whose `toParent` is the offerer's own slot
         * goes to them, every other day to the other parent.
         */
        fun ofOffer(group: DaySwapGroup, offererSlot: String): SwapSides {
            val (toOfferer, toOther) = group.swaps.partition { it.override.toParent == offererSlot }
            return SwapSides(
                toOther = toOther.map { it.date }.sorted(),
                toOfferer = toOfferer.map { it.date }.sorted()
            )
        }
    }
}

/** The slot a day held by [holder] would go to in a swap, or null when [holder] is not a parent slot. */
fun receiverOf(holder: String?): String? = when (holder) {
    "mom" -> "dad"
    "dad" -> "mom"
    else -> null
}
