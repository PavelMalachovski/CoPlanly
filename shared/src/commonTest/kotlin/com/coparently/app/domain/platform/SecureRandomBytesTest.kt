package com.coparently.app.domain.platform

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse

/** [secureRandomBytes] gives the size asked for, and two draws differ. */
class SecureRandomBytesTest {

    @Test
    fun givesTheSizeAskedFor() {
        assertEquals(32, secureRandomBytes(32).size)
        assertEquals(0, secureRandomBytes(0).size)
    }

    @Test
    fun twoDrawsDiffer() {
        // Two equal 32-byte draws from a working source happen with probability 2^-256.
        assertFalse(secureRandomBytes(32).contentEquals(secureRandomBytes(32)))
    }

    @Test
    fun refusesANegativeSizeTheSameWayEverywhere() {
        assertFailsWith<IllegalArgumentException> { secureRandomBytes(-1) }
    }
}
