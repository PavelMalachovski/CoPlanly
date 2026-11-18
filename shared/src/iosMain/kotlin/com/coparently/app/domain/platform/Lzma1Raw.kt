package com.coparently.app.domain.platform

/**
 * Raw LZMA1 through [Lzma1Encoder], the common Kotlin port of the `org.tukaani:xz` encoder Android
 * calls, with the same settings, so a Slovak account's PAY by square code is the same bytes on
 * both phones (CLAUDE.md item 45).
 */
actual object Lzma1Raw {

    /** [input] compressed as raw LZMA1: no header, with the end marker. Never null on iOS. */
    actual fun encode(input: ByteArray): ByteArray? = Lzma1Encoder.encode(input)
}
