package com.coparently.app.domain.chat

import com.coparently.app.domain.activity.ActivityAnnouncement
import com.coparently.app.domain.activity.ActivityEntityType
import com.coparently.app.domain.activity.ActivityKind
import com.coparently.app.domain.model.Message
import com.coparently.app.domain.model.MessageType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ReplyQuoteTest {

    private fun original(
        content: String = "hello",
        conversationId: String = "c",
        attachments: List<String> = emptyList(),
        activity: ActivityAnnouncement? = null
    ) = Message(
        id = "m1",
        conversationId = conversationId,
        senderId = "them",
        senderName = "Bob",
        content = content,
        sentAtMillis = 1L,
        messageType = if (activity != null) MessageType.ACTIVITY else MessageType.TEXT,
        attachments = attachments,
        activity = activity
    )

    @Test
    fun `no reply id gives no quote`() {
        assertNull(ReplyQuote.of(null, original(), "c"))
    }

    @Test
    fun `an unknown original is unavailable`() {
        assertEquals(ReplyQuote.Unavailable, ReplyQuote.of("x", null, "c"))
    }

    @Test
    fun `an original of another conversation is unavailable`() {
        assertEquals(ReplyQuote.Unavailable, ReplyQuote.of("m1", original(conversationId = "other"), "c"))
    }

    @Test
    fun `a text original quotes its first line`() {
        val quote = ReplyQuote.of("m1", original(content = "Can you take her\non Friday?"), "c")

        assertEquals(shown(ReplyExcerpt.Text("Can you take her")), quote)
    }

    @Test
    fun `a file original quotes its name`() {
        val entry = ChatAttachmentCodec.encode(
            ChatAttachment(
                storagePath = "chat_attachments/c/m1/Trip-consent-form.pdf",
                contentType = "application/pdf",
                sizeBytes = 10L,
                sha256 = "a".repeat(64),
                fileName = "Trip-consent-form.pdf"
            )
        )

        val quote = ReplyQuote.of("m1", original(content = " ", attachments = listOf(entry)), "c")

        assertEquals(shown(ReplyExcerpt.File("Trip-consent-form.pdf")), quote)
    }

    @Test
    fun `a sticker card quotes a sticker`() {
        val sticker = ActivityAnnouncement(ActivityKind.STICKER_SENT, ActivityEntityType.STICKER, "s1", "thank_you")

        val quote = ReplyQuote.of("m1", original(activity = sticker), "c")

        assertEquals(shown(ReplyExcerpt.Sticker), quote)
    }

    @Test
    fun `plain cuts at max with an ellipsis`() {
        val plain = ReplyExcerpt.Text("a".repeat(100)).plain(80)

        assertEquals(81, plain.length)
        assertEquals('…', plain.last())
    }

    @Test
    fun `plain keeps a short line and gives a sticker nothing`() {
        assertEquals("short", ReplyExcerpt.Text("short").plain(80))
        assertEquals("", ReplyExcerpt.Sticker.plain(80))
        assertEquals("a.pdf", ReplyExcerpt.File("a.pdf").plain(80))
    }

    private fun fileEntry() = ChatAttachmentCodec.encode(
        ChatAttachment(
            storagePath = "chat_attachments/c/m1/form.pdf",
            contentType = "application/pdf",
            sizeBytes = 10L,
            sha256 = "a".repeat(64),
            fileName = "form.pdf"
        )
    )

    @Test
    fun `a file with a real caption quotes the caption`() {
        val quote = ReplyQuote.of("m1", original(content = "Please sign this", attachments = listOf(fileEntry())), "c")

        assertEquals(shown(ReplyExcerpt.Text("Please sign this")), quote)
    }

    @Test
    fun `leading blank lines are skipped`() {
        val quote = ReplyQuote.of("m1", original(content = "\n  \nSecond\nthird"), "c")

        assertEquals(shown(ReplyExcerpt.Text("Second")), quote)
    }

    @Test
    fun `an activity card other than a sticker is unavailable`() {
        val card = ActivityAnnouncement(ActivityKind.EXPENSE_ADDED, ActivityEntityType.EXPENSE, "e1", "Shoes")

        assertEquals(ReplyQuote.Unavailable, ReplyQuote.of("m1", original(activity = card), "c"))
    }

    @Test
    fun `a card with no readable payload is unavailable`() {
        val unreadable = original().copy(messageType = MessageType.ACTIVITY, activity = null)

        assertEquals(ReplyQuote.Unavailable, ReplyQuote.of("m1", unreadable, "c"))
    }

    @Test
    fun `an original with nothing to quote is unavailable`() {
        assertEquals(ReplyQuote.Unavailable, ReplyQuote.of("m1", original(content = "  \n "), "c"))
    }

    @Test
    fun `plain at exactly max adds no ellipsis`() {
        assertEquals("a".repeat(80), ReplyExcerpt.Text("a".repeat(80)).plain(80))
    }

    @Test
    fun `plain does not split an emoji at the cut`() {
        val plain = ReplyExcerpt.Text("a".repeat(79) + "\uD83D\uDE00 tail").plain(80)

        assertEquals("a".repeat(79) + "…", plain)
    }

    /** The quote of [original]'s message, as [ReplyQuote.of] builds it. */
    private fun shown(excerpt: ReplyExcerpt) =
        ReplyQuote.Shown("m1", "Bob", excerpt, authorId = "them", sentAtMillis = 1L)

    @Test
    fun `a quote names its original's author and time`() {
        val quote = ReplyQuote.of("m1", original(), "c") as ReplyQuote.Shown

        assertEquals("them", quote.authorId)
        assertEquals(1L, quote.sentAtMillis)
    }
}
