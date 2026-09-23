package com.coparently.app.domain.chat

import com.coparently.app.domain.export.RecordFormat
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The MON-23 wire form of a chat attachment: one string in the `attachments` list messages have
 * always carried, beside the event ids a change-request card puts there.
 */
class ChatAttachmentCodecTest {

    private val attachment = ChatAttachment(
        storagePath = ChatAttachmentCodec.storagePath("alice__bob", "m-1", "report.pdf"),
        contentType = "application/pdf",
        sizeBytes = 2048,
        sha256 = "0123456789abcdef".repeat(4),
        fileName = "report.pdf"
    )

    @Test
    fun `a reference survives the round trip unchanged`() {
        val encoded = ChatAttachmentCodec.encode(attachment)
        assertTrue(encoded.startsWith(ChatAttachmentCodec.PREFIX))
        assertEquals(attachment, ChatAttachmentCodec.decode(encoded))
    }

    @Test
    fun `an event id in the same list is not an attachment`() {
        val entries = listOf("5f0c1d7e-0000-4000-8000-000000000000", ChatAttachmentCodec.encode(attachment))
        assertEquals(listOf(attachment), ChatAttachmentCodec.attachmentsOf(entries))
    }

    @Test
    fun `a malformed reference is dropped, not guessed at`() {
        assertNull(ChatAttachmentCodec.decode("att1|chat_attachments/a/b/c.pdf|application/pdf|x|y|c.pdf"))
        assertNull(ChatAttachmentCodec.decode("att1|elsewhere/a.pdf|application/pdf|1|" + "a".repeat(64) + "|a.pdf"))
        val zip = "att1|chat_attachments/a/b/c.zip|application/zip|1|" + "a".repeat(64) + "|c"
        assertNull(ChatAttachmentCodec.decode(zip))
        assertNull(ChatAttachmentCodec.decode("att1|too|few"))
    }

    @Test
    fun `a reference pointing at another message's file does not belong to this one`() {
        assertTrue(ChatAttachmentCodec.belongsTo(attachment, "alice__bob", "m-1"))
        assertFalse(ChatAttachmentCodec.belongsTo(attachment, "alice__bob", "m-2"))
        assertFalse(ChatAttachmentCodec.belongsTo(attachment, "alice__carol", "m-1"))
    }

    @Test
    fun `the export lists a file by name and digest and does not repeat the fallback text`() {
        assertEquals(
            "report.pdf (SHA-256 ${attachment.sha256})",
            RecordFormat.messageText("report.pdf", listOf(attachment))
        )
        assertEquals(
            "See attached\nreport.pdf (SHA-256 ${attachment.sha256})",
            RecordFormat.messageText("See attached", listOf(attachment))
        )
        assertEquals("Just words", RecordFormat.messageText("Just words", emptyList()))
    }
}
