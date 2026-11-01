package com.coparently.app.domain.text

import org.junit.Test
import java.net.URLDecoder
import java.net.URLEncoder
import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.StandardCharsets
import kotlin.random.Random
import kotlin.test.assertEquals

/**
 * [PercentCodec] against the JDK's `URLEncoder` and `URLDecoder` in UTF-8, which the install
 * referrer and the message report call today: a fixed corpus and 10 000 seeded random strings
 * from the Cyrillic, Latin-2, emoji and ASCII-punctuation ranges.
 *
 * Encoding is equal byte for byte. Decoding is equal wherever `URLDecoder` reads well-formed input;
 * the port is stricter in two places on purpose, and the reference below names both: an escape
 * must be two ASCII hex digits (`Integer.parseInt` also takes `%+F` and non-ASCII digits), and the
 * escaped bytes must be UTF-8 (`URLDecoder` puts U+FFFD in for bad ones and carries on).
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
            assertEquals(strictUrlDecode(text), PercentCodec.decode(text), "decode(\"$text\")")
        }
    }

    @Test
    fun decodingMatchesUrlDecoderOnRandomText() {
        val random = Random(SEED)
        repeat(RANDOM_CASES) {
            val text = random.text()
            val encoded = URLEncoder.encode(text, UTF_8)
            assertEquals(strictUrlDecode(encoded), PercentCodec.decode(encoded), "decode(\"$encoded\")")
            assertEquals(strictUrlDecode(text), PercentCodec.decode(text), "decode(\"$text\")")
            val escapes = random.escapes()
            assertEquals(strictUrlDecode(escapes), PercentCodec.decode(escapes), "decode(\"$escapes\")")
        }
    }

    /**
     * `URLDecoder.decode(text, UTF-8)`, or null where it throws, where an escape is not two ASCII
     * hex digits, or where a run of escapes is not UTF-8 — the two places the port refuses.
     */
    private fun strictUrlDecode(text: String): String? {
        val decoded = runCatching { URLDecoder.decode(text, UTF_8) }.getOrNull() ?: return null
        val escapesWellFormed = Regex("%(?![0-9A-Fa-f]{2})").find(text) == null
        val runsAreUtf8 = ESCAPE_RUN.findAll(text).all { run ->
            val bytes = run.value.chunked(ESCAPE_LENGTH).map { it.drop(1).toInt(HEX).toByte() }.toByteArray()
            try {
                StandardCharsets.UTF_8.newDecoder().decode(ByteBuffer.wrap(bytes))
                true
            } catch (_: CharacterCodingException) {
                false
            }
        }
        return decoded.takeIf { escapesWellFormed && runsAreUtf8 }
    }

    private fun Random.text(): String = buildString {
        repeat(nextInt(0, MAX_LENGTH)) {
            val range = RANGES[nextInt(RANGES.size)]
            appendCodePoint(nextInt(range.first, range.last + 1))
        }
    }

    /** Escapes and characters that decoding has to judge: hex of every case, and near-misses. */
    private fun Random.escapes(): String = buildString {
        repeat(nextInt(0, MAX_LENGTH)) {
            when (nextInt(4)) {
                0 -> append('%').append(ESCAPE_CHARS[nextInt(ESCAPE_CHARS.length)])
                    .append(ESCAPE_CHARS[nextInt(ESCAPE_CHARS.length)])
                1 -> append('%').append(HEX_UPPER[nextInt(HEX_UPPER.length)])
                2 -> append("%C4%8D")
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
        const val ESCAPE_LENGTH = 3
        const val HEX = 16
        val UTF_8: String = StandardCharsets.UTF_8.name()
        const val HEX_UPPER = "0123456789ABCDEF"
        const val ESCAPE_CHARS = "0123456789ABCDEFabcdefGgZz+-% "
        val ESCAPE_RUN = Regex("(%[0-9A-Fa-f]{2})+")

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
            "č%C4%8Dč",
        )
    }
}
