package com.coparently.app.domain.platform

/**
 * Raw LZMA1 compression with PAY by square's settings: no header, an end marker, lc 3, lp 0, pb 2
 * and a 128 KiB dictionary.
 *
 * A Slovak bank's app decodes the payment QR code with the reference encoder's settings, and
 * `PaymentQrTest` pins the Android encoder to that reference byte for byte, so the Android actual
 * is the `org.tukaani:xz` call `PaymentQr` makes today, unchanged. iOS has no such encoder yet and
 * answers null: `PaymentQr` then draws no code and still offers the IBAN to copy and share
 * (CLAUDE.md item 45), until a later phase ports one.
 */
expect object Lzma1Raw {

    /** [input] compressed as raw LZMA1, or null where this platform has no encoder. */
    fun encode(input: ByteArray): ByteArray?
}
