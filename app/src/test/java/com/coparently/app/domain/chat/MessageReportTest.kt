package com.coparently.app.domain.chat

import com.coparently.app.domain.activity.ActivityAnnouncement
import com.coparently.app.domain.activity.ActivityEntityType
import com.coparently.app.domain.activity.ActivityKind
import com.coparently.app.domain.model.Message
import com.coparently.app.domain.model.MessageType
import org.junit.Test
import java.net.URI
import java.net.URLDecoder
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Reporting a chat message (play-final audit F-10): which messages offer it, and the `mailto:`
 * the parent's email app is opened with.
 */
class MessageReportTest {

    private val fromCoParent = Message(
        id = "m1",
        conversationId = "a_b",
        senderId = "b",
        senderName = "Bob",
        content = "You never listen",
        sentAtMillis = 1_790_000_000_123L
    )

    // ---- which messages ---------------------------------------------------

    @Test
    fun `a co-parent's text message can be reported`() {
        assertTrue(MessageReport.isReportable(fromCoParent, isCurrentUser = false))
    }

    @Test
    fun `a message of one's own cannot`() {
        assertFalse(MessageReport.isReportable(fromCoParent, isCurrentUser = true))
    }

    @Test
    fun `a card the app composed cannot`() {
        val card = fromCoParent.copy(
            messageType = MessageType.ACTIVITY,
            activity = ActivityAnnouncement(
                kind = ActivityKind.EVENT_CREATED,
                entityType = ActivityEntityType.EVENT,
                entityId = "e1",
                title = "Dentist"
            )
        )
        assertFalse(MessageReport.isReportable(card, isCurrentUser = false))
        val link = fromCoParent.copy(messageType = MessageType.EVENT_LINK, attachments = listOf("e1"))
        assertFalse(MessageReport.isReportable(link, isCurrentUser = false))
    }

    @Test
    fun `a file the co-parent sent can be reported`() {
        val file = fromCoParent.copy(messageType = MessageType.IMAGE, content = "photo.jpg")
        assertTrue(MessageReport.isReportable(file, isCurrentUser = false))
    }

    // ---- the email ----------------------------------------------------------

    @Test
    fun `the time is a UTC instant to the second`() {
        assertEquals("2026-09-21T14:13:20Z", MessageReport.sentAtUtc(1_790_000_000_123L))
    }

    @Test
    fun `the mailto carries the address, subject and body, and decodes back to them`() {
        val subject = "CoPlanly: nahlášení zprávy"
        val body = "ID zprávy: m1\nConversation ID: a_b & more?\nSent at (UTC): 2026-09-21T14:13:20Z"
        val uri = MessageReport.mailtoUri("support@example.com", subject, body)

        assertTrue(uri.startsWith("mailto:support@example.com?subject="))
        val query = uri.substringAfter('?').split('&').associate { part ->
            part.substringBefore('=') to URLDecoder.decode(part.substringAfter('='), "UTF-8")
        }
        assertEquals(subject, query["subject"])
        assertEquals(body, query["body"])
        // A valid URI, so Uri.parse on the phone reads it the same way.
        URI(uri)
    }

    @Test
    fun `a space is percent-encoded, never a plus`() {
        val uri = MessageReport.mailtoUri("s@example.com", "a b", "1 + 1")
        assertEquals("mailto:s@example.com?subject=a%20b&body=1%20%2B%201", uri)
    }
}
