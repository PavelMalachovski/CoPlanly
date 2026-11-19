package com.coparently.app.domain.platform.lzma

private const val PRICE_UPDATE_INTERVAL = 32

/**
 * xz-java's `LZMAEncoder.LengthEncoder` (org.tukaani:xz 1.10): codes a match length and keeps the
 * prices of the lengths up to the nice length, refreshed every 32 lengths coded at a position.
 */
internal class LengthEncoder(private val rc: RangeEncoder, pb: Int, niceLen: Int) : LengthCoder() {
    private val counters = IntArray(1 shl pb)
    private val prices = Array(1 shl pb) {
        IntArray(maxOf(niceLen - MATCH_LEN_MIN + 1, LOW_SYMBOLS + MID_SYMBOLS))
    }

    /** Codes [length] at [posState]. */
    fun encode(length: Int, posState: Int) {
        var len = length - MATCH_LEN_MIN
        if (len < LOW_SYMBOLS) {
            rc.encodeBit(choice, 0, 0)
            rc.encodeBitTree(low[posState], len)
        } else {
            rc.encodeBit(choice, 0, 1)
            len -= LOW_SYMBOLS
            if (len < MID_SYMBOLS) {
                rc.encodeBit(choice, 1, 0)
                rc.encodeBitTree(mid[posState], len)
            } else {
                rc.encodeBit(choice, 1, 1)
                rc.encodeBitTree(high, len - MID_SYMBOLS)
            }
        }
        --counters[posState]
    }

    /** The price of [len] at [posState], as last refreshed. */
    fun getPrice(len: Int, posState: Int): Int = prices[posState][len - MATCH_LEN_MIN]

    /** Refreshes the prices of every position whose counter ran out. */
    fun updatePrices() {
        for (posState in counters.indices) {
            if (counters[posState] <= 0) {
                counters[posState] = PRICE_UPDATE_INTERVAL
                updatePrices(posState)
            }
        }
    }

    private fun updatePrices(posState: Int) {
        val row = prices[posState]
        var choice0Price = Prices.bit(choice[0], 0)
        var i = 0
        while (i < LOW_SYMBOLS) {
            row[i] = choice0Price + Prices.bitTree(low[posState], i)
            ++i
        }
        choice0Price = Prices.bit(choice[0], 1)
        var choice1Price = Prices.bit(choice[1], 0)
        while (i < LOW_SYMBOLS + MID_SYMBOLS) {
            row[i] = choice0Price + choice1Price + Prices.bitTree(mid[posState], i - LOW_SYMBOLS)
            ++i
        }
        choice1Price = Prices.bit(choice[1], 1)
        while (i < row.size) {
            row[i] = choice0Price + choice1Price + Prices.bitTree(high, i - LOW_SYMBOLS - MID_SYMBOLS)
            ++i
        }
    }
}
