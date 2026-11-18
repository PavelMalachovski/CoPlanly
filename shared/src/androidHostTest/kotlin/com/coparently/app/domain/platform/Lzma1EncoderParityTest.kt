package com.coparently.app.domain.platform

import com.coparently.app.domain.payment.PaymentQr
import com.coparently.app.domain.payment.PaymentRequest
import org.junit.Test
import org.tukaani.xz.LZMA2Options
import org.tukaani.xz.LZMAOutputStream
import java.io.ByteArrayOutputStream
import java.util.zip.CRC32
import kotlin.random.Random
import kotlin.test.assertContentEquals

/**
 * [Lzma1Encoder], the common Kotlin port the iPhone runs, against the oracle Android runs:
 * `org.tukaani:xz` called exactly as [Lzma1Raw]'s Android actual calls it. Every case must come out
 * byte for byte the same, because a Slovak bank's app reads the code either phone draws.
 */
class Lzma1EncoderParityTest {

    @Test
    fun paymentQrTestDocumentMatches() {
        val document = listOf(
            "", "1", "1", "450.00", "EUR", "", "", "", "", "", "CoPlanly 2026-10", "1",
            "SK3112000000198742637541", "", "0", "0", "Zuzana Nova", "", ""
        ).joinToString("\t").toByteArray(Charsets.UTF_8)
        assertParity(withCrc(document))
    }

    @Test
    fun emptyInputMatches() {
        assertParity(ByteArray(0))
    }

    @Test
    fun seededRandomInputsMatch() {
        val random = Random(SEED)
        repeat(RANDOM_CASES) { index ->
            val length = random.nextInt(0, MAX_LENGTH + 1)
            val input = if (index % 2 == 0) {
                random.nextBytes(length)
            } else {
                ByteArray(length) { ('a'.code + random.nextInt(ALPHABET_SIZE)).toByte() }
            }
            val expected = paymentQrCompress(input)
            val actual = Lzma1Encoder.encode(input)
            assertContentEquals(
                expected,
                actual,
                "case $index, length ${input.size}, input ${input.toHex()}"
            )
        }
    }

    @Test
    fun longRepeatsMatch() {
        listOf(300, 1_000, 4_096).forEach { length -> assertParity(ByteArray(length) { 'x'.code.toByte() }) }
        val pattern = "ab3\u0000Zq9".toByteArray(Charsets.ISO_8859_1)
        assertParity(ByteArray(3_000) { pattern[it % pattern.size] })
        val block = Random(SEED + 1).nextBytes(2_048)
        assertParity(block + block + block)
    }

    @Test
    fun payBySquareShapedDocumentsMatch() {
        val pieces = "Платба за школку, Žluťoučký kůň úpěl ďábelské ódy, ãß €; "
        listOf(0, 1, 70, 140, 280).forEach { noteLength ->
            val note = buildString { while (length < noteLength) append(pieces) }.take(noteLength)
            val request = PaymentRequest(
                iban = "SK3112000000198742637541",
                amountMinor = 12_345,
                currency = "EUR",
                beneficiary = "Zuzana Nová",
                note = note
            )
            val document = PaymentQr.PayBySquare.document(request).toByteArray(Charsets.UTF_8)
            assertParity(withCrc(document))
        }
    }

    private fun assertParity(input: ByteArray) {
        assertContentEquals(paymentQrCompress(input), Lzma1Encoder.encode(input), "length ${input.size}")
    }

    private fun withCrc(document: ByteArray): ByteArray {
        val crc = CRC32().apply { update(document) }.value
        return ByteArray(Int.SIZE_BYTES) { (crc shr (8 * it) and 0xFF).toByte() } + document
    }

    /** `PaymentQr.PayBySquare.encode`'s compression on Android, copied from `Lzma1RawTest`. */
    private fun paymentQrCompress(input: ByteArray): ByteArray = ByteArrayOutputStream().also { out ->
        val options = LZMA2Options().apply {
            setLcLp(3, 0)
            pb = 2
            dictSize = 1 shl 17
        }
        LZMAOutputStream(out, options, true).use { it.write(input) }
    }.toByteArray()

    private fun ByteArray.toHex(): String = joinToString("") { (it.toInt() and 0xFF).toString(16).padStart(2, '0') }

    private companion object {
        const val SEED = 20261118
        const val RANDOM_CASES = 2_000
        const val MAX_LENGTH = 2_048
        const val ALPHABET_SIZE = 16
    }
}
