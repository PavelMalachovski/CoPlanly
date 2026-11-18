package com.coparently.app.domain.platform.lzma

private const val LITERAL_PROBS = 0x300
private const val SYMBOL_FLAG = 0x100
private const val SYMBOL_END = 0x10000
private const val BIT_SHIFT = 7

/**
 * xz-java's `LZMAEncoder.LiteralEncoder` (org.tukaani:xz 1.10): codes a byte with the models chosen
 * by the previous byte's top [lc] bits and the position's low [lp] bits, matched against the byte
 * at the newest distance after a match.
 */
internal class LiteralEncoder(private val lc: Int, lp: Int, private val encoder: LzmaEncoder) {
    private val literalPosMask = (1 shl lp) - 1
    private val subencoders = Array(1 shl (lc + lp)) { probabilities(LITERAL_PROBS) }

    private fun subcoderIndex(prevByte: Int, pos: Int): Int =
        (prevByte shr (BYTE_BITS - lc)) + ((pos and literalPosMask) shl lc)

    /** Codes the first byte, which has no previous one. */
    fun encodeInit() {
        encode(subencoders[0])
    }

    /** Codes the byte at the encoder's position. */
    fun encode() {
        val lz = encoder.lz
        val readAhead = encoder.readAhead
        encode(subencoders[subcoderIndex(lz.getByte(1 + readAhead), lz.pos - readAhead)])
    }

    private fun encode(probs: ShortArray) {
        val lz = encoder.lz
        val readAhead = encoder.readAhead
        val rc = encoder.rc
        var symbol = lz.getByte(readAhead) or SYMBOL_FLAG
        if (encoder.state.isLiteral) {
            do {
                rc.encodeBit(probs, symbol ushr BYTE_BITS, (symbol ushr BIT_SHIFT) and 1)
                symbol = symbol shl 1
            } while (symbol < SYMBOL_END)
        } else {
            var matchByte = lz.getByte(encoder.reps[0] + 1 + readAhead)
            var offset = SYMBOL_FLAG
            do {
                matchByte = matchByte shl 1
                val matchBit = matchByte and offset
                rc.encodeBit(probs, offset + matchBit + (symbol ushr BYTE_BITS), (symbol ushr BIT_SHIFT) and 1)
                symbol = symbol shl 1
                offset = offset and (matchByte xor symbol).inv()
            } while (symbol < SYMBOL_END)
        }
        encoder.state.updateLiteral()
    }

    /** The price of coding [curByte] after [prevByte] at [pos] in [state], [matchByte] at the newest distance. */
    fun getPrice(curByte: Int, matchByte: Int, prevByte: Int, pos: Int, state: LzmaState): Int {
        val probs = subencoders[subcoderIndex(prevByte, pos)]
        return Prices.bit(encoder.isMatch[state.value][pos and encoder.posMask], 0) +
            if (state.isLiteral) normalPrice(probs, curByte) else matchedPrice(probs, curByte, matchByte)
    }

    private fun normalPrice(probs: ShortArray, byte: Int): Int {
        var price = 0
        var symbol = byte or SYMBOL_FLAG
        do {
            price += Prices.bit(probs[symbol ushr BYTE_BITS], (symbol ushr BIT_SHIFT) and 1)
            symbol = symbol shl 1
        } while (symbol < SYMBOL_END)
        return price
    }

    private fun matchedPrice(probs: ShortArray, byte: Int, matchByteIn: Int): Int {
        var price = 0
        var offset = SYMBOL_FLAG
        var symbol = byte or SYMBOL_FLAG
        var matchByte = matchByteIn
        do {
            matchByte = matchByte shl 1
            val matchBit = matchByte and offset
            price += Prices.bit(probs[offset + matchBit + (symbol ushr BYTE_BITS)], (symbol ushr BIT_SHIFT) and 1)
            symbol = symbol shl 1
            offset = offset and (matchByte xor symbol).inv()
        } while (symbol < SYMBOL_END)
        return price
    }
}
