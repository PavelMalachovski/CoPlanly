package com.coparently.app.domain.chat

import com.coparently.app.domain.activity.ActivityKind
import com.coparently.app.domain.model.Message

/**
 * What a reply's quote shows above the reply's own words: the message it answers, or the plain
 * fact that the original can no longer be shown.
 *
 * A reply is a [Message] whose `replyToMessageId` names the original. The quote is derived from the
 * original on every read and never stored, so it cannot drift from the message it quotes.
 */
sealed interface ReplyQuote {

    /**
     * The original, drawn as a quote.
     *
     * @property messageId The original's id, which a tap on the quote scrolls to.
     * @property authorName The original's sender, as the thread names them.
     * @property excerpt What of the original is shown.
     * @property authorId The original's sender's uid, so the screen calls the reader "you" rather
     *   than naming them to themselves (design item 22, H4).
     * @property sentAtMillis When the original was sent, so a tap can size the thread's window to
     *   reach it in one step, as a search result does.
     */
    data class Shown(
        val messageId: String,
        val authorName: String,
        val excerpt: ReplyExcerpt,
        val authorId: String,
        val sentAtMillis: Long
    ) : ReplyQuote

    /** The original is not on this phone, or belongs to another thread, so nothing of it is shown. */
    data object Unavailable : ReplyQuote

    companion object {
        /** The most characters of a quoted line the thread and the record show before an ellipsis. */
        const val EXCERPT_MAX = 80

        /**
         * The quote for a message that replies to [replyToId], or null when it replies to nothing.
         *
         * @param replyToId The reply's `replyToMessageId`.
         * @param original The message that id names, as far as this phone holds it, or null.
         * @param conversationId The thread the reply is in: an original of any other thread is
         *   [Unavailable], so a forged id cannot quote another conversation. So is an original the
         *   thread does not draw (an activity card but a sticker) and one with nothing to quote.
         */
        fun of(replyToId: String?, original: Message?, conversationId: String): ReplyQuote? = when {
            replyToId == null -> null
            original == null || original.conversationId != conversationId -> Unavailable
            // A card the thread does not draw is not something a parent can have replied to.
            !isShownInThread(original) -> Unavailable
            else -> excerptOf(original)?.let {
                Shown(original.id, original.senderName, it, original.senderId, original.sentAtMillis)
            } ?: Unavailable
        }

        private fun excerptOf(original: Message): ReplyExcerpt? = ReplyExcerpt.of(
            content = original.content,
            files = ChatAttachmentCodec.attachmentsOf(original.attachments),
            isSticker = original.activity?.kind == ActivityKind.STICKER_SENT
        )
    }
}

/** The part of a quoted message the quote draws. */
sealed interface ReplyExcerpt {

    /** The first line of a message's words. */
    data class Text(val line: String) : ReplyExcerpt

    /** A message that is a file: the file's name. */
    data class File(val name: String) : ReplyExcerpt

    /** A sticker, which the screen words and draws itself. */
    data object Sticker : ReplyExcerpt

    companion object {
        /**
         * What of a message is quoted, from its facts rather than from a [Message], so the export,
         * which reads its own input type, quotes exactly what the thread does. Null when there is
         * nothing to quote (no words and no file).
         *
         * @param content The message's text.
         * @param files The files it carried.
         * @param isSticker Whether it is a sticker.
         */
        fun of(content: String, files: List<ChatAttachment>, isSticker: Boolean): ReplyExcerpt? {
            val firstLine = content.lineSequence().firstOrNull { it.isNotBlank() }?.trim().orEmpty()
            return when {
                isSticker -> Sticker
                // A file is sent with its own name as the text, so that is no words of the parent's.
                files.isNotEmpty() && (firstLine.isEmpty() || firstLine == files.first().fileName) ->
                    File(files.first().fileName)
                firstLine.isEmpty() -> null
                else -> Text(firstLine)
            }
        }
    }
}

/**
 * The excerpt as plain text of at most [max] characters, ending in an ellipsis when it was cut.
 *
 * For the export, which prints no UI words: a [ReplyExcerpt.Sticker] is the empty text and the
 * caller prints its own word.
 */
fun ReplyExcerpt.plain(max: Int): String = when (this) {
    is ReplyExcerpt.Text -> line.cutAt(max)
    is ReplyExcerpt.File -> name.cutAt(max)
    ReplyExcerpt.Sticker -> ""
}

private fun String.cutAt(max: Int): String {
    if (length <= max) return this
    val kept = take(max)
    // Never leave half of a surrogate pair (an emoji) at the cut.
    return (if (kept.lastOrNull()?.isHighSurrogate() == true) kept.dropLast(1) else kept) + "…"
}
