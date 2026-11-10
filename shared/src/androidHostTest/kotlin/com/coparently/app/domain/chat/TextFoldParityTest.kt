package com.coparently.app.domain.chat

import org.junit.Test
import java.text.Normalizer
import java.util.Locale
import kotlin.random.Random
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals

/**
 * [TextFold] against the JDK implementation it replaced (MON-24), kept below as the oracle: a
 * fixed corpus and 10 000 seeded random strings from the ranges a chat holds — Latin-2, Cyrillic,
 * combining marks, every kind of space, emoji and unpaired surrogates — must fold to the same text
 * with the same map back to the original.
 */
class TextFoldParityTest {

    @Test
    fun whitespaceIsCharacterIsWhitespace() {
        for (code in Char.MIN_VALUE.code..Char.MAX_VALUE.code) {
            val folded = TextFold.simple(code.toChar().toString())
            assertEquals(Character.isWhitespace(code), folded == " ", "U+%04X".format(code))
        }
    }

    @Test
    fun foldsTheCorpusAsTheJdkDid() {
        CORPUS.forEach(::assertFoldsAsTheJdk)
    }

    @Test
    fun foldsRandomTextAsTheJdkDid() {
        val random = Random(SEED)
        repeat(RANDOM_CASES) { assertFoldsAsTheJdk(random.text()) }
    }

    private fun assertFoldsAsTheJdk(text: String) {
        val expected = JdkTextFold.fold(text)
        val actual = TextFold.fold(text)
        assertEquals(expected.text, actual.text, "fold(\"$text\")")
        assertContentEquals(expected.starts, actual.starts, "starts of \"$text\"")
        assertContentEquals(expected.ends, actual.ends, "ends of \"$text\"")
    }

    private fun Random.text(): String = buildString {
        repeat(nextInt(1, MAX_LENGTH)) {
            val range = RANGES[nextInt(RANGES.size)]
            appendCodePoint(nextInt(range.first, range.last + 1))
        }
    }

    private fun StringBuilder.appendCodePoint(codePoint: Int) {
        append(String(Character.toChars(codePoint)))
    }

    /** TextFold as it was before MON-24, on `java.text.Normalizer` and `java.lang.Character`. */
    private object JdkTextFold {
        private val COMBINING_MARKS = Regex("\\p{Mn}+")

        fun fold(text: String): FoldedText {
            val out = StringBuilder(text.length)
            val starts = ArrayList<Int>(text.length)
            val ends = ArrayList<Int>(text.length)
            var index = 0
            while (index < text.length) {
                val codePoint = text.codePointAt(index)
                val next = index + Character.charCount(codePoint)
                val piece = foldCodePoint(codePoint)
                if (piece.isEmpty()) {
                    if (ends.isNotEmpty()) ends[ends.lastIndex] = next
                } else {
                    piece.forEach { folded ->
                        out.append(folded)
                        starts += index
                        ends += next
                    }
                }
                index = next
            }
            return FoldedText(out.toString(), starts.toIntArray(), ends.toIntArray())
        }

        private fun foldCodePoint(codePoint: Int): String {
            if (Character.isWhitespace(codePoint)) return " "
            val lower = String(Character.toChars(codePoint)).lowercase(Locale.ROOT)
            return Normalizer.normalize(lower, Normalizer.Form.NFD).replace(COMBINING_MARKS, "")
        }
    }

    private companion object {
        const val SEED = 20261101
        const val RANDOM_CASES = 10_000
        const val MAX_LENGTH = 24

        val CORPUS = listOf(
            "", " ", "Čas", "čas", "čas", "Příliš žluťoučký kůň", "ÄÖÜ ß straße",
            "Привет, ЁЖИК", "Їжак і ґанок, є", "İstanbul ıI", "ΣΑΣ σας ς", "ǅǈǋ", "ﬁ ﬀ",
            "a b c d", "a\tb\nc\u000Bd\u000Ce\rf\u001Cg\u001Fh", "a b c　d",
            "a b c d e᠎f", "́x", "x́̂y", "😀 👨‍👩‍👧 🇨🇿",
            "a\uD83Db", "\uDE00a", "\uD83D", "𝐀𝐁 𝒜", "ẞ", "Å Å Å", "Ǆ", "İ", "ⅰⅱ ①",
        )

        /** Code point ranges the random strings draw from, each about equally often. */
        val RANGES = listOf(
            0x20..0x7E, // ASCII
            0x00A0..0x017F, // Latin-1 supplement and Latin Extended-A (Czech, German)
            0x0300..0x036F, // combining diacritical marks
            0x0370..0x03FF, // Greek
            0x0400..0x04FF, // Cyrillic
            0x1E00..0x1EFF, // Latin Extended Additional
            0x2000..0x206F, // general punctuation, its spaces included
            0x0009..0x000D, // controls that are whitespace
            0x001C..0x001F, // information separators, whitespace to Character
            0x3000..0x3000, // ideographic space
            0xD800..0xDFFF, // unpaired surrogates
            0x1F300..0x1F64F, // emoji
            0x1D400..0x1D4FF, // mathematical letters, which decompose compatibly but not canonically
            0xFB00..0xFB06, // ligatures
        )
    }
}
