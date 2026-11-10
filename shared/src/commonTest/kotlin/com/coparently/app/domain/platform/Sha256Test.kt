package com.coparently.app.domain.platform

import com.coparently.app.domain.files.toHex
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

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

    @Test
    fun aStreamedDigestIsTheDigestOfTheWhole() {
        // A shared file is hashed a buffer at a time; the runs, an empty one and offsets inside a
        // larger array included, must hash to what the bytes hash to at once.
        val whole = ByteArray(200_003) { (it * 31 + 7).toByte() }
        val streamed = Sha256.digestOf { update ->
            update(whole, 0, 0)
            var offset = 0
            var run = 1
            while (offset < whole.size) {
                val length = minOf(run, whole.size - offset)
                update(whole, offset, length)
                offset += length
                run = run * 3 + 1
            }
        }
        assertEquals(Sha256.digest(whole).toHex(), streamed.toHex())
    }

    @Test
    fun aStreamedDigestOfNothingIsTheEmptyDigest() {
        assertEquals(
            "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855",
            Sha256.digestOf { update -> update(ByteArray(4), 2, 0) }.toHex()
        )
    }

    @Test
    fun aStreamedDigestLeavesWithTheFeedsException() {
        val thrown = assertFailsWith<IllegalStateException> {
            Sha256.digestOf { update ->
                update("abc".encodeToByteArray(), 0, 3)
                error("cap reached")
            }
        }
        assertEquals("cap reached", thrown.message)
    }

    @Test
    fun aRunOutsideTheArrayIsRefused() {
        assertFailsWith<IllegalArgumentException> {
            Sha256.digestOf { update -> update(ByteArray(4), 2, 3) }
        }
        assertFailsWith<IllegalArgumentException> {
            Sha256.digestOf { update -> update(ByteArray(4), -1, 1) }
        }
    }
}
