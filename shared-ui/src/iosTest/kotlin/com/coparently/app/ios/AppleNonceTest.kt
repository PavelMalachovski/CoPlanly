package com.coparently.app.ios

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/** [AppleNonce]: what Apple's request carries is the SHA-256 of what Firebase receives. */
class AppleNonceTest {

    @Test
    fun nonceHashIsSha256Hex() {
        assertEquals("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad", sha256Hex("abc"))
    }

    @Test
    fun `a nonce's hash is the SHA-256 of its raw text`() {
        val nonce = AppleNonce.random()

        assertEquals(sha256Hex(nonce.raw), nonce.hashed)
        assertEquals(64, nonce.hashed.length)
    }

    @Test
    fun `every nonce is new and 64 hex characters`() {
        val first = AppleNonce.random()
        val second = AppleNonce.random()

        assertNotEquals(first.raw, second.raw)
        assertEquals(64, first.raw.length)
        assertTrue(first.raw.all { it in "0123456789abcdef" })
    }

    @Test
    fun `a nonce never prints itself`() {
        val nonce = AppleNonce.random()

        assertTrue(nonce.raw !in nonce.toString())
    }
}
