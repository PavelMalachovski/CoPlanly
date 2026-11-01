package com.coparently.app.domain.text

/**
 * UTF-8 to text by the Unicode Standard, written from its definition rather than from any
 * platform's decoder, so [PercentCodec.decode] reads the same bytes the same way on every platform.
 *
 * A well-formed sequence is one row of Table 3-7 of the Unicode Standard (§3.9, "Well-Formed UTF-8
 * Byte Sequences"): the lead byte decides the length and the range the second byte must fall in,
 * and every later byte is 80..BF. Anything else is ill-formed, and each **maximal subpart** of it —
 * the longest prefix of a well-formed sequence that the bytes begin, or a single byte when they
 * begin none — becomes one U+FFFD, the practice §3.9 recommends ("U+FFFD Substitution of Maximal
 * Subparts"). So `C4` alone is one U+FFFD, `E2 82` cut short is one, `C0 80` (an overlong NUL) is
 * two, and `ED A0 80` (a surrogate written as UTF-8, which Table 3-7 excludes by ending ED's second
 * byte at 9F) is three. The JVM's own decoder writes one U+FFFD for that last case; this one
 * follows Unicode there by decision (ROADMAP MON-24, "a surrogate written as UTF-8 follows
 * Unicode").
 */
internal object Utf8Decoder {

    /** U+FFFD REPLACEMENT CHARACTER, written in place of each maximal subpart. */
    private const val REPLACEMENT = '\uFFFD'

    private const val BYTE_MASK = 0xFF
    private const val CONTINUATION_BITS = 6
    private const val CONTINUATION_PAYLOAD = 0x3F
    private const val SUPPLEMENTARY_BASE = 0x10000
    private const val SURROGATE_BITS = 10
    private const val LOW_SURROGATE_PAYLOAD = 0x3FF

    /** Every byte after the second in a well-formed sequence, and the second after most leads. */
    private val CONTINUATION = 0x80..0xBF

    /**
     * The rows of Table 3-7, keyed by the lead bytes they start with: how many bytes the sequence
     * has, which bits of the lead carry the code point, and the range the second byte must be in.
     * A lead byte that no row names (80..C1, F5..FF) begins no sequence at all.
     */
    private val ROWS = listOf(
        Row(leads = 0x00..0x7F, length = 1, leadPayload = 0x7F, second = CONTINUATION),
        Row(leads = 0xC2..0xDF, length = 2, leadPayload = 0x1F, second = CONTINUATION),
        Row(leads = 0xE0..0xE0, length = 3, leadPayload = 0x0F, second = 0xA0..0xBF),
        Row(leads = 0xE1..0xEC, length = 3, leadPayload = 0x0F, second = CONTINUATION),
        Row(leads = 0xED..0xED, length = 3, leadPayload = 0x0F, second = 0x80..0x9F),
        Row(leads = 0xEE..0xEF, length = 3, leadPayload = 0x0F, second = CONTINUATION),
        Row(leads = 0xF0..0xF0, length = 4, leadPayload = 0x07, second = 0x90..0xBF),
        Row(leads = 0xF1..0xF3, length = 4, leadPayload = 0x07, second = CONTINUATION),
        Row(leads = 0xF4..0xF4, length = 4, leadPayload = 0x07, second = 0x80..0x8F),
    )

    /**
     * [bytes] read as UTF-8, each maximal subpart of an ill-formed sequence replaced by one U+FFFD.
     * Never fails: every byte array is some text.
     */
    fun decode(bytes: ByteArray): String {
        val text = StringBuilder(bytes.size)
        var index = 0
        while (index < bytes.size) {
            index += decodeAt(bytes, index, text)
        }
        return text.toString()
    }

    /**
     * Reads the sequence starting at [start] into [text] — its character, or one U+FFFD when it is
     * ill-formed — and answers how many bytes it took: the whole sequence, or its maximal subpart.
     */
    private fun decodeAt(bytes: ByteArray, start: Int, text: StringBuilder): Int {
        val lead = bytes[start].toInt() and BYTE_MASK
        val row = ROWS.firstOrNull { lead in it.leads }
        if (row == null) {
            text.append(REPLACEMENT)
            return 1
        }
        var codePoint = lead and row.leadPayload
        var taken = 1
        while (taken < row.length && start + taken < bytes.size) {
            val next = bytes[start + taken].toInt() and BYTE_MASK
            val allowed = if (taken == 1) row.second else CONTINUATION
            if (next !in allowed) break
            codePoint = (codePoint shl CONTINUATION_BITS) or (next and CONTINUATION_PAYLOAD)
            taken++
        }
        if (taken == row.length) appendCodePoint(text, codePoint) else text.append(REPLACEMENT)
        return taken
    }

    /** Appends [codePoint] as UTF-16: one unit below U+10000, a surrogate pair from there on. */
    private fun appendCodePoint(text: StringBuilder, codePoint: Int) {
        if (codePoint < SUPPLEMENTARY_BASE) {
            text.append(codePoint.toChar())
        } else {
            val offset = codePoint - SUPPLEMENTARY_BASE
            text.append(Char.MIN_HIGH_SURROGATE + (offset shr SURROGATE_BITS))
            text.append(Char.MIN_LOW_SURROGATE + (offset and LOW_SURROGATE_PAYLOAD))
        }
    }

    /**
     * One row of Table 3-7.
     *
     * @property leads the lead bytes the row starts with.
     * @property length the number of bytes in the sequence, lead included.
     * @property leadPayload the mask of the lead's bits that belong to the code point.
     * @property second the range the second byte must fall in; unused when [length] is 1.
     */
    private class Row(val leads: IntRange, val length: Int, val leadPayload: Int, val second: IntRange)
}
