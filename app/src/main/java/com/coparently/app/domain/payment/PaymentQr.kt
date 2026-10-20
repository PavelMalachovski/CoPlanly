package com.coparently.app.domain.payment

import org.tukaani.xz.LZMA2Options
import org.tukaani.xz.LZMAOutputStream
import java.io.ByteArrayOutputStream
import java.math.BigDecimal
import java.math.RoundingMode
import java.text.Normalizer
import java.util.zip.CRC32

/**
 * One payment a parent is asked to make: to whom, how much, and what the bank shows as the note.
 *
 * @property iban The receiving account, already [Iban.normalize]d.
 * @property amount What to pay, never negative.
 * @property currency ISO 4217 code.
 * @property beneficiary The receiving parent's name.
 * @property note The payment's message, e.g. "CoPlanly 2026-10".
 */
data class PaymentRequest(
    val iban: String,
    val amount: BigDecimal,
    val currency: String,
    val beneficiary: String,
    val note: String
)

/** The QR payment standards the app writes, chosen by the receiving account's country. */
enum class PaymentQrFormat {
    /** Czech "QR Platba" (Short Payment Descriptor, ČBA). */
    SPAYD,

    /** Slovak "PAY by square" (SBA). */
    PAY_BY_SQUARE,

    /** The European Payments Council's SEPA credit transfer code ("BCD"), euros only. */
    EPC
}

/**
 * What a payment QR code says (tester feedback, October 2026: "a QR code for the amount at Settle
 * up"; owner decision: by the receiving account's country).
 *
 * A Czech account gets QR Platba and a Slovak one PAY by square, the codes those countries' banking
 * apps read; any other account gets the EPC code, which is for euros only, and nothing for another
 * currency — the screen then shows the IBAN to copy. All three are pure text built here, so the
 * JVM tests reach every byte.
 */
object PaymentQr {

    private const val SPAYD_MESSAGE_MAX = 60
    private const val SPAYD_NAME_MAX = 35
    private const val EPC_NAME_MAX = 70
    private const val EPC_TEXT_MAX = 140
    private const val BYSQUARE_TEXT_MAX = 140

    /** Which code [request] gets, or null when none of the three can carry it. */
    fun formatFor(request: PaymentRequest): PaymentQrFormat? = when (Iban.countryOf(request.iban)) {
        "CZ" -> PaymentQrFormat.SPAYD
        "SK" -> PaymentQrFormat.PAY_BY_SQUARE
        else -> PaymentQrFormat.EPC.takeIf { request.currency == "EUR" }
    }

    /** The QR code's text for [request], or null when [formatFor] has none. */
    fun encode(request: PaymentRequest): String? = when (formatFor(request)) {
        PaymentQrFormat.SPAYD -> spayd(request)
        PaymentQrFormat.PAY_BY_SQUARE -> PayBySquare.encode(request)
        PaymentQrFormat.EPC -> epc(request)
        null -> null
    }

    /** The amount as every one of the three writes it: two decimals, a point, no grouping. */
    fun amountText(amount: BigDecimal): String = amount.setScale(2, RoundingMode.HALF_UP).toPlainString()

    /**
     * QR Platba: `SPD*1.0*ACC:…*AM:…*CC:…*MSG:…*RN:…`. A value may not hold `*`, and banks read
     * plain ASCII most reliably, so the free text loses its diacritics.
     */
    private fun spayd(request: PaymentRequest): String = listOf(
        "SPD",
        "1.0",
        "ACC:${request.iban}",
        "AM:${amountText(request.amount)}",
        "CC:${request.currency}",
        "MSG:${ascii(request.note).take(SPAYD_MESSAGE_MAX)}",
        "RN:${ascii(request.beneficiary).take(SPAYD_NAME_MAX)}"
    ).joinToString("*")

    /** The EPC069-12 code, version 002, in UTF-8 (character set 1), lines joined by LF. */
    private fun epc(request: PaymentRequest): String = listOf(
        "BCD",
        "002",
        "1",
        "SCT",
        "",
        ascii(request.beneficiary).take(EPC_NAME_MAX),
        request.iban,
        "EUR${amountText(request.amount)}",
        "",
        "",
        ascii(request.note).take(EPC_TEXT_MAX)
    ).joinToString("\n")

    /** [text] in plain ASCII: accents dropped, anything else outside printable ASCII and `*` removed. */
    internal fun ascii(text: String): String =
        Normalizer.normalize(text, Normalizer.Form.NFD)
            .filter { it.code in PRINTABLE_ASCII && it != '*' }
            .trim()

    private val PRINTABLE_ASCII = 0x20..0x7E

    /**
     * PAY by square, version 1.0 with the 1.1 beneficiary fields, as the Slovak banks' reference
     * encoder writes it: the tab-separated "Pay" document, its CRC32 in front, raw LZMA1
     * (lc 3, lp 0, pb 2, 128 KiB dictionary), a four-byte header (two zero bytes for type and
     * version, then the uncompressed length, little-endian), all of it in base32hex.
     */
    internal object PayBySquare {
        private const val ALPHABET = "0123456789ABCDEFGHIJKLMNOPQRSTUV"
        private const val DICTIONARY = 1 shl 17
        private const val LITERAL_CONTEXT_BITS = 3
        private const val POSITION_BITS = 2
        private const val BYTE_MASK = 0xFF
        private const val BITS_PER_BYTE = 8
        private const val BITS_PER_CHAR = 5
        private const val CHAR_MASK = 31

        fun encode(request: PaymentRequest): String {
            val payload = document(request).toByteArray(Charsets.UTF_8)
            val crc = CRC32().apply { update(payload) }.value
            val withCrc = littleEndian(crc, Int.SIZE_BYTES) + payload
            val compressed = ByteArrayOutputStream().also { out ->
                val options = LZMA2Options().apply {
                    setLcLp(LITERAL_CONTEXT_BITS, 0)
                    pb = POSITION_BITS
                    dictSize = DICTIONARY
                }
                LZMAOutputStream(out, options, true).use { it.write(withCrc) }
            }.toByteArray()
            val header = byteArrayOf(0, 0) + littleEndian(withCrc.size.toLong(), 2)
            return base32hex(header + compressed)
        }

        /** The "Pay" document: one payment order to one account. */
        internal fun document(request: PaymentRequest): String = listOf(
            "", // invoice id
            "1", // one payment
            "1", // a payment order
            amountText(request.amount),
            request.currency,
            "", // due date
            "", // variable symbol
            "", // constant symbol
            "", // specific symbol
            "", // originator's reference
            request.note.take(BYSQUARE_TEXT_MAX).replace('\t', ' '),
            "1", // one account
            request.iban,
            "", // BIC
            "0", // no standing order
            "0", // no direct debit
            request.beneficiary.take(BYSQUARE_TEXT_MAX).replace('\t', ' '),
            "", // beneficiary address, line 1
            "" // beneficiary address, line 2
        ).joinToString("\t")

        private fun littleEndian(value: Long, bytes: Int): ByteArray =
            ByteArray(bytes) { index -> (value shr (BITS_PER_BYTE * index) and BYTE_MASK.toLong()).toByte() }

        private fun base32hex(bytes: ByteArray): String = buildString {
            var buffer = 0
            var bits = 0
            for (byte in bytes) {
                buffer = (buffer shl BITS_PER_BYTE) or (byte.toInt() and BYTE_MASK)
                bits += BITS_PER_BYTE
                while (bits >= BITS_PER_CHAR) {
                    append(ALPHABET[(buffer shr (bits - BITS_PER_CHAR)) and CHAR_MASK])
                    bits -= BITS_PER_CHAR
                }
                buffer = buffer and ((1 shl bits) - 1)
            }
            if (bits > 0) append(ALPHABET[(buffer shl (BITS_PER_CHAR - bits)) and CHAR_MASK])
        }
    }
}
