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
     */
    data class Shown(val messageId: String, val authorName: String, val excerpt: ReplyExcerpt) : ReplyQuote

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
         *   [Unavailable], so a forged id cannot quote another conversation.
         */
        fun of(replyToId: String?, original: Message?, conversationId: String): ReplyQuote? {
            if (replyToId == null) return null
            if (original == null || original.conversationId != conversationId) return Unavailable
            return Shown(original.id, original.senderName, excerptOf(original))
        }

        private fun excerptOf(original: Message): ReplyExcerpt {
            val files = ChatAttachmentCodec.attachmentsOf(original.attachments)
            val firstLine = original.content.lineSequence().firstOrNull { it.isNotBlank() }?.trim().orEmpty()
            return when {
                original.activity?.kind == ActivityKind.STICKER_SENT -> ReplyExcerpt.Sticker
                // A file is sent with its own name as the text, so that is no words of the parent's.
                files.isNotEmpty() && (firstLine.isEmpty() || firstLine == files.first().fileName) ->
                    ReplyExcerpt.File(files.first().fileName)
                else -> ReplyExcerpt.Text(firstLine)
            }
        }
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

private fun String.cutAt(max: Int): String = if (length <= max) this else take(max) + "…"
