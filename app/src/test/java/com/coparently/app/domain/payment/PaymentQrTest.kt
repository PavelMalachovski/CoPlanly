package com.coparently.app.domain.payment

import org.junit.Test
import org.tukaani.xz.LZMAInputStream
import java.math.BigDecimal
import java.util.zip.CRC32
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * The three payment codes, byte for byte. PAY by square's expected text was produced by Python's
 * liblzma raw LZMA1 encoder from the same document, so the test pins this build's encoder to the
 * reference one; the round trip below then reads it back the way a bank's app does.
 */
class PaymentQrTest {

    private fun request(iban: String, currency: String, amount: String = "450") = PaymentRequest(
        iban = iban,
        amount = BigDecimal(amount),
        currency = currency,
        beneficiary = "Zuzana Nová",
        note = "CoPlanly 2026-10"
    )

    @Test
    fun `a Czech account gets QR Platba, in ASCII`() {
        val wire = PaymentQr.encode(request("CZ6508000000192000145399", "CZK", "1290.5"))

        assertEquals(
            "SPD*1.0*ACC:CZ6508000000192000145399*AM:1290.50*CC:CZK*MSG:CoPlanly 2026-10*RN:Zuzana Nova",
            wire
        )
    }

    @Test
    fun `a Slovak account gets PAY by square, as the reference encoder writes it`() {
        val wire = PaymentQr.encode(request("SK3112000000198742637541", "EUR").copy(beneficiary = "Zuzana Nova"))

        assertEquals(
            "0005E000B8N9JIMTM9E7QHLQ1RKMBERA9EP9AF5JI36914EPNU3DBT0HTRCHQV49L6I8E0LV493LH2S31DLTO186AL5" +
                "DFCCIIRPP8GANRSAQROLKR07JINI0MSI2S2U7A9UPBVVVELM0000",
            wire
        )
    }

    @Test
    fun `PAY by square reads back to its document, with its length and checksum`() {
        val request = request("SK3112000000198742637541", "EUR", "12.3")
        val bytes = fromBase32hex(PaymentQr.encode(request)!!)

        assertEquals(0, bytes[0].toInt())
        assertEquals(0, bytes[1].toInt())
        val length = (bytes[2].toInt() and 0xFF) or ((bytes[3].toInt() and 0xFF) shl 8)
        // lc 3, lp 0, pb 2 is properties byte (2 * 5 + 0) * 9 + 3 = 93; the stream ends with its marker.
        val compressed = bytes.copyOfRange(4, bytes.size).inputStream()
        val raw = LZMAInputStream(compressed, -1L, 93.toByte(), 1 shl 17).readBytes()
        assertEquals(length, raw.size)
        val payload = raw.copyOfRange(4, raw.size)
        val crc = CRC32().apply { update(payload) }.value
        assertEquals(crc, (0..3).fold(0L) { acc, i -> acc or ((raw[i].toLong() and 0xFF) shl (8 * i)) })
        val fields = payload.toString(Charsets.UTF_8).split("\t")
        assertEquals("12.30", fields[3])
        assertEquals("EUR", fields[4])
        assertEquals("SK3112000000198742637541", fields[12])
        assertEquals("Zuzana Nová", fields[16])
    }

    @Test
    fun `another account gets the EPC code in euros and nothing in another currency`() {
        assertEquals(
            "BCD\n002\n1\nSCT\n\nZuzana Nova\nDE89370400440532013000\nEUR450.00\n\n\nCoPlanly 2026-10",
            PaymentQr.encode(request("DE89370400440532013000", "EUR"))
        )
        assertNull(PaymentQr.encode(request("DE89370400440532013000", "CZK")))
        assertNull(PaymentQr.formatFor(request("DE89370400440532013000", "PLN")))
    }

    private fun fromBase32hex(text: String): ByteArray {
        val alphabet = "0123456789ABCDEFGHIJKLMNOPQRSTUV"
        val bits = text.map { alphabet.indexOf(it).toString(2).padStart(5, '0') }.joinToString("")
        return bits.chunked(8).filter { it.length == 8 }.map { it.toInt(2).toByte() }.toByteArray()
    }
}
