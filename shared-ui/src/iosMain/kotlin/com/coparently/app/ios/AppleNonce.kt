package com.coparently.app.ios

import com.coparently.app.domain.platform.Sha256
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * The nonce of one Sign in with Apple request (MON-24 phase 4, part A): Apple signs the [hashed]
 * value into its ID token, and Firebase checks that token against the [raw] one, so a token taken
 * from another request cannot be replayed for this sign-in. A fresh one per request, never reused.
 *
 * @property raw The random text Firebase receives beside the ID token.
 * @property hashed [raw]'s SHA-256 in lower-case hex, which the request to Apple carries.
 */
internal class AppleNonce private constructor(val raw: String) {

    val hashed: String = sha256Hex(raw)

    /** Leaves the nonce out, so a log line never carries it. */
    override fun toString(): String = "AppleNonce"

    companion object {
        /**
         * A new nonce: two random UUIDs' hex, 64 characters from a cryptographically secure source
         * (`Uuid.random`).
         */
        @OptIn(ExperimentalUuidApi::class)
        fun random(): AppleNonce = AppleNonce(Uuid.random().toHexString() + Uuid.random().toHexString())
    }
}

/** [text]'s SHA-256, over its UTF-8 bytes, in lower-case hex: the form Apple's request carries. */
internal fun sha256Hex(text: String): String =
    Sha256.digest(text.encodeToByteArray()).joinToString("") { byte ->
        (byte.toInt() and BYTE_MASK).toString(HEX_RADIX).padStart(2, '0')
    }

private const val BYTE_MASK = 0xFF
private const val HEX_RADIX = 16
