package com.coparently.app.domain.text

import org.junit.Test
import java.net.URLDecoder
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import kotlin.random.Random
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * [PercentCodec] against the JDK's `URLEncoder` and `URLDecoder` in UTF-8, which the install
 * referrer and the message report call today: a fixed corpus and 10 000 seeded random strings
 * from the Cyrillic, Latin-2, emoji and ASCII-punctuation ranges, plus malformed input built to
 * reach every branch of the JDK's UTF-8 replacement and of `Integer.parseInt`'s hex reading.
 *
 * Both directions are equal to the JDK for every input but one class: where `URLDecoder` throws, the
 * port answers null, and everything else, U+FFFD replacements included, is the same text. The class
 * left out is a surrogate written as UTF-8 (an escaped run holding ED followed by A0..BF): the JDK
 * writes one U+FFFD for up to three of those bytes, while the port follows the Unicode Standard
 * (§3.9, "U+FFFD Substitution of Maximal Subparts") and writes one per maximal subpart — a decision
 * ROADMAP MON-24 records ("a surrogate written as UTF-8 follows Unicode"). Those inputs are held
 * to Unicode's count instead ([assertDecodesAsTheJdk]).
 */
class PercentCodecParityTest {

    @Test
    fun encodingMatchesUrlEncoderOnTheCorpus() {
        CORPUS.forEach { text ->
            assertEquals(URLEncoder.encode(text, UTF_8), PercentCodec.encode(text), "encode(\"$text\")")
        }
    }

    @Test
    fun encodingMatchesUrlEncoderOnRandomText() {
        val random = Random(SEED)
        repeat(RANDOM_CASES) {
            val text = random.text()
            assertEquals(URLEncoder.encode(text, UTF_8), PercentCodec.encode(text), "encode(\"$text\")")
        }
    }

    @Test
    fun decodingMatchesUrlDecoderOnTheCorpus() {
        (CORPUS + CORPUS.map { URLEncoder.encode(it, UTF_8) } + MALFORMED).forEach { text ->
            assertDecodesAsTheJdk(text)
        }
    }

    @Test
    fun decodingMatchesUrlDecoderOnRandomText() {
        val random = Random(SEED)
        repeat(RANDOM_CASES) {
            val text = random.text()
            val encoded = URLEncoder.encode(text, UTF_8)
            assertDecodesAsTheJdk(encoded)
            assertDecodesAsTheJdk(text)
            assertDecodesAsTheJdk(random.escapes())
        }
    }

    @Test
    fun decodingMatchesUrlDecoderOnMalformedUtf8() {
        val random = Random(SEED)
        repeat(RANDOM_CASES) {
            val escaped = random.utf8ish().joinToString("") { "%%%02X".format(it.toInt() and BYTE_MASK) }
            assertDecodesAsTheJdk(escaped)
        }
    }

    @Test
    fun aSurrogateWrittenAsUtf8IsOneReplacementPerMaximalSubpart() {
        // Unicode's count, not the JDK's single U+FFFD (ROADMAP MON-24). ED alone is a maximal subpart
        // (its second byte ends at 9F), and every continuation byte after it is one more.
        assertEquals("\uFFFD\uFFFD\uFFFD", PercentCodec.decode("%ED%A0%80"))
        assertEquals("\uFFFD\uFFFD\uFFFD\uFFFD", PercentCodec.decode("%ED%A0%80%80"))
        assertEquals("\uFFFD\uFFFDA", PercentCodec.decode("%ED%A0%41"))
        assertEquals("a\uFFFD\uFFFD\uFFFD\u010D", PercentCodec.decode("a%ED%BF%BF%C4%8D"))
        assertEquals("\uFFFD\uFFFD\uFFFD\uFFFD\uFFFD\uFFFD", PercentCodec.decode("%ED%B0%80%ED%A0%80"))
        // The reference agrees with those, and with the JDK where no surrogate is written.
        listOf("%ED%A0%80", "%ED%A0%41", "%ED%B0%80%ED%A0%80", "%ED%9F%BF%E2%82%AC").forEach { text ->
            assertEquals(PercentCodec.decode(text), unicodeDecode(text), "reference(${describe(text)})")
        }
        assertEquals(urlDecode("%ED%9F%BF%C0%80"), unicodeDecode("%ED%9F%BF%C0%80"))
        // The random malformed input reaches the class often, so the exclusion is not vacuous.
        val random = Random(SEED)
        val reached = (1..RANDOM_CASES).count {
            val escaped = random.utf8ish().joinToString("") { "%%%02X".format(it.toInt() and BYTE_MASK) }
            writesASurrogate(escaped)
        }
        assertTrue(reached > RANDOM_CASES / MIN_CLASS_SHARE, "surrogate inputs reached: $reached")
    }

    @Test
    fun anEscapesDigitsAreCharacterDigits() {
        // Integer.parseInt reads each digit with Character.digit(c, 16); the port reads it with
        // digitToIntOrNull(16). Every UTF-16 unit, so a Unicode or fullwidth digit cannot differ.
        (Char.MIN_VALUE..Char.MAX_VALUE).forEach { c ->
            assertEquals(Character.digit(c, HEX).takeIf { it >= 0 }, c.digitToIntOrNull(HEX), "U+%04X".format(c.code))
        }
    }

    /**
     * [text] decodes as `URLDecoder` decodes it — except for a surrogate written as UTF-8, which
     * ROADMAP MON-24's decision takes out of the comparison and holds to the Unicode Standard's
     * count of one U+FFFD per maximal subpart instead ([unicodeDecode]). Nothing else is excluded.
     */
    private fun assertDecodesAsTheJdk(text: String) {
        if (writesASurrogate(text)) {
            assertEquals(unicodeDecode(text), PercentCodec.decode(text), "decode(${describe(text)}), Unicode's count")
        } else {
            assertEquals(urlDecode(text), PercentCodec.decode(text), "decode(${describe(text)})")
        }
    }

    /** Whether one of [text]'s escaped runs holds ED followed by A0..BF, a surrogate as UTF-8. */
    private fun writesASurrogate(text: String): Boolean =
        pieces(text)?.any { it is Piece.Escaped && it.bytes.surrogateAt() != null } == true

    /**
     * [text] decoded with the JDK, except that each surrogate written as UTF-8 is replaced as the
     * Unicode Standard recommends: ED is one maximal subpart and each continuation byte after it is
     * one more. The bytes between are the JDK's, which agrees with Unicode on everything else.
     */
    private fun unicodeDecode(text: String): String? = pieces(text)?.joinToString("") { piece ->
        when (piece) {
            is Piece.Literal -> piece.char.toString()
            is Piece.Escaped -> unicodeDecode(piece.bytes)
        }
    }

    private fun unicodeDecode(bytes: ByteArray): String = buildString {
        var start = 0
        var surrogate = bytes.surrogateAt(start)
        while (surrogate != null) {
            append(String(bytes, start, surrogate - start, StandardCharsets.UTF_8))
            var end = surrogate + 1
            while (end < bytes.size && (bytes[end].toInt() and BYTE_MASK) in CONTINUATION) end++
            repeat(end - surrogate) { append('\uFFFD') }
            start = end
            surrogate = bytes.surrogateAt(start)
        }
        append(String(bytes, start, bytes.size - start, StandardCharsets.UTF_8))
    }

    /** The first index from [from] where ED is followed by A0..BF, or null. */
    private fun ByteArray.surrogateAt(from: Int = 0): Int? = (from until size - 1).firstOrNull {
        (this[it].toInt() and BYTE_MASK) == SURROGATE_LEAD && (this[it + 1].toInt() and BYTE_MASK) in SURROGATE_SECOND
    }

    /** [text] as `URLDecoder` reads it before decoding UTF-8, or null where it throws. */
    private fun pieces(text: String): List<Piece>? {
        val pieces = mutableListOf<Piece>()
        var index = 0
        while (index < text.length) {
            if (text[index] != '%') {
                pieces += Piece.Literal(if (text[index] == '+') ' ' else text[index])
                index++
                continue
            }
            val bytes = mutableListOf<Byte>()
            while (index < text.length && text[index] == '%') {
                val value = if (index + 3 <= text.length) parseHex(text.substring(index + 1, index + 3)) else null
                if (value == null || value < 0) return null
                bytes += value.toByte()
                index += 3
            }
            pieces += Piece.Escaped(bytes.toByteArray())
        }
        return pieces
    }

    private fun parseHex(digits: String): Int? = try {
        Integer.parseInt(digits, HEX)
    } catch (_: NumberFormatException) {
        null
    }

    /** One stretch of a form-encoded text: a character, or a run of escaped bytes. */
    private sealed interface Piece {
        class Literal(val char: Char) : Piece
        class Escaped(val bytes: ByteArray) : Piece
    }

    /** `URLDecoder.decode(text, UTF-8)`, or null where it throws. */
    private fun urlDecode(text: String): String? = try {
        URLDecoder.decode(text, UTF_8)
    } catch (_: IllegalArgumentException) {
        null
    }

    /** [text] with every character outside printable ASCII as an escape, for a failure message. */
    private fun describe(text: String): String =
        text.map { if (it.code in PRINTABLE) it.toString() else "\\u%04X".format(it.code) }.joinToString("")

    /**
     * Bytes that are mostly not UTF-8: lone continuation bytes, leads cut short, overlong forms,
     * surrogates (U+D800 to U+DFFF) and code points above U+10FFFF, between valid sequences.
     */
    private fun Random.utf8ish(): List<Byte> = buildList {
        repeat(nextInt(1, MAX_PIECES)) {
            val piece = when (nextInt(PIECE_KINDS)) {
                0 -> listOf(nextInt(0x80, 0xC0))
                1 -> listOf(nextInt(0xC0, 0x100))
                2 -> listOf(nextInt(0xC0, 0xF8), nextInt(0x80, 0xC0))
                3 -> listOf(0xE0, nextInt(0x80, 0xA0), nextInt(0x80, 0xC0))
                4 -> listOf(0xED, nextInt(0xA0, 0xC0), nextInt(0x80, 0xC0))
                5 -> listOf(0xF0, nextInt(0x80, 0x90), nextInt(0x80, 0xC0), nextInt(0x80, 0xC0))
                6 -> listOf(nextInt(0xF4, 0xF8), nextInt(0x80, 0xC0), nextInt(0x80, 0xC0), nextInt(0x80, 0xC0))
                7 -> listOf(nextInt(0xE0, 0xF0), nextInt(0x80, 0xC0))
                8 -> listOf(nextInt(0xF0, 0xF5), nextInt(0x80, 0xC0), nextInt(0x80, 0xC0))
                9 -> "a\u010D\uD83D\uDE00".encodeToByteArray().map { b -> b.toInt() and BYTE_MASK }
                else -> List(nextInt(1, 5)) { nextInt(0, 0x100) }
            }
            piece.forEach { add(it.toByte()) }
        }
    }

    private fun Random.text(): String = buildString {
        repeat(nextInt(0, MAX_LENGTH)) {
            val range = RANGES[nextInt(RANGES.size)]
            appendCodePoint(nextInt(range.first, range.last + 1))
        }
    }

    /** Escapes and characters that decoding has to judge: hex of every kind, and near-misses. */
    private fun Random.escapes(): String = buildString {
        repeat(nextInt(0, MAX_LENGTH)) {
            when (nextInt(5)) {
                0 -> append('%').append(ESCAPE_CHARS[nextInt(ESCAPE_CHARS.length)])
                    .append(ESCAPE_CHARS[nextInt(ESCAPE_CHARS.length)])
                1 -> append('%').append(HEX_UPPER[nextInt(HEX_UPPER.length)])
                2 -> append("%C4%8D")
                3 -> append('%').append(HEX_UPPER[nextInt(HEX_UPPER.length)])
                    .append(HEX_UPPER[nextInt(HEX_UPPER.length)])
                else -> append(ESCAPE_CHARS[nextInt(ESCAPE_CHARS.length)])
            }
        }
    }

    private fun StringBuilder.appendCodePoint(codePoint: Int) {
        append(String(Character.toChars(codePoint)))
    }

    private companion object {
        const val SEED = 20261101
        const val RANDOM_CASES = 10_000
        const val MAX_LENGTH = 24
        const val HEX = 16
        const val BYTE_MASK = 0xFF
        const val SURROGATE_LEAD = 0xED
        const val MIN_CLASS_SHARE = 20
        val SURROGATE_SECOND = 0xA0..0xBF
        val CONTINUATION = 0x80..0xBF
        const val MAX_PIECES = 6
        const val PIECE_KINDS = 11
        val PRINTABLE = 0x20..0x7E
        val UTF_8: String = StandardCharsets.UTF_8.name()
        const val HEX_UPPER = "0123456789ABCDEF"

        // Hex of both cases, signs, Arabic-Indic one and two, fullwidth three and A, and non-digits.
        const val ESCAPE_CHARS = "0123456789ABCDEFabcdefGgZz+-% \u0661\u0662\uFF13\uFF21"

        /** ASCII punctuation and space, Latin-2 (Latin Extended-A), Cyrillic, and emoji. */
        val RANGES = listOf(0x20..0x7E, 0x0100..0x017F, 0x0400..0x04FF, 0x1F300..0x1FAFF)

        val CORPUS = listOf(
            "",
            " ",
            "a b+č*~%😀",
            "AZaz09.-*_",
            "pair=ABC234",
            "pair=ABC234&utm_source=google-play",
            "Летние каникулы",
            "Літо з татом",
            "Prázdniny u táty: Žďár nad Sázavou",
            "Sommerferien bei Papa – Straße",
            "👨‍👩‍👧 rodina",
            "!\"#$&'()+,/:;<=>?@[\\]^`{|}~",
            "\t\n\r",
            "a\uD83Db",
            "\uDE00",
            "\uDE00\uD83D",
            "�",
            "\u0000",
        )

        val MALFORMED = listOf(
            "%",
            "%4",
            "abc%",
            "%ZZ",
            "%+F",
            "%-1",
            "%C4",
            "%C4x%8D",
            "%FF",
            "%ED%A0%80",
            "%C0%80",
            "%F0%9F%98",
            "%EF%BF%BD",
            "%e2%82%ac",
            "+%2B+",
            "\u010D%C4%8D\u010D",
            "%-0",
            "%+F",
            "%\u0661\u0662",
            "%\uFF11\uFF12",
            "%\uFF21\uFF22",
            "%\uFF41f",
            "%E2%82",
            "%E2%82%",
            "%E2%82%A",
            "%A",
            "%F4%90%80%80",
            "%F0%80%80%80",
            "%F0%9F%98%80%F0%9F",
            "%F5%80",
            "%F4",
            "%F4%8F",
            "%F4%8F%BF",
            "%E0%80%80",
            "%E0%A0",
            "%C2",
            "%C2%41",
            "%80%80",
        )
    }
}
