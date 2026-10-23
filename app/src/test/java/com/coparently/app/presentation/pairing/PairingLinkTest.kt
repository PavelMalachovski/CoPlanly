package com.coparently.app.presentation.pairing

import com.coparently.app.BuildConfig
import com.coparently.app.domain.pairing.PairingUri
import org.junit.Assert.assertEquals
import org.junit.Test

/** The share text and the QR carry the link [pairingLinkFor] picks, in whichever form the build chose. */
class PairingLinkTest {

    @Test
    fun `the web form when the build shares web links`() {
        assertEquals("https://coparently-a39c9.web.app/pair/4F7K2M", pairingLinkFor("4F7K2M", web = true))
    }

    @Test
    fun `the app-only form when it does not`() {
        assertEquals("coplanly://pair?code=4F7K2M", pairingLinkFor("4F7K2M", web = false))
    }

    @Test
    fun `the default follows the build flag`() {
        val expected = if (BuildConfig.PAIRING_WEB_LINK) PairingUri.buildWeb("4F7K2M") else PairingUri.build("4F7K2M")
        assertEquals(expected, pairingLinkFor("4F7K2M"))
    }

    @Test
    fun `the Play link carries the code as an install referrer`() {
        assertEquals(
            "https://play.google.com/store/apps/details?id=app.coplanly&referrer=pair%3D4F7K2M",
            playStoreUrlFor("4F7K2M")
        )
    }

    @Test
    fun `without a code the Play link is the plain listing`() {
        assertEquals(PLAY_STORE_URL, playStoreUrlFor(null))
    }
}
