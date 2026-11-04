package com.coparently.app.domain.platform

import org.tukaani.xz.LZMA2Options
import org.tukaani.xz.LZMAOutputStream
import java.io.ByteArrayOutputStream

/**
 * Raw LZMA1 through `org.tukaani:xz`, with exactly the options `PaymentQr.PayBySquare` sets today
 * (and every other option at the library's default, as there), so the payment code a Slovak
 * account gets does not change by a byte.
 */
actual object Lzma1Raw {

    private const val DICTIONARY = 1 shl 17
    private const val LITERAL_CONTEXT_BITS = 3
    private const val LITERAL_POSITION_BITS = 0
    private const val POSITION_BITS = 2

    /** [input] compressed as raw LZMA1: no header, with the end marker. Never null on Android. */
    actual fun encode(input: ByteArray): ByteArray? = ByteArrayOutputStream().also { out ->
        val options = LZMA2Options().apply {
            setLcLp(LITERAL_CONTEXT_BITS, LITERAL_POSITION_BITS)
            pb = POSITION_BITS
            dictSize = DICTIONARY
        }
        LZMAOutputStream(out, options, true).use { it.write(input) }
    }.toByteArray()
}
