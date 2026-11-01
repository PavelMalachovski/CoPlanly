package com.coparently.app.domain.platform

/**
 * No raw LZMA1 encoder on iOS yet: every call answers null, and `PaymentQr` draws no PAY by square
 * code for a Slovak account, offering the IBAN to copy and share instead (CLAUDE.md item 45).
 */
actual object Lzma1Raw {

    /** Always null: this platform has no encoder. */
    actual fun encode(input: ByteArray): ByteArray? = null
}
