package com.coparently.app.domain.text

/**
 * `application/x-www-form-urlencoded` text in UTF-8, as `java.net.URLEncoder` and `URLDecoder`
 * write and read it, in pure Kotlin so both apps share it.
 *
 * The install referrer carries a pairing code in this form, and the message report's `mailto:`
 * link is built from it, so [encode] must give what `URLEncoder.encode(text, "UTF-8")` gives,
 * character for character: the unreserved `A–Z a–z 0–9 . - * _` as they are, a space as `+`, and
 * everything else as uppercase `%XX` of its UTF-8 bytes. A surrogate with no partner is written as
 * `?` (`%3F`), as `String.getBytes(UTF_8)` writes it.
 *
 * [decode] reads what `URLDecoder.decode(text, "UTF-8")` reads, with two refusals where the JDK
 * carries on: an escape must be `%` and two ASCII hex digits (`Integer.parseInt` there also takes
 * `%+F` and non-ASCII digits), and the escaped bytes must be UTF-8 (the JDK puts U+FFFD in for
 * bad ones). Either way the answer is null, so a caller keeps what it could not read rather than
 * a replacement character.
 */
object PercentCodec {

    private const val ESCAPE = '%'
    private const val ESCAPE_LENGTH = 3
    private const val HEX_UPPER = "0123456789ABCDEF"
    private const val HEX_RADIX = 16
    private const val NIBBLE_BITS = 4
    private const val NIBBLE_MASK = 0x0F
    private const val BYTE_MASK = 0xFF
    private const val UNPAIRED_SURROGATE = '?'.code

    /** [text] form-encoded in UTF-8, as `URLEncoder.encode(text, "UTF-8")` writes it. */
    fun encode(text: String): String = buildString {
        var i = 0
        while (i < text.length) {
            val c = text[i]
            when {
                c == ' ' -> append('+')
                c.isUnreserved() -> append(c)
                else -> {
                    val pair = c.isHighSurrogate() && i + 1 < text.length && text[i + 1].isLowSurrogate()
                    val codePoint = when {
                        pair -> Utf8.codePointOf(c, text[i + 1])
                        c.isSurrogate() -> UNPAIRED_SURROGATE
                        else -> c.code
                    }
                    if (pair) i++
                    Utf8.bytesOf(codePoint).forEach { appendEscape(it) }
                }
            }
            i++
        }
    }

    /**
     * The text [text] form-encodes, or null when an escape is not `%` and two hex digits or the
     * escaped bytes are not UTF-8. A `+` is a space; any other character stands for itself.
     */
    fun decode(text: String): String? {
        val out = StringBuilder(text.length)
        var i = 0
        while (i < text.length) {
            val c = text[i]
            if (c == ESCAPE) {
                // A run of escapes is one byte sequence, as URLDecoder decodes it.
                val end = escapeRunEnd(text, i)
                out.append(decodeRun(text, i, end) ?: return null)
                i = end
            } else {
                out.append(if (c == '+') ' ' else c)
                i++
            }
        }
        return out.toString()
    }

    /** Where the run of escapes starting at [start] ends; past the text when the last is cut short. */
    private fun escapeRunEnd(text: String, start: Int): Int {
        var i = start
        while (i < text.length && text[i] == ESCAPE) i += ESCAPE_LENGTH
        return i
    }

    /** The UTF-8 text the escapes from [start] to [end] spell, or null when they do not. */
    private fun decodeRun(text: String, start: Int, end: Int): String? {
        if (end > text.length) return null
        val bytes = ByteArray((end - start) / ESCAPE_LENGTH)
        for (k in bytes.indices) {
            val at = start + k * ESCAPE_LENGTH
            val high = hexValue(text[at + 1])
            val low = hexValue(text[at + 2])
            if (high < 0 || low < 0) return null
            bytes[k] = ((high shl NIBBLE_BITS) or low).toByte()
        }
        return runCatching { bytes.decodeToString(throwOnInvalidSequence = true) }.getOrNull()
    }

    private fun Char.isUnreserved(): Boolean =
        this in 'a'..'z' || this in 'A'..'Z' || this in '0'..'9' || this == '.' || this == '-' ||
            this == '*' || this == '_'

    private fun StringBuilder.appendEscape(byte: Int) {
        append(ESCAPE)
        append(HEX_UPPER[(byte and BYTE_MASK) shr NIBBLE_BITS])
        append(HEX_UPPER[byte and NIBBLE_MASK])
    }

    /** The value of an ASCII hex digit, or -1 for anything else. */
    private fun hexValue(c: Char): Int = when (c) {
        in '0'..'9', in 'a'..'f', in 'A'..'F' -> c.digitToInt(HEX_RADIX)
        else -> -1
    }
}
