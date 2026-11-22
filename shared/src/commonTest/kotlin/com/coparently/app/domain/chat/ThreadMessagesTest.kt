package com.coparently.app.domain.chat

import com.coparently.app.domain.activity.ActivityAnnouncement
import com.coparently.app.domain.activity.ActivityEntityType
import com.coparently.app.domain.activity.ActivityKind
import com.coparently.app.domain.model.Message
import com.coparently.app.domain.model.MessageType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ThreadMessagesTest {

    private fun message(type: MessageType = MessageType.TEXT, activity: ActivityAnnouncement? = null) = Message(
        id = "m1",
        conversationId = "c1",
        senderId = "them",
        senderName = "Bob",
        content = "hello",
        sentAtMillis = 1L,
        messageType = type,
        activity = activity
    )

    private fun card(kind: ActivityKind) =
        message(MessageType.ACTIVITY, ActivityAnnouncement(kind, ActivityEntityType.EVENT, "e1", "Dentist"))

    @Test
    fun `a parent's text message is drawn`() {
        assertTrue(isShownInThread(message()))
    }

    @Test
    fun `an image or a voice message is drawn`() {
        assertTrue(isShownInThread(message(MessageType.IMAGE)))
        assertTrue(isShownInThread(message(MessageType.VOICE)))
        assertTrue(isShownInThread(message(MessageType.EVENT_LINK)))
    }

    @Test
    fun `a sticker is the one card drawn`() {
        assertTrue(isShownInThread(card(ActivityKind.STICKER_SENT)))
    }

    @Test
    fun `every other card kind is kept out of the thread`() {
        val hidden = ActivityKind.entries.filter { !isShownInThread(card(it)) }

        assertEquals(ActivityKind.entries - ActivityKind.STICKER_SENT, hidden)
    }

    @Test
    fun `a card whose payload this build cannot read is kept out of the thread`() {
        assertFalse(isShownInThread(message(MessageType.ACTIVITY, activity = null)))
    }

    @Test
    fun `a sticker is drawn whatever type its row was read as`() {
        val sticker = ActivityAnnouncement(ActivityKind.STICKER_SENT, ActivityEntityType.STICKER, "s1", "thank_you")

        assertTrue(isShownInThread(message(MessageType.TEXT, sticker)))
    }
}
