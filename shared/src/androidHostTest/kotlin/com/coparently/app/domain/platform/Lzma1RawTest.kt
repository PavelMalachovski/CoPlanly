package com.coparently.app.domain.platform

import org.junit.Test
import org.tukaani.xz.LZMA2Options
import org.tukaani.xz.LZMAOutputStream
import java.io.ByteArrayOutputStream
import java.util.zip.CRC32
import kotlin.random.Random
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

/**
 * [Lzma1Raw] on Android is the encoder `PaymentQr.PayBySquare` runs today. The fixed case is the
 * Slovak document of the app's `PaymentQrTest`, whose expected code was produced by Python's
 * liblzma: the compressed bytes inside that code must come out of the port unchanged. The random
 * cases compare it with `PaymentQr`'s own call of the library.
 */
class Lzma1RawTest {

    @Test
    fun compressesThePaymentQrTestDocumentAsTheReferenceEncoderDid() {
        val document = listOf(
            "", "1", "1", "450.00", "EUR", "", "", "", "", "", "CoPlanly 2026-10", "1",
            "SK3112000000198742637541", "", "0", "0", "Zuzana Nova", "", ""
        ).joinToString("\t").toByteArray(Charsets.UTF_8)
        val crc = CRC32().apply { update(document) }.value
        val withCrc = ByteArray(Int.SIZE_BYTES) { (crc shr (8 * it) and 0xFF).toByte() } + document
        val code = fromBase32hex(PAY_BY_SQUARE_EXPECTED)

        // Two zero bytes for type and version, then the uncompressed length, little-endian.
        assertEquals(withCrc.size, (code[2].toInt() and 0xFF) or ((code[3].toInt() and 0xFF) shl 8))
        assertContentEquals(code.copyOfRange(4, code.size), assertNotNull(Lzma1Raw.encode(withCrc)))
    }

    @Test
    fun matchesPaymentQrsCallOfTheLibraryOnRandomInput() {
        val random = Random(SEED)
        repeat(RANDOM_CASES) {
            val input = random.nextBytes(random.nextInt(0, MAX_LENGTH))
            assertContentEquals(paymentQrCompress(input), Lzma1Raw.encode(input), "length ${input.size}")
        }
    }

    /** `PaymentQr.PayBySquare.encode`'s compression, copied from the app as it stands. */
    private fun paymentQrCompress(input: ByteArray): ByteArray = ByteArrayOutputStream().also { out ->
        val options = LZMA2Options().apply {
            setLcLp(3, 0)
            pb = 2
            dictSize = 1 shl 17
        }
        LZMAOutputStream(out, options, true).use { it.write(input) }
    }.toByteArray()

    private fun fromBase32hex(text: String): ByteArray {
        val alphabet = "0123456789ABCDEFGHIJKLMNOPQRSTUV"
        val bits = text.map { alphabet.indexOf(it).toString(2).padStart(5, '0') }.joinToString("")
        return bits.chunked(8).filter { it.length == 8 }.map { it.toInt(2).toByte() }.toByteArray()
    }

    private companion object {
        const val SEED = 20261101
        const val RANDOM_CASES = 200
        const val MAX_LENGTH = 600
        const val PAY_BY_SQUARE_EXPECTED =
            "0005E000B8N9JIMTM9E7QHLQ1RKMBERA9EP9AF5JI36914EPNU3DBT0HTRCHQV49L6I8E0LV493LH2S31DLTO186AL5" +
                "DFCCIIRPP8GANRSAQROLKR07JINI0MSI2S2U7A9UPBVVVELM0000"
    }
}
