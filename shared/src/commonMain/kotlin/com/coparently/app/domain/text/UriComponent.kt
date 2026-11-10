package com.coparently.app.domain.text

/**
 * A text percent-encoded as `android.net.Uri.encode(text)` writes it, in pure Kotlin, for the
 * navigation routes that carry an id or a draft (MON-24 phase 2, Task 12).
 *
 * The routes moved to common code with the navigation graph, where `Uri` does not exist. They have
 * to keep their text: a route a pending deep link carries across a process death is written by one
 * build and parsed by the next. `UriComponentParityTest` (the app's Robolectric tests) holds this
 * to `Uri.encode` itself, and `UriComponentTest` holds the vectors on every platform.
 */
object UriComponent {

    private const val ESCAPE = '%'
    private const val BITS_PER_HEX_DIGIT = 4
    private const val LOW_NIBBLE = 0xF
    private const val HEX_DIGITS = "0123456789ABCDEF"

    /**
     * The byte an unpaired surrogate is written as: `?`. `Uri.encode` encodes each run of
     * characters it does not allow through `String.getBytes("UTF-8")`, which writes `?` for a
     * surrogate with no partner; a well-formed pair is always in one run, since no surrogate is
     * allowed, and is the four UTF-8 bytes of its code point.
     */
    private const val UNPAIRED_SURROGATE_BYTE = 0x3F

    /** The marks `Uri.encode` leaves alone, beside the ASCII letters and digits. */
    private const val UNRESERVED_MARKS = "_-!.~'()*"

    /**
     * [text] as `Uri.encode(text)` writes it: `A`–`Z`, `a`–`z`, `0`–`9` and `_ - ! . ~ ' ( ) *` as
     * they are, and every other character as `%XX`, with upper-case hex, for each byte of its UTF-8
     * form (a space is `%20`). An unpaired surrogate is `%3F`.
     */
    fun encode(text: String): String {
        val encoded = StringBuilder(text.length)
        var index = 0
        while (index < text.length) {
            val char = text[index]
            val next = text.getOrNull(index + 1)
            when {
                isAllowed(char) -> encoded.append(char)
                char.isHighSurrogate() && next != null && next.isLowSurrogate() -> {
                    Utf8.bytesOf(Utf8.codePointOf(char, next)).forEach { encoded.appendEscape(it) }
                    index++
                }
                char.isSurrogate() -> encoded.appendEscape(UNPAIRED_SURROGATE_BYTE)
                else -> Utf8.bytesOf(char.code).forEach { encoded.appendEscape(it) }
            }
            index++
        }
        return encoded.toString()
    }

    private fun isAllowed(char: Char): Boolean =
        char in 'A'..'Z' || char in 'a'..'z' || char in '0'..'9' || char in UNRESERVED_MARKS

    /** Appends `%XX` for [byte] (0 to 255), in upper-case hex. */
    private fun StringBuilder.appendEscape(byte: Int) {
        append(ESCAPE)
        append(HEX_DIGITS[byte shr BITS_PER_HEX_DIGIT])
        append(HEX_DIGITS[byte and LOW_NIBBLE])
    }
}
