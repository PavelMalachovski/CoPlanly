package com.coparently.app.domain.pairing

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
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
}
