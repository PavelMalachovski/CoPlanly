package com.coparently.app.domain.platform.lzma

/**
 * xz-java's `LZMAEncoder` (org.tukaani:xz 1.10), the paths raw LZMA1 reaches: codes the symbols a
 * subclass chooses ([nextSymbol]), the end marker, and the prices the choice is made by.
 */
internal abstract class LzmaEncoder(
    /** Where the bits go. */
    val rc: RangeEncoder,
    /** The window and its match finder. */
    val lz: LzWindow,
    lc: Int,
    lp: Int,
    pb: Int,
    dictSize: Int
) : LzmaCoder(pb) {
    /** The nice length: a match this long is taken at once. */
    val niceLen = lz.niceLen

    /** Codes literals. */
    val literalEncoder = LiteralEncoder(lc, lp, this)

    /** Codes the lengths of new-distance matches. */
    val matchLenEncoder = LengthEncoder(rc, pb, niceLen)

    /** Codes the lengths of repeated-distance matches. */
    val repLenEncoder = LengthEncoder(rc, pb, niceLen)

    private val distancePrices = DistancePrices(this, dictSize)

    /** What [nextSymbol] chose: -1 a literal, below [REPS] a repeated distance, else a distance plus [REPS]. */
    var back = 0

    /** Positions the match finder is ahead of the next symbol to code, less one. */
    var readAhead = -1
        private set

    /** Chooses the next symbol, sets [back] and answers its length. */
    abstract fun nextSymbol(): Int

    /** Codes every symbol the window has enough input for. */
    fun encodeForLzma1() {
        if (!lz.isStarted && !encodeInit()) return
        var more = true
        while (more) more = encodeSymbol()
    }

    /** Codes the end marker: a match at distance 0xFFFFFFFF. */
    fun encodeEndMarker() {
        val posState = (lz.pos - readAhead) and posMask
        rc.encodeBit(isMatch[state.value], posState, 1)
        rc.encodeBit(isRep, state.value, 0)
        encodeMatch(-1, MATCH_LEN_MIN, posState)
    }

    private fun encodeInit(): Boolean {
        if (!lz.hasEnoughData(0)) return false
        skip(1)
        rc.encodeBit(isMatch[state.value], 0, 0)
        literalEncoder.encodeInit()
        --readAhead
        return true
    }

    private fun encodeSymbol(): Boolean {
        if (!lz.hasEnoughData(readAhead + 1)) return false
        val len = nextSymbol()
        val posState = (lz.pos - readAhead) and posMask
        if (back == -1) {
            rc.encodeBit(isMatch[state.value], posState, 0)
            literalEncoder.encode()
        } else {
            rc.encodeBit(isMatch[state.value], posState, 1)
            if (back < REPS) {
                rc.encodeBit(isRep, state.value, 1)
                encodeRepMatch(back, len, posState)
            } else {
                rc.encodeBit(isRep, state.value, 0)
                encodeMatch(back - REPS, len, posState)
            }
        }
        readAhead -= len
        return true
    }

    private fun encodeMatch(dist: Int, len: Int, posState: Int) {
        state.updateMatch()
        matchLenEncoder.encode(len, posState)
        val slot = distSlot(dist)
        rc.encodeBitTree(distSlots[distState(len)], slot)
        var usedAlign = false
        if (slot >= DIST_MODEL_START) {
            val footerBits = (slot ushr 1) - 1
            val base = (2 or (slot and 1)) shl footerBits
            val distReduced = dist - base
            if (slot < DIST_MODEL_END) {
                rc.encodeReverseBitTree(distSpecial[slot - DIST_MODEL_START], distReduced)
            } else {
                rc.encodeDirectBits(distReduced ushr ALIGN_BITS, footerBits - ALIGN_BITS)
                rc.encodeReverseBitTree(distAlign, distReduced and ALIGN_MASK)
                usedAlign = true
            }
        }
        reps.copyInto(reps, 1, 0, REPS - 1)
        reps[0] = dist
        distancePrices.matchEncoded(usedAlign)
    }

    private fun encodeRepMatch(rep: Int, len: Int, posState: Int) {
        if (rep == 0) {
            rc.encodeBit(isRep0, state.value, 0)
            rc.encodeBit(isRep0Long[state.value], posState, if (len == 1) 0 else 1)
        } else {
            val dist = reps[rep]
            rc.encodeBit(isRep0, state.value, 1)
            if (rep == 1) {
                rc.encodeBit(isRep1, state.value, 0)
            } else {
                rc.encodeBit(isRep1, state.value, 1)
                rc.encodeBit(isRep2, state.value, rep - 2)
                if (rep == REPS - 1) reps[REPS - 1] = reps[2]
                reps[2] = reps[1]
            }
            reps[1] = reps[0]
            reps[0] = dist
        }
        if (len == 1) {
            state.updateShortRep()
        } else {
            repLenEncoder.encode(len, posState)
            state.updateLongRep()
        }
    }

    /** The matches at the next position, one more byte read ahead. */
    fun getMatches(): Matches {
        ++readAhead
        return lz.getMatches()
    }

    /** Reads [len] more bytes ahead without coding them. */
    fun skip(len: Int) {
        readAhead += len
        lz.skip(len)
    }

    /** The price of saying "a match" in [state] at [posState]. */
    fun anyMatchPrice(state: LzmaState, posState: Int): Int = Prices.bit(isMatch[state.value][posState], 1)

    /** [anyMatchPrice] plus saying "a new distance". */
    fun normalMatchPrice(anyMatchPrice: Int, state: LzmaState): Int =
        anyMatchPrice + Prices.bit(isRep[state.value], 0)

    /** [anyMatchPrice] plus saying "a repeated distance". */
    fun anyRepPrice(anyMatchPrice: Int, state: LzmaState): Int = anyMatchPrice + Prices.bit(isRep[state.value], 1)

    /** [anyRepPrice] plus saying "one byte at the newest distance". */
    fun shortRepPrice(anyRepPrice: Int, state: LzmaState, posState: Int): Int =
        anyRepPrice + Prices.bit(isRep0[state.value], 0) + Prices.bit(isRep0Long[state.value][posState], 0)

    /** [anyRepPrice] plus naming repeated distance [rep] for a match longer than one byte. */
    fun longRepPrice(anyRepPrice: Int, rep: Int, state: LzmaState, posState: Int): Int {
        val s = state.value
        return anyRepPrice + if (rep == 0) {
            Prices.bit(isRep0[s], 0) + Prices.bit(isRep0Long[s][posState], 1)
        } else if (rep == 1) {
            Prices.bit(isRep0[s], 1) + Prices.bit(isRep1[s], 0)
        } else {
            Prices.bit(isRep0[s], 1) + Prices.bit(isRep1[s], 1) + Prices.bit(isRep2[s], rep - 2)
        }
    }

    /** The whole price of a match of [len] at repeated distance [rep]. */
    fun longRepAndLenPrice(rep: Int, len: Int, state: LzmaState, posState: Int): Int {
        val anyMatchPrice = anyMatchPrice(state, posState)
        val anyRepPrice = anyRepPrice(anyMatchPrice, state)
        return longRepPrice(anyRepPrice, rep, state, posState) + repLenEncoder.getPrice(len, posState)
    }

    /** [normalMatchPrice] plus the length [len] and the distance [dist]. */
    fun matchAndLenPrice(normalMatchPrice: Int, dist: Int, len: Int, posState: Int): Int =
        normalMatchPrice + matchLenEncoder.getPrice(len, posState) + distancePrices.of(dist, len)

    /** Refreshes the price tables that are due. */
    fun updatePrices() {
        distancePrices.updateIfDue()
        matchLenEncoder.updatePrices()
        repLenEncoder.updatePrices()
    }
}
