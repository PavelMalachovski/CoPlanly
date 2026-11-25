package com.coparently.app.domain.chat

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class NewestButtonTest {

    @Test
    fun `hidden near the newest`() {
        (0..NewestButton.NEAR_NEWEST).forEach { index ->
            assertFalse(NewestButton.shows(firstVisibleIndex = index), "shown at $index")
        }
    }

    @Test
    fun `shown far up`() {
        assertTrue(NewestButton.shows(firstVisibleIndex = NewestButton.NEAR_NEWEST + 1))
        assertTrue(NewestButton.shows(firstVisibleIndex = 40))
    }

    @Test
    fun `counts messages after the one left at`() {
        val newestFirst = listOf("m5", "m4", "m3", "m2", "m1")

        assertEquals(2, NewestButton.unseen(newestIdWhenLeft = "m3", messagesNewestFirst = newestFirst))
        assertEquals(4, NewestButton.unseen(newestIdWhenLeft = "m1", messagesNewestFirst = newestFirst))
    }

    @Test
    fun `zero when the reader left at the newest`() {
        val newestFirst = listOf("m3", "m2", "m1")

        assertEquals(0, NewestButton.unseen(newestIdWhenLeft = "m3", messagesNewestFirst = newestFirst))
        assertEquals(0, NewestButton.unseen(newestIdWhenLeft = null, messagesNewestFirst = newestFirst))
    }

    @Test
    fun `zero when the message left at is no longer in the window`() {
        assertEquals(0, NewestButton.unseen(newestIdWhenLeft = "gone", messagesNewestFirst = listOf("m2", "m1")))
    }
}
