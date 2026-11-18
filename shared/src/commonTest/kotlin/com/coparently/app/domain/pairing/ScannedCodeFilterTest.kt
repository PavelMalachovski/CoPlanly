package com.coparently.app.domain.pairing

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ScannedCodeFilterTest {

    @Test
    fun aMenuQrIsIgnored() {
        val filter = ScannedCodeFilter()

        assertNull(filter.accept("https://restaurant.example/menu"))
    }

    @Test
    fun aValidCodeIsHandedOverOnce() {
        val filter = ScannedCodeFilter()
        // What `pairingLinkFor(code, web = true)` shares: the web form of the link.
        val link = PairingUri.buildWeb("ABC234")

        assertEquals("ABC234", filter.accept(link))
        assertNull(filter.accept(link))
    }

    @Test
    fun aValidCodeAfterNoiseIsHandedOver() {
        val filter = ScannedCodeFilter()

        assertNull(filter.accept("https://restaurant.example/menu"))
        assertEquals("ABC234", filter.accept("ABC234"))
    }

    @Test
    fun aSecondCodeAfterTheFirstIsNotHandedOver() {
        val filter = ScannedCodeFilter()

        assertEquals("ABC234", filter.accept("ABC234"))
        assertNull(filter.accept(PairingUri.build("XYZ789")))
    }
}
