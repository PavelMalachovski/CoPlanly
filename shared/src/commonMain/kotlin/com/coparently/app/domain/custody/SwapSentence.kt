package com.coparently.app.domain.custody

import com.coparently.app.domain.model.ChildInfo
import kotlinx.datetime.LocalDate

/**
 * Which sentence a day swap is phrased in, as the parent being asked reads it (meeting of 25
 * November 2026, item 3): the days that would come to "you", the days that would go to the sender,
 * or both.
 */
enum class SwapSentenceShape {
    /** Days each way: "Lenička will be with you on Fri 13 and with Pavel on Fri 20". */
    BOTH,

    /** Days to the reader alone: "Lenička will be with you on Fri 13". */
    GIVES,

    /** Days to the sender alone: "Lenička will be with Pavel on Fri 20". */
    ASKS
}

/**
 * A day swap phrased by who has the child on which days, rather than by dates and arrows: the
 * tester's "who has the child when" (meeting of 25 November 2026, item 3). Home's swap pop-up, the
 * inbox card and the push all lead with it.
 *
 * Pure: the screens word it from their own strings, and the push from `:push-wording`'s.
 *
 * @property children The children's first names, already filtered and in display order
 *   ([swapChildren]); empty when the family has none the swap moves, and the sentence then names
 *   the days alone.
 * @property toYou The days that would come to the parent being asked, soonest first.
 * @property toSender The days that would go to the sender, soonest first.
 */
data class SwapSentence(
    val children: List<String>,
    val toYou: List<LocalDate>,
    val toSender: List<LocalDate>
) {
    init {
        require(toYou.isNotEmpty() || toSender.isNotEmpty()) { "A swap sentence names at least one day" }
    }

    /** Which way the days go: both ways, to the reader alone, or to the sender alone. */
    val shape: SwapSentenceShape
        get() = when {
            toYou.isNotEmpty() && toSender.isNotEmpty() -> SwapSentenceShape.BOTH
            toYou.isNotEmpty() -> SwapSentenceShape.GIVES
            else -> SwapSentenceShape.ASKS
        }

    /** Builders for [SwapSentence]. */
    companion object {
        /**
         * The sentence of an offer as the parent being asked reads it: the offerer's days that
         * would come to them ([SwapSides.toOther]) and theirs that would go to the offerer
         * ([SwapSides.toOfferer]); null when the offer names no day.
         */
        fun of(sides: SwapSides, children: List<String>): SwapSentence? =
            if (sides.isEmpty) null else SwapSentence(children, sides.toOther, sides.toOfferer)
    }
}

/**
 * The children a day swap moves, by first name, in [children]'s order: every child but one with a
 * schedule of their own ([ChildScheduleOverride], FAM-4), which a swap does not move (CLAUDE.md
 * item 33). A first name is the name's first word; a child with no name is left out. Pets are never
 * passed in: a swap is about the children.
 *
 * @param children The family on screen's children, in display order.
 * @param overrides The children's own schedules from the family's custody model.
 */
fun swapChildren(children: List<ChildInfo>, overrides: List<ChildScheduleOverride>): List<String> {
    val ownSchedule = overrides.mapTo(mutableSetOf()) { it.childId }
    return children
        .filter { it.id !in ownSchedule }
        .mapNotNull { child -> firstNameOf(child.childName) }
}

/** The first word of [name], or null when it has none. */
private fun firstNameOf(name: String): String? =
    name.trim().split(WHITESPACE).firstOrNull()?.takeIf { it.isNotEmpty() }

private val WHITESPACE = Regex("\\s+")

/** The longest `childNames` a swap push may carry; `firestore.rules` bounds the field at the same. */
const val SWAP_CHILD_NAMES_MAX_LENGTH = 200

/**
 * The children's first names as a swap offer's push carries them (`childNames`): each trimmed,
 * blank ones dropped, joined by a line break, and cut to [SWAP_CHILD_NAMES_MAX_LENGTH] characters
 * on a name boundary only — never inside a name, so never inside a surrogate pair: a name that would
 * cross the bound is left out with every name after it, and a name longer than the bound on its own
 * is left out. Null when no name is left, and the push then carries no field and is worded as it
 * always was.
 */
fun swapChildNamesField(names: List<String>): String? {
    val kept = mutableListOf<String>()
    var length = 0
    for (name in names.map { it.trim() }.filter { it.isNotEmpty() && it.length <= SWAP_CHILD_NAMES_MAX_LENGTH }) {
        val added = if (kept.isEmpty()) name.length else length + 1 + name.length
        if (added > SWAP_CHILD_NAMES_MAX_LENGTH) break
        kept += name
        length = added
    }
    return kept.takeIf { it.isNotEmpty() }?.joinToString("\n")
}
