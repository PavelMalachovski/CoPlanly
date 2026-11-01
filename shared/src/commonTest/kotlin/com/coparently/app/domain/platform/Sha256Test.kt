package com.coparently.app.domain.platform

import com.coparently.app.domain.files.toHex
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * [Sha256] against the FIPS 180-4 examples. Every stored digest (a plan citation, an export
 * receipt, a school event's id) is compared byte for byte with what another phone or the server
 * computed, so the iPhone's CommonCrypto has to give what Android's MessageDigest gives.
 */
class Sha256Test {

    @Test
    fun abcGivesTheStandardDigest() {
        assertEquals(
            "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad",
            Sha256.digest("abc".encodeToByteArray()).toHex()
        )
    }

    @Test
    fun emptyInputGivesTheStandardDigest() {
        // CommonCrypto is handed no pointer into an empty array; the guard must still hash nothing.
        assertEquals(
            "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855",
            Sha256.digest(ByteArray(0)).toHex()
        )
    }

    @Test
    fun aDigestIsThirtyTwoBytes() {
        assertEquals(32, Sha256.digest(ByteArray(1000) { it.toByte() }).size)
    }
}
