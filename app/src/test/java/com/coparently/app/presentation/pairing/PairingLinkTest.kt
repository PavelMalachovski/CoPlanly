package com.coparently.app.presentation.pairing

import org.junit.Assert.assertEquals
import org.junit.Test

class PairingLinkTest {

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
