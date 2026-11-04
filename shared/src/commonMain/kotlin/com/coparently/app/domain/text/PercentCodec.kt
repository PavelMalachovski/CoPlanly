package com.coparently.app.domain.text

/**
 * Form encoding (`application/x-www-form-urlencoded`) in UTF-8, as `java.net.URLEncoder.encode`
 * and `java.net.URLDecoder.decode` do it with `"UTF-8"`, in pure Kotlin: the install referrer and
 * the message report are written and read this way, and an iPhone has to give the JDK's answers.
 *
 * Written from the two classes' documentation and from black-box runs of JDK 21, not from any
 * implementation of them; `PercentCodecTest` holds the vectors on every platform and
 * `PercentCodecParityTest` holds the JVM build to the JDK itself.
 */
object PercentCodec {

    private const val ESCAPE = '%'
    private const val SPACE = ' '
    private const val PLUS = '+'
    private const val MINUS = '-'
    private const val HEX_RADIX = 16
    private const val BITS_PER_HEX_DIGIT = 4
    private const val LOW_NIBBLE = 0xF
    private const val HEX_DIGITS = "0123456789ABCDEF"

    /** A `%` and the two characters after it. */
    private const val ESCAPE_LENGTH = 3

    /**
     * The byte an unpaired surrogate is written as: `?`. `URLEncoder` encodes text through
     * `String.getBytes`, and black-box runs of JDK 21 show every unpaired surrogate — a high one
     * not followed by a low one, or a low one not preceded by a high one — coming out as `%3F`, one
     * per surrogate, while a well-formed pair is the four UTF-8 bytes of its code point.
     */
    private const val UNPAIRED_SURROGATE_BYTE = 0x3F

    /** The characters form encoding leaves alone (besides the space, which becomes `+`). */
    private const val UNRESERVED_MARKS = ".-*_"

    /**
     * [text] form-encoded, as `URLEncoder.encode(text, "UTF-8")` writes it: `A`–`Z`, `a`–`z`,
     * `0`–`9` and `. - * _` as they are, a space as `+`, and every other character as `%XX` with
     * upper-case hex for each byte of its UTF-8 form. An unpaired surrogate is `%3F` (a `?`), which
     * is what the JDK writes for one; the standard library's own encoder would write U+FFFD on
     * some platforms, so the bytes come from [Utf8] instead.
     */
    fun encode(text: String): String {
        val encoded = StringBuilder(text.length)
        var index = 0
        while (index < text.length) {
            val char = text[index]
            val next = text.getOrNull(index + 1)
            when {
                isUnreserved(char) -> encoded.append(char)
                char == SPACE -> encoded.append(PLUS)
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

    /**
     * [text] form-decoded, as `URLDecoder.decode(text, "UTF-8")` reads it, or null wherever that
     * throws `IllegalArgumentException`.
     *
     * A `+` is a space. A `%` and the two characters after it are one byte, the two characters read
     * as `Integer.parseInt(them, 16)` reads them: an optional leading `+` or `-` and then hex digits
     * as `Character.digit(c, 16)` accepts them, which takes any Unicode decimal digit and the
     * fullwidth `Ａ`–`Ｆ`/`ａ`–`ｆ` as well as ASCII. Bytes from consecutive escapes are decoded
     * together as UTF-8 by [Utf8Decoder], each ill-formed maximal subpart becoming U+FFFD. Every
     * other character stands for itself, a lone surrogate included.
     *
     * Null, as black-box runs of JDK 21 show `URLDecoder` throwing, for: a `%` with fewer than two
     * characters after it, anywhere in a run (`%`, `%4`, `abc%`, `%41%`, `%+`); two characters
     * `Integer.parseInt` refuses (`%ZZ`, `%4x`, `%%4`, `%--`, `%+-`, `% 1`); and a negative value
     * (`%-1`, `%-F`, while `%-0` is NUL).
     */
    fun decode(text: String): String? {
        val decoded = StringBuilder(text.length)
        var index = 0
        while (index < text.length) {
            val char = text[index]
            when (char) {
                PLUS -> {
                    decoded.append(SPACE)
                    index++
                }
                ESCAPE -> {
                    val run = escapedRun(text, index) ?: return null
                    decoded.append(Utf8Decoder.decode(run.bytes))
                    index = run.end
                }
                else -> {
                    decoded.append(char)
                    index++
                }
            }
        }
        return decoded.toString()
    }

    /**
     * The bytes of the consecutive escapes starting at [start] and the index just past them, or
     * null when one of them is cut short or does not read as a byte.
     */
    private fun escapedRun(text: String, start: Int): EscapedRun? {
        val bytes = mutableListOf<Byte>()
        var index = start
        while (index < text.length && text[index] == ESCAPE) {
            val value = if (index + ESCAPE_LENGTH <= text.length) byteOf(text[index + 1], text[index + 2]) else null
            if (value == null) return null
            bytes.add(value.toByte())
            index += ESCAPE_LENGTH
        }
        return EscapedRun(bytes.toByteArray(), index)
    }

    /**
     * `Integer.parseInt("$first$second", 16)` when that is a byte (0 to 255), else null: a sign
     * followed by one digit, or two digits. A lone sign, a sign in second place, a non-digit and
     * a negative value are all null.
     */
    private fun byteOf(first: Char, second: Char): Int? {
        val low = second.digitToIntOrNull(HEX_RADIX)
        val value = when {
            low == null -> null
            first == PLUS -> low
            first == MINUS -> -low
            else -> first.digitToIntOrNull(HEX_RADIX)?.let { (it shl BITS_PER_HEX_DIGIT) or low }
        }
        return value?.takeIf { it >= 0 }
    }

    private fun isUnreserved(char: Char): Boolean =
        char in 'A'..'Z' || char in 'a'..'z' || char in '0'..'9' || char in UNRESERVED_MARKS

    /** Appends `%XX` for [byte] (0 to 255), in upper-case hex. */
    private fun StringBuilder.appendEscape(byte: Int) {
        append(ESCAPE)
        append(HEX_DIGITS[byte shr BITS_PER_HEX_DIGIT])
        append(HEX_DIGITS[byte and LOW_NIBBLE])
    }

    /**
     * The bytes a run of escapes stands for.
     *
     * @property bytes the escaped bytes, in order.
     * @property end the index in the text just past the run's last escape.
     */
    private class EscapedRun(val bytes: ByteArray, val end: Int)
}
