package com.coparently.app.domain.text

import kotlin.test.Test
import kotlin.test.assertEquals

/** [Crc32] is the IEEE CRC-32 a PAY by square document carries in front of itself. */
class Crc32Test {

    @Test
    fun theCheckValueOfTheStandard() {
        assertEquals(0xCBF43926L, Crc32.of("123456789".encodeToByteArray()))
    }

    @Test
    fun nothingHashesToZero() {
        assertEquals(0L, Crc32.of(ByteArray(0)))
    }

    @Test
    fun allOnesStayUnsigned() {
        // A sign-extended Int would come back negative.
        assertEquals(0xFFFFFFFFL, Crc32.of(byteArrayOf(0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte())))
    }
}
