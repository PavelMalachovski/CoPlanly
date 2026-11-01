package com.coparently.app.domain.text

import org.junit.Test
import kotlin.random.Random
import kotlin.test.assertContentEquals

/**
 * [Utf8.encode] against the JDK's `String.toByteArray(Charsets.UTF_8)`, which the plan citation's
 * hash and the PAY by square document were encoded with before MON-24: every single UTF-16 unit,
 * every two-unit combination around the surrogate ranges, and 10 000 seeded random strings that mix
 * Latin-2, Cyrillic, emoji and unpaired surrogates.
 */
class Utf8ParityTest {

    @Test
    fun everyCharAloneIsEncodedAsTheJdkDoes() {
        for (code in Char.MIN_VALUE.code..Char.MAX_VALUE.code) {
            assertEncodesAsTheJdk(code.toChar().toString())
        }
    }

    @Test
    fun everyPairAroundTheSurrogatesIsEncodedAsTheJdkDoes() {
        val edges = listOf('a', 'č', '퟿', '\uD800', '\uD83D', '\uDBFF', '\uDC00', '\uDE00', '\uDFFF', '')
        for (first in edges) for (second in edges) for (third in edges) {
            assertEncodesAsTheJdk("$first$second$third")
        }
    }

    @Test
    fun randomTextIsEncodedAsTheJdkDoes() {
        val random = Random(SEED)
        repeat(RANDOM_CASES) {
            val text = buildString {
                repeat(random.nextInt(1, MAX_LENGTH)) {
                    val range = RANGES[random.nextInt(RANGES.size)]
                    append(String(Character.toChars(random.nextInt(range.first, range.last + 1))))
                }
            }
            assertEncodesAsTheJdk(text)
        }
    }

    private fun assertEncodesAsTheJdk(text: String) {
        assertContentEquals(text.toByteArray(Charsets.UTF_8), Utf8.encode(text), text)
    }

    private companion object {
        const val SEED = 20261101
        const val RANDOM_CASES = 10_000
        const val MAX_LENGTH = 24

        val RANGES = listOf(
            0x20..0x7E,
            0x00A0..0x017F,
            0x0400..0x04FF,
            0x0800..0xD7FF,
            0xD800..0xDFFF, // unpaired surrogates, as single UTF-16 units
            0xE000..0xFFFF,
            0x1F300..0x1F64F,
            0x10000..0x10FFFF,
        )
    }
}
