package com.coparently.app.domain.files

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * [toHex] is the form every stored SHA-256 takes (MON-23), compared byte for byte by both
 * parents' phones and by the server, so it has to read the same on the JVM, on Android and on iOS.
 * commonTest runs on all three: the Android unit tests and the iOS simulator tests.
 */
class HexDigestTest {

    @Test
    fun emptyInputIsEmptyText() {
        assertEquals("", ByteArray(0).toHex())
    }

    @Test
    fun zeroAndAllOnesAreTwoDigitsEach() {
        // 0xFF is -1 as a Kotlin Byte: a sign-extended nibble would print "ffffffff".
        assertEquals("00", byteArrayOf(0x00).toHex())
        assertEquals("ff", byteArrayOf(0xFF.toByte()).toHex())
        assertEquals("00ff00ff", byteArrayOf(0x00, 0xFF.toByte(), 0x00, 0xFF.toByte()).toHex())
    }

    @Test
    fun knownBytesGiveKnownLowercaseHex() {
        val bytes = listOf(0x01, 0x23, 0x45, 0x67, 0x89, 0xAB, 0xCD, 0xEF)
            .map { it.toByte() }
            .toByteArray()
        assertEquals("0123456789abcdef", bytes.toHex())
        assertEquals("00ff10ab", byteArrayOf(0, -1, 16, -85).toHex())
    }

    @Test
    fun aKnownSha256DigestReadsAsItsPublishedHex() {
        // SHA-256 of the empty input, as the standard publishes it.
        val expected = "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855"
        val digest = ByteArray(expected.length / 2) { i ->
            expected.substring(i * 2, i * 2 + 2).toInt(radix = 16).toByte()
        }
        assertEquals(expected, digest.toHex())
    }

    @Test
    fun everyByteValueIsItsTwoDigitLowercaseHex() {
        val all = ByteArray(256) { it.toByte() }
        val hex = all.toHex()
        assertEquals(512, hex.length)
        for (value in 0 until 256) {
            assertEquals(
                value.toString(radix = 16).padStart(2, '0'),
                hex.substring(value * 2, value * 2 + 2),
                "byte $value",
            )
        }
    }
}
