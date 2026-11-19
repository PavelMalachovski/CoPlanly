package com.coparently.app.domain.platform.lzma

private const val INFINITY_PRICE = 1 shl 30

/** One position of xz-java's optimal parse (`Optimum`): the cheapest way found to reach it. */
internal class Optimum {
    /** The coder state on arrival. */
    val state = LzmaState()

    /** The repeated distances on arrival. */
    val reps = IntArray(REPS)

    /** The price of arriving. */
    var price = 0

    /** The position arrived from. */
    var optPrev = 0

    /** The symbol arrived by. */
    var backPrev = 0

    /** Whether a literal came just before. */
    var prev1IsLiteral = false

    /** Whether a match came before that literal. */
    var hasPrev2 = false

    /** Where that match started. */
    var optPrev2 = 0

    /** That match's symbol. */
    var backPrev2 = 0

    /** Not reached yet. */
    fun reset() {
        price = INFINITY_PRICE
    }

    /** Reached from [optCur] by one symbol [back]. */
    fun set1(newPrice: Int, optCur: Int, back: Int) {
        price = newPrice
        optPrev = optCur
        backPrev = back
        prev1IsLiteral = false
    }

    /** Reached from [optCur] by a literal and then the repeated match [back]. */
    fun set2(newPrice: Int, optCur: Int, back: Int) {
        price = newPrice
        optPrev = optCur + 1
        backPrev = back
        prev1IsLiteral = true
        hasPrev2 = false
    }

    /** Reached from [optCur] by the match [back2] of [len2], a literal, and the repeated match [back]. */
    fun set3(newPrice: Int, optCur: Int, back2: Int, len2: Int, back: Int) {
        price = newPrice
        optPrev = optCur + len2 + 1
        backPrev = back
        prev1IsLiteral = true
        hasPrev2 = true
        optPrev2 = optCur
        backPrev2 = back2
    }
}
