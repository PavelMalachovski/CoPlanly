package com.coparently.app.domain.text

/**
 * CRC-32 as `java.util.zip.CRC32` computes it (ISO-HDLC: reflected polynomial `0xEDB88320`,
 * initial and final value all ones), in pure Kotlin.
 *
 * A PAY by square document carries this checksum in front of itself, and a Slovak bank's app
 * refuses a code whose checksum does not match, so the value must be the JDK's bit for bit.
 */
object Crc32 {

    private const val POLYNOMIAL = 0xEDB88320.toInt()
    private const val ALL_ONES = 0xFFFFFFFFL
    private const val BYTE_MASK = 0xFF
    private const val BITS_PER_BYTE = 8
    private const val TABLE_SIZE = 256

    private val table = IntArray(TABLE_SIZE) { index ->
        var crc = index
        repeat(BITS_PER_BYTE) { crc = if (crc and 1 != 0) (crc ushr 1) xor POLYNOMIAL else crc ushr 1 }
        crc
    }

    /** The CRC-32 of [bytes], from 0 to 2^32 − 1, as `CRC32().apply { update(bytes) }.value` gives it. */
    fun of(bytes: ByteArray): Long {
        var crc = -1
        for (byte in bytes) {
            crc = (crc ushr BITS_PER_BYTE) xor table[(crc xor byte.toInt()) and BYTE_MASK]
        }
        return crc.inv().toLong() and ALL_ONES
    }
}
