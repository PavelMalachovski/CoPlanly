package com.coparently.app.domain.text

/**
 * UTF-8 by hand, for the places the standard library's encoder cannot be used: it writes an
 * unpaired surrogate differently per platform (`?` on the JVM, U+FFFD elsewhere), and
 * [PercentCodec.encode] has to write what the JVM writes on every platform, as does every text
 * that is hashed or compressed for the other phone to compare ([encode]).
 */
internal object Utf8 {

    private const val ONE_BYTE_LIMIT = 0x80
    private const val TWO_BYTE_LIMIT = 0x800
    private const val THREE_BYTE_LIMIT = 0x10000
    private const val SUPPLEMENTARY_BASE = 0x10000
    private const val SURROGATE_BITS = 10
    private const val TWO_BYTE_LEAD = 0xC0
    private const val THREE_BYTE_LEAD = 0xE0
    private const val FOUR_BYTE_LEAD = 0xF0
    private const val CONTINUATION = 0x80
    private const val CONTINUATION_BITS = 6
    private const val TWO_CONTINUATIONS = 2 * CONTINUATION_BITS
    private const val THREE_CONTINUATIONS = 3 * CONTINUATION_BITS
    private const val CONTINUATION_MASK = 0x3F
    private const val THIRD_LAST = 2

    /** What `String.toByteArray(Charsets.UTF_8)` writes for an unpaired surrogate: `?`. */
    private const val UNPAIRED_SURROGATE = 0x3F

    /**
     * [text]'s UTF-8 bytes exactly as the JVM's `String.toByteArray(Charsets.UTF_8)` writes them:
     * a well-formed surrogate pair as its code point's four bytes, and each unpaired surrogate — a
     * high one not followed by a low one, or a low one on its own — as `?`. Kotlin's
     * `encodeToByteArray()` writes U+FFFD's three bytes for one on iOS, so a text hashed there
     * would not match the same text hashed on an Android phone.
     */
    fun encode(text: String): ByteArray {
        val bytes = ArrayList<Byte>(text.length)
        var index = 0
        while (index < text.length) {
            val char = text[index]
            val next = text.getOrNull(index + 1)
            val codePoint = when {
                char.isHighSurrogate() && next != null && next.isLowSurrogate() -> {
                    index++
                    codePointOf(char, next)
                }
                char.isSurrogate() -> UNPAIRED_SURROGATE
                else -> char.code
            }
            bytesOf(codePoint).forEach { bytes.add(it.toByte()) }
            index++
        }
        return bytes.toByteArray()
    }

    /** The code point a surrogate pair stands for. */
    fun codePointOf(high: Char, low: Char): Int =
        ((high - Char.MIN_HIGH_SURROGATE) shl SURROGATE_BITS) + (low - Char.MIN_LOW_SURROGATE) + SUPPLEMENTARY_BASE

    /** [codePoint]'s UTF-8 bytes, each as an Int from 0 to 255. */
    fun bytesOf(codePoint: Int): IntArray = when {
        codePoint < ONE_BYTE_LIMIT -> intArrayOf(codePoint)
        codePoint < TWO_BYTE_LIMIT -> intArrayOf(
            TWO_BYTE_LEAD or (codePoint shr CONTINUATION_BITS),
            continuation(codePoint, 0)
        )
        codePoint < THREE_BYTE_LIMIT -> intArrayOf(
            THREE_BYTE_LEAD or (codePoint shr TWO_CONTINUATIONS),
            continuation(codePoint, 1),
            continuation(codePoint, 0)
        )
        else -> intArrayOf(
            FOUR_BYTE_LEAD or (codePoint shr THREE_CONTINUATIONS),
            continuation(codePoint, THIRD_LAST),
            continuation(codePoint, 1),
            continuation(codePoint, 0)
        )
    }

    private fun continuation(codePoint: Int, index: Int): Int =
        CONTINUATION or ((codePoint shr (index * CONTINUATION_BITS)) and CONTINUATION_MASK)
}
