package com.coparently.app.domain.platform

import com.coparently.app.domain.text.Crc32
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

/**
 * [Lzma1Raw] on every platform against the reference: the Slovak document of `Lzma1RawTest` and
 * `PaymentQrTest`, whose PAY by square code Python's liblzma produced. On Android this is
 * `org.tukaani:xz`, on the iPhone the common port [Lzma1Encoder]; both must give the bytes inside
 * that code, so a Slovak bank's app reads what either phone draws.
 */
class Lzma1RawVectorTest {

    @Test
    fun paymentQrTestDocumentCompressesToTheReferenceBytes() {
        val document = listOf(
            "", "1", "1", "450.00", "EUR", "", "", "", "", "", "CoPlanly 2026-10", "1",
            "SK3112000000198742637541", "", "0", "0", "Zuzana Nova", "", ""
        ).joinToString("\t").encodeToByteArray()
        val crc = Crc32.of(document)
        val withCrc = ByteArray(Int.SIZE_BYTES) { (crc shr (8 * it) and 0xFF).toByte() } + document
        val code = fromBase32hex(PAY_BY_SQUARE_EXPECTED)

        // Two zero bytes for type and version, then the uncompressed length, little-endian.
        assertEquals(withCrc.size, (code[2].toInt() and 0xFF) or ((code[3].toInt() and 0xFF) shl 8))
        assertContentEquals(code.copyOfRange(4, code.size), assertNotNull(Lzma1Raw.encode(withCrc)))
    }

    private fun fromBase32hex(text: String): ByteArray {
        val alphabet = "0123456789ABCDEFGHIJKLMNOPQRSTUV"
        val bits = text.map { alphabet.indexOf(it).toString(2).padStart(5, '0') }.joinToString("")
        return bits.chunked(8).filter { it.length == 8 }.map { it.toInt(2).toByte() }.toByteArray()
    }

    private companion object {
        const val PAY_BY_SQUARE_EXPECTED =
            "0005E000B8N9JIMTM9E7QHLQ1RKMBERA9EP9AF5JI36914EPNU3DBT0HTRCHQV49L6I8E0LV493LH2S31DLTO186AL5" +
                "DFCCIIRPP8GANRSAQROLKR07JINI0MSI2S2U7A9UPBVVVELM0000"
    }
}
