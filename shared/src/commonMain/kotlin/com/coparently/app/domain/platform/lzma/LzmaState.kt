package com.coparently.app.domain.platform.lzma

private const val LIT_STATES = 7
private const val LIT_LIT = 0
private const val SHORTREP_LIT_LIT = 3
private const val LIT_SHORTREP = 9
private const val LIT_MATCH = 7
private const val LIT_LONGREP = 8
private const val NONLIT_MATCH = 10
private const val NONLIT_REP = 11
private const val AFTER_LITERAL_STEP = 3
private const val AFTER_NONLITERAL_STEP = 6

/** The coder's state machine, as xz-java's `State`: which kinds of symbol came last. */
internal class LzmaState {

    /** The state, 0 until [STATES]. */
    var value = LIT_LIT
        private set

    /** Whether the last symbol was a literal. */
    val isLiteral: Boolean get() = value < LIT_STATES

    /** Takes [other]'s state. */
    fun set(other: LzmaState) {
        value = other.value
    }

    /** After a literal. */
    fun updateLiteral() {
        value = when {
            value <= SHORTREP_LIT_LIT -> LIT_LIT
            value <= LIT_SHORTREP -> value - AFTER_LITERAL_STEP
            else -> value - AFTER_NONLITERAL_STEP
        }
    }

    /** After a match. */
    fun updateMatch() {
        value = if (value < LIT_STATES) LIT_MATCH else NONLIT_MATCH
    }

    /** After a repeated match longer than one byte. */
    fun updateLongRep() {
        value = if (value < LIT_STATES) LIT_LONGREP else NONLIT_REP
    }

    /** After a one-byte repeated match. */
    fun updateShortRep() {
        value = if (value < LIT_STATES) LIT_SHORTREP else NONLIT_REP
    }
}
