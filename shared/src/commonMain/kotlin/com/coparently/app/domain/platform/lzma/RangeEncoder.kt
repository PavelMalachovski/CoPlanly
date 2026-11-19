package com.coparently.app.domain.platform.lzma

private const val SHIFT_BITS = 8
private const val TOP_MASK = 0xFF000000.toInt()
private const val BIT_MODEL_TOTAL_BITS = 11
private const val BIT_MODEL_TOTAL = 1 shl BIT_MODEL_TOTAL_BITS
private const val MOVE_BITS = 5
private const val MOVE_REDUCING_BITS = 4
private const val BIT_PRICE_SHIFT_BITS = 4
private const val LOW_HALF_MASK = 0xFFFF0000.toInt()
private const val PRICE_OFFSET = 15
private const val UNSIGNED_INT_MASK = 0xFFFFFFFFL
private const val LOW_CARRY_LIMIT = 0xFF000000L
private const val LOW_KEEP_MASK = 0x00FFFFFFL
private const val CACHE_SHIFT = 24
private const val FLUSH_BYTES = 5
private const val INITIAL_CAPACITY = 256

/** The probability every bit model starts from: one half. */
internal const val PROB_INIT: Short = (BIT_MODEL_TOTAL / 2).toShort()

/** A table of [size] bit models, each at [PROB_INIT]. */
internal fun probabilities(size: Int): ShortArray = ShortArray(size) { PROB_INIT }

/**
 * The range encoder of xz-java's `RangeEncoder` and `RangeEncoderToStream` (org.tukaani:xz 1.10),
 * writing into memory. The arithmetic is the library's, bit for bit: `low` is a 64-bit value and
 * `range` an unsigned 32-bit one held in an [Int].
 */
internal class RangeEncoder {
    private var low = 0L
    private var range = -1
    private var cacheSize = 1L
    private var cache: Byte = 0
    private var out = ByteArray(INITIAL_CAPACITY)
    private var outSize = 0

    /** Flushes the coder and answers every byte written. */
    fun finish(): ByteArray {
        repeat(FLUSH_BYTES) { shiftLow() }
        return out.copyOf(outSize)
    }

    /** Encodes [bit] (zero or not) with the model `probs[index]`, and adapts the model. */
    fun encodeBit(probs: ShortArray, index: Int, bit: Int) {
        val prob = probs[index].toInt()
        val bound = (range ushr BIT_MODEL_TOTAL_BITS) * prob
        if (bit == 0) {
            range = bound
            probs[index] = (prob + ((BIT_MODEL_TOTAL - prob) ushr MOVE_BITS)).toShort()
        } else {
            low += bound.toLong() and UNSIGNED_INT_MASK
            range -= bound
            probs[index] = (prob - (prob ushr MOVE_BITS)).toShort()
        }
        normalize()
    }

    /** Encodes [symbol] through the bit tree [probs], most significant bit first. */
    fun encodeBitTree(probs: ShortArray, symbol: Int) {
        var index = 1
        var mask = probs.size
        do {
            mask = mask ushr 1
            val bit = symbol and mask
            encodeBit(probs, index, bit)
            index = index shl 1
            if (bit != 0) index = index or 1
        } while (mask != 1)
    }

    /** Encodes [symbol] through the bit tree [probs], least significant bit first. */
    fun encodeReverseBitTree(probs: ShortArray, symbol: Int) {
        var index = 1
        var rest = symbol or probs.size
        do {
            val bit = rest and 1
            rest = rest ushr 1
            encodeBit(probs, index, bit)
            index = (index shl 1) or bit
        } while (rest != 1)
    }

    /** Encodes the low [count] bits of [value] with fixed probability one half. */
    fun encodeDirectBits(value: Int, count: Int) {
        var left = count
        do {
            range = range ushr 1
            left--
            low += (range and (0 - ((value ushr left) and 1))).toLong()
            normalize()
        } while (left != 0)
    }

    private fun normalize() {
        if ((range and TOP_MASK) == 0) {
            range = range shl SHIFT_BITS
            shiftLow()
        }
    }

    private fun shiftLow() {
        val lowHi = (low ushr Int.SIZE_BITS).toInt()
        if (lowHi != 0 || low < LOW_CARRY_LIMIT) {
            var temp = cache.toInt()
            do {
                writeByte(temp + lowHi)
                temp = BYTE_MASK
            } while (--cacheSize != 0L)
            cache = (low ushr CACHE_SHIFT).toByte()
        }
        ++cacheSize
        low = (low and LOW_KEEP_MASK) shl SHIFT_BITS
    }

    private fun writeByte(value: Int) {
        if (outSize == out.size) out = out.copyOf(out.size * 2)
        out[outSize++] = value.toByte()
    }
}

/** The price tables of xz-java's `RangeEncoder`: what coding a bit costs, in sixteenths of a bit. */
internal object Prices {
    private val bitPrices = IntArray(BIT_MODEL_TOTAL ushr MOVE_REDUCING_BITS).also { table ->
        var i = (1 shl MOVE_REDUCING_BITS) / 2
        while (i < BIT_MODEL_TOTAL) {
            table[i shr MOVE_REDUCING_BITS] = priceAt(i)
            i += 1 shl MOVE_REDUCING_BITS
        }
    }

    private fun priceAt(probability: Int): Int {
        var w = probability
        var bitCount = 0
        repeat(BIT_PRICE_SHIFT_BITS) {
            w *= w
            bitCount = bitCount shl 1
            while ((w and LOW_HALF_MASK) != 0) {
                w = w ushr 1
                ++bitCount
            }
        }
        return (BIT_MODEL_TOTAL_BITS shl BIT_PRICE_SHIFT_BITS) - PRICE_OFFSET - bitCount
    }

    /** The price of [bit] (0 or 1) with the model [prob]. */
    fun bit(prob: Int, bit: Int): Int =
        bitPrices[(prob xor ((-bit) and (BIT_MODEL_TOTAL - 1))) ushr MOVE_REDUCING_BITS]

    /** The price of [bit] with the model [prob]. */
    fun bit(prob: Short, bit: Int): Int = bit(prob.toInt(), bit)

    /** The price of [symbol] through the bit tree [probs]. */
    fun bitTree(probs: ShortArray, symbol: Int): Int {
        var price = 0
        var rest = symbol or probs.size
        do {
            val bit = rest and 1
            rest = rest ushr 1
            price += bit(probs[rest], bit)
        } while (rest != 1)
        return price
    }

    /** The price of [symbol] through the reverse bit tree [probs]. */
    fun reverseBitTree(probs: ShortArray, symbol: Int): Int {
        var price = 0
        var index = 1
        var rest = symbol or probs.size
        do {
            val bit = rest and 1
            rest = rest ushr 1
            price += bit(probs[index], bit)
            index = (index shl 1) or bit
        } while (rest != 1)
        return price
    }

    /** The price of [count] direct bits. */
    fun directBits(count: Int): Int = count shl BIT_PRICE_SHIFT_BITS
}
