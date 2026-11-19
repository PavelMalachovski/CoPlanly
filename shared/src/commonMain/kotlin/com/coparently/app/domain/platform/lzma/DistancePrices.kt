package com.coparently.app.domain.platform.lzma

/**
 * The distance price tables of xz-java's `LZMAEncoder` (org.tukaani:xz 1.10): every distance below
 * [FULL_DISTANCES] priced whole, longer ones by slot and aligned tail, each table refreshed after
 * as many matches as it has entries.
 */
internal class DistancePrices(private val coder: LzmaCoder, dictSize: Int) {
    private val distSlotPricesSize = distSlot(dictSize - 1) + 1
    private val distSlotPrices = Array(DIST_STATES) { IntArray(distSlotPricesSize) }
    private val fullDistPrices = Array(DIST_STATES) { IntArray(FULL_DISTANCES) }
    private val alignPrices = IntArray(ALIGN_SIZE)
    private var distPriceCount = 0
    private var alignPriceCount = 0

    /** A match was coded; [usedAlign] when its distance had an aligned tail. */
    fun matchEncoded(usedAlign: Boolean) {
        if (usedAlign) --alignPriceCount
        --distPriceCount
    }

    /** Refreshes the tables whose count ran out. */
    fun updateIfDue() {
        if (distPriceCount <= 0) updateDistPrices()
        if (alignPriceCount <= 0) updateAlignPrices()
    }

    /** The price of [dist] (minus one) for a match of [len]. */
    fun of(dist: Int, len: Int): Int {
        val state = distState(len)
        return if (dist < FULL_DISTANCES) {
            fullDistPrices[state][dist]
        } else {
            distSlotPrices[state][distSlot(dist)] + alignPrices[dist and ALIGN_MASK]
        }
    }

    private fun updateDistPrices() {
        distPriceCount = FULL_DISTANCES
        for (distState in 0 until DIST_STATES) {
            val slotPrices = distSlotPrices[distState]
            for (slot in 0 until distSlotPricesSize) {
                slotPrices[slot] = Prices.bitTree(coder.distSlots[distState], slot)
            }
            for (slot in DIST_MODEL_END until distSlotPricesSize) {
                slotPrices[slot] += Prices.directBits((slot ushr 1) - 1 - ALIGN_BITS)
            }
            for (dist in 0 until DIST_MODEL_START) fullDistPrices[distState][dist] = slotPrices[dist]
        }
        var dist = DIST_MODEL_START
        for (slot in DIST_MODEL_START until DIST_MODEL_END) {
            val footerBits = (slot ushr 1) - 1
            val base = (2 or (slot and 1)) shl footerBits
            val probs = coder.distSpecial[slot - DIST_MODEL_START]
            repeat(probs.size) {
                val price = Prices.reverseBitTree(probs, dist - base)
                for (distState in 0 until DIST_STATES) {
                    fullDistPrices[distState][dist] = distSlotPrices[distState][slot] + price
                }
                ++dist
            }
        }
    }

    private fun updateAlignPrices() {
        alignPriceCount = ALIGN_SIZE
        for (i in 0 until ALIGN_SIZE) alignPrices[i] = Prices.reverseBitTree(coder.distAlign, i)
    }
}
