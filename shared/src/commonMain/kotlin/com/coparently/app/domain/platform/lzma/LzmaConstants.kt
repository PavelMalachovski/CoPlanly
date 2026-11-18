package com.coparently.app.domain.platform.lzma

// The constants of xz-java's `LZMACoder` and `LengthCoder` (org.tukaani:xz 1.10), under the same
// names. The port of that library's encoder in this package reads them; see [Lzma1Encoder].

/** Positions a probability table is split by: `1 shl pb` at most, pb being at most 4. */
internal const val POS_STATES_MAX = 1 shl 4

/** The shortest match LZMA encodes. */
internal const val MATCH_LEN_MIN = 2

/** Lengths the low tree of a length coder holds. */
internal const val LOW_SYMBOLS = 1 shl 3

/** Lengths the middle trees of a length coder hold. */
internal const val MID_SYMBOLS = 1 shl 3

/** Lengths the high tree of a length coder holds. */
internal const val HIGH_SYMBOLS = 1 shl 8

/** The longest match LZMA encodes: 273. */
internal const val MATCH_LEN_MAX = MATCH_LEN_MIN + LOW_SYMBOLS + MID_SYMBOLS + HIGH_SYMBOLS - 1

/** Distance states, chosen by the match length. */
internal const val DIST_STATES = 4

/** Distance slots per distance state. */
internal const val DIST_SLOTS = 1 shl 6

/** The first distance slot with footer bits. */
internal const val DIST_MODEL_START = 4

/** The first distance slot whose footer is direct bits and an aligned tail. */
internal const val DIST_MODEL_END = 14

/** Distances priced one by one: those below this. */
internal const val FULL_DISTANCES = 1 shl (DIST_MODEL_END / 2)

/** Bits of the aligned tail of a long distance. */
internal const val ALIGN_BITS = 4

/** Values of the aligned tail. */
internal const val ALIGN_SIZE = 1 shl ALIGN_BITS

/** Mask of the aligned tail. */
internal const val ALIGN_MASK = ALIGN_SIZE - 1

/** Repeated distances remembered. */
internal const val REPS = 4

/** Coder states. */
internal const val STATES = 12

/** Bits of a byte. */
internal const val BYTE_BITS = 8

/** Mask of a byte. */
internal const val BYTE_MASK = 0xFF

/** The distance slot of [dist], as xz-java's `LZMAEncoder.getDistSlot`. */
internal fun distSlot(dist: Int): Int {
    if (dist <= DIST_MODEL_START && dist >= 0) return dist
    val i = Int.SIZE_BITS - 1 - dist.countLeadingZeroBits()
    return (i shl 1) + ((dist ushr (i - 1)) and 1)
}

/** The distance state a match of [len] is coded in. */
internal fun distState(len: Int): Int =
    if (len < DIST_STATES + MATCH_LEN_MIN) len - MATCH_LEN_MIN else DIST_STATES - 1
