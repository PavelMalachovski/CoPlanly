package com.coparently.app.domain.platform.lzma

private const val DIST_SPECIAL_SIZES = 10

/**
 * The probability models of xz-java's `LZMACoder` (org.tukaani:xz 1.10), each starting at one half,
 * with the state and the four repeated distances.
 */
internal open class LzmaCoder(pb: Int) {
    /** Mask of a position's low bits that select a model. */
    val posMask = (1 shl pb) - 1

    /** The four most recent distances, newest first. */
    val reps = IntArray(REPS)

    /** Which kinds of symbol came last. */
    val state = LzmaState()

    /** Literal or match, by state and position. */
    val isMatch = Array(STATES) { probabilities(POS_STATES_MAX) }

    /** Repeated or new distance, by state. */
    val isRep = probabilities(STATES)

    /** The newest distance or another, by state. */
    val isRep0 = probabilities(STATES)

    /** The second distance or an older one, by state. */
    val isRep1 = probabilities(STATES)

    /** The third distance or the fourth, by state. */
    val isRep2 = probabilities(STATES)

    /** One byte or longer at the newest distance, by state and position. */
    val isRep0Long = Array(STATES) { probabilities(POS_STATES_MAX) }

    /** Distance slots, by distance state. */
    val distSlots = Array(DIST_STATES) { probabilities(DIST_SLOTS) }

    /** The footers of distance slots 4 to 13: 2, 2, 4, 4, … 32, 32 models. */
    val distSpecial = Array(DIST_SPECIAL_SIZES) { probabilities(2 shl (it / 2)) }

    /** The aligned tail of long distances. */
    val distAlign = probabilities(ALIGN_SIZE)
}

/** The probability models of xz-java's `LengthCoder`: a choice, then a low, middle or high tree. */
internal open class LengthCoder {
    /** Low or longer, then middle or high. */
    val choice = probabilities(2)

    /** Lengths 2 to 9, by position. */
    val low = Array(POS_STATES_MAX) { probabilities(LOW_SYMBOLS) }

    /** Lengths 10 to 17, by position. */
    val mid = Array(POS_STATES_MAX) { probabilities(MID_SYMBOLS) }

    /** Lengths 18 to 273. */
    val high = probabilities(HIGH_SYMBOLS)
}
