package com.coparently.app.domain.text

import kotlin.test.Test
import kotlin.test.assertContentEquals

/**
 * [Utf8.encode] writes what the JVM's `String.toByteArray(Charsets.UTF_8)` writes — every expected
 * array below was printed by JDK 21 — so a text hashed on an iPhone matches the same text hashed on
 * an Android phone, an unpaired surrogate included (`?`, where Kotlin/Native's own encoder writes
 * U+FFFD). `Utf8ParityTest` holds the JVM build to the JDK itself.
 */
class Utf8Test {

    @Test
    fun asciiIsOneByteEach() {
        assertBytes("A", 0x41)
    }

    @Test
    fun lettersOutsideAsciiAreTheirUtf8() {
        assertBytes("č", 0xC4, 0x8D)
        assertBytes("€퟿￿", 0xE2, 0x82, 0xAC, 0xED, 0x9F, 0xBF, 0xEE, 0x80, 0x80, 0xEF, 0xBF, 0xBF)
    }

    @Test
    fun aSurrogatePairIsItsCodePoint() {
        assertBytes("😀", 0xF0, 0x9F, 0x98, 0x80)
        assertBytes("􏿿", 0xF4, 0x8F, 0xBF, 0xBF)
    }

    @Test
    fun anUnpairedSurrogateIsAQuestionMark() {
        assertBytes("\uD83D", 0x3F)
        assertBytes("\uDE00", 0x3F)
        assertBytes("a\uD83Db", 0x61, 0x3F, 0x62)
        assertBytes("\uD83D😀", 0x3F, 0xF0, 0x9F, 0x98, 0x80)
        assertBytes("\uDE00\uD83D", 0x3F, 0x3F)
    }

    private fun assertBytes(text: String, vararg expected: Int) {
        assertContentEquals(ByteArray(expected.size) { expected[it].toByte() }, Utf8.encode(text), text)
    }
}
