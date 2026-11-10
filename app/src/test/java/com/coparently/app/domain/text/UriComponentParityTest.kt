package com.coparently.app.domain.text

import android.net.Uri
import com.coparently.app.testing.RobolectricTestApplication
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.random.Random

/**
 * [UriComponent] against `android.net.Uri.encode`, which the navigation routes called until they
 * moved to `:shared-ui`'s common code (MON-24 phase 2, Task 12): a fixed corpus and 10 000 seeded
 * random strings from ASCII, Latin-2, Cyrillic, emoji and lone surrogates.
 *
 * Robolectric runs the framework's own `Uri` class, so this is the text a route was written with on
 * a phone: a route a pending deep link carries across a process death is written by one build and
 * parsed by the next, and must not change.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = RobolectricTestApplication::class)
class UriComponentParityTest {

    @Test
    fun encodingMatchesUriEncodeOnTheCorpus() {
        CORPUS.forEach { text ->
            assertEquals("encode(${describe(text)})", Uri.encode(text), UriComponent.encode(text))
        }
    }

    @Test
    fun encodingMatchesUriEncodeOnRandomText() {
        val random = Random(SEED)
        repeat(RANDOM_CASES) {
            val text = random.text()
            assertEquals("encode(${describe(text)})", Uri.encode(text), UriComponent.encode(text))
        }
    }

    @Test
    fun everyUtf16UnitAloneMatchesUriEncode() {
        (Char.MIN_VALUE..Char.MAX_VALUE).forEach { char ->
            val text = char.toString()
            assertEquals("encode(${describe(text)})", Uri.encode(text), UriComponent.encode(text))
        }
    }

    /** [text] with every character outside printable ASCII as an escape, for a failure message. */
    private fun describe(text: String): String =
        text.map { if (it.code in PRINTABLE) it.toString() else "\\u%04X".format(it.code) }.joinToString("")

    /** One to twenty characters from the ranges a route may carry, and some it never should. */
    private fun Random.text(): String {
        val length = nextInt(1, MAX_LENGTH + 1)
        return buildString {
            repeat(length) {
                when (nextInt(RANGES)) {
                    0 -> append(nextInt(ASCII_FIRST, ASCII_LAST + 1).toChar())
                    1 -> append(nextInt(LATIN_FIRST, LATIN_LAST + 1).toChar())
                    2 -> append(nextInt(CYRILLIC_FIRST, CYRILLIC_LAST + 1).toChar())
                    3 -> appendCodePoint(nextInt(EMOJI_FIRST, EMOJI_LAST + 1))
                    else -> append(nextInt(SURROGATE_FIRST, SURROGATE_LAST + 1).toChar())
                }
            }
        }
    }

    private fun StringBuilder.appendCodePoint(codePoint: Int) {
        append(Character.toChars(codePoint))
    }

    private companion object {
        const val SEED = 20261109
        const val RANDOM_CASES = 10_000
        const val MAX_LENGTH = 20
        const val RANGES = 5
        const val ASCII_FIRST = 0x00
        const val ASCII_LAST = 0x7F
        const val LATIN_FIRST = 0x80
        const val LATIN_LAST = 0x17F
        const val CYRILLIC_FIRST = 0x400
        const val CYRILLIC_LAST = 0x4FF
        const val EMOJI_FIRST = 0x1F300
        const val EMOJI_LAST = 0x1FAFF
        const val SURROGATE_FIRST = 0xD800
        const val SURROGATE_LAST = 0xDFFF
        val PRINTABLE = 0x20..0x7E

        val CORPUS = listOf(
            "",
            "alice__bob",
            "a b",
            "AZaz09_-!.~'()*",
            "a/b?c=d&e#f%g+h",
            "Pay me 1 290,50 Kč for March, please",
            "čřžýáíé ÄÖÜß",
            "лето і ї є ґ ʼ",
            "😀👨‍👩‍👧",
            "a\uD83Db",
            "\uDE00",
            "\uDE00\uD83D",
            "\uD83D",
            "line\nbreak\ttab",
            "[]{}<>|\\^`\"",
            "school-0123456789abcdef0123456789abcdef",
            "family_abc__def",
        )
    }
}
