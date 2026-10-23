package com.coparently.app.domain.pairing

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class InstallReferrerTest {

    @Test
    fun `reads the pairing code`() {
        assertEquals("4F7K2M", InstallReferrer.pairingCode("pair=4F7K2M"))
    }

    @Test
    fun `upper-cases the code`() {
        assertEquals("4F7K2M", InstallReferrer.pairingCode("pair=4f7k2m"))
    }

    @Test
    fun `finds the code among Play's own parameters`() {
        assertEquals(
            "4F7K2M",
            InstallReferrer.pairingCode("utm_source=google-play&pair=4F7K2M&utm_medium=x")
        )
    }

    @Test
    fun `reads a referrer Play handed back still encoded`() {
        assertEquals("4F7K2M", InstallReferrer.pairingCode("pair%3D4F7K2M"))
    }

    @Test
    fun `an organic install carries no code`() {
        assertNull(InstallReferrer.pairingCode("utm_source=google-play&utm_medium=organic"))
    }

    @Test
    fun `a malformed code is no code`() {
        assertNull(InstallReferrer.pairingCode("pair=abc"))
        assertNull(InstallReferrer.pairingCode("pair=4F7K2M0"))
        assertNull(InstallReferrer.pairingCode(""))
        assertNull(InstallReferrer.pairingCode(null))
    }

    @Test
    fun `encodes the value for Play's referrer parameter`() {
        assertEquals("pair%3D4F7K2M", InstallReferrer.encode("4F7K2M"))
    }

    @Test
    fun `the referrer is used up only when its own code reaches the pairing screen`() {
        assertTrue(InstallReferrer.isHandOff(consumed = "4F7K2M", fromReferrer = "4F7K2M"))
        // A deep link the parent tapped took the slot: the referrer's code is still owed to them.
        assertFalse(InstallReferrer.isHandOff(consumed = "ABCDEF", fromReferrer = "4F7K2M"))
        assertFalse(InstallReferrer.isHandOff(consumed = "4F7K2M", fromReferrer = null))
        assertFalse(InstallReferrer.isHandOff(consumed = null, fromReferrer = "4F7K2M"))
    }
}
