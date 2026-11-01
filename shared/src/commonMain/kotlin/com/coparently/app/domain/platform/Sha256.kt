package com.coparently.app.domain.platform

/**
 * SHA-256 (FIPS 180-4), from the platform's own implementation: `MessageDigest` on Android,
 * CommonCrypto on iOS.
 *
 * Every digest the app stores is compared with one computed elsewhere — a parenting-plan citation
 * by the co-parent's phone, an export receipt by the verification page, a school event's id by the
 * other parent importing the same school account — so both actuals must return the standard
 * digest byte for byte, exactly what `MessageDigest.getInstance("SHA-256")` returns today.
 */
expect object Sha256 {

    /** The 32-byte SHA-256 digest of [bytes]. */
    fun digest(bytes: ByteArray): ByteArray
}
