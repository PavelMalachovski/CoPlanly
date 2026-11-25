package com.coparently.app.domain.export

import kotlinx.datetime.TimeZone

/**
 * How a record prints a reply's quote, shared by the CSV and the PDF so the two formats read alike.
 *
 * Kept apart from [RecordFormat], which is already as large as an object may be.
 */
object RecordReplyText {

    private val placeholder = Regex("%([123])\\\$s")

    /**
     * The line a reply prints: "In reply to Alice's message of 2026-03-10 09:00:00 +01:00: “…”".
     * The template ([RecordLabels.inReplyTo]) is filled in one pass, so a name that itself reads
     * `%2$s` is printed as written.
     */
    fun line(reply: RecordReply, zone: TimeZone, labels: RecordLabels): String {
        val values = listOf(
            reply.authorName,
            RecordFormat.instant(reply.sentAtMillis, zone),
            if (reply.sticker) labels.replySticker else reply.excerpt
        )
        return placeholder.replace(labels.inReplyTo) { values[it.groupValues[1].toInt() - 1] }
    }
}
