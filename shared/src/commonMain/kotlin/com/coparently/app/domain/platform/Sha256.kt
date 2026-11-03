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

    /**
     * The 32-byte SHA-256 digest of every run of bytes [feed] hands its `update`, in order: the
     * digest of a shared file read a buffer at a time, which is too large to hold at once (MON-23;
     * `data/files/` in `:shared-data`). An exception [feed] throws leaves this call with it, and no
     * digest.
     *
     * @param feed Called once, with `update(bytes, offset, length)`, which hashes
     *   `bytes[offset until offset + length]`.
     */
    fun digestOf(feed: (update: (bytes: ByteArray, offset: Int, length: Int) -> Unit) -> Unit): ByteArray
}
