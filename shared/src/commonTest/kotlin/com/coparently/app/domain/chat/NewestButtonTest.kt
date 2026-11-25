package com.coparently.app.domain.chat

import com.coparently.app.domain.model.Message
import com.coparently.app.domain.model.MessageSendStatus
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
    fun `only a message sent from this phone brings the newest into view`() {
        assertTrue(NewestButton.followsOwnSend(message("m1", ME).copy(status = MessageSendStatus.SENDING), ME))
        assertTrue(NewestButton.followsOwnSend(message("m1", ME).copy(status = MessageSendStatus.ERROR), ME))
        // The reader's own message from another phone arrives already sent.
        assertFalse(NewestButton.followsOwnSend(message("m1", ME).copy(status = MessageSendStatus.SENT), ME))
        assertFalse(NewestButton.followsOwnSend(message("m1", ME).copy(status = MessageSendStatus.READ), ME))
        assertFalse(NewestButton.followsOwnSend(message("m1", THEM).copy(status = MessageSendStatus.SENDING), ME))
    }

    @Test
    fun `counts messages after the one left at`() {
        val newestFirst = theirs("m5", "m4", "m3", "m2", "m1")

        assertEquals(2, NewestButton.unseen("m3", newestFirst, ME))
        assertEquals(4, NewestButton.unseen("m1", newestFirst, ME))
    }

    @Test
    fun `zero when the reader left at the newest`() {
        val newestFirst = theirs("m3", "m2", "m1")

        assertEquals(0, NewestButton.unseen("m3", newestFirst, ME))
        assertEquals(0, NewestButton.unseen(null, newestFirst, ME))
    }

    @Test
    fun `zero when the message left at is no longer in the window`() {
        assertEquals(0, NewestButton.unseen("gone", theirs("m2", "m1"), ME))
    }

    @Test
    fun `the reader's own messages are not counted`() {
        val newestFirst = listOf(message("m4", THEM), message("m3", ME), message("m2", THEM), message("m1", THEM))

        assertEquals(2, NewestButton.unseen("m1", newestFirst, ME))
        assertEquals(0, NewestButton.unseen("m4", listOf(message("m5", ME)) + newestFirst, ME))
    }

    private fun theirs(vararg ids: String) = ids.map { message(it, THEM) }

    private fun message(id: String, from: String) =
        Message(id = id, conversationId = "c1", senderId = from, senderName = from, content = id, sentAtMillis = 0L)

    private companion object {
        const val ME = "alice"
        const val THEM = "bob"
    }
}
