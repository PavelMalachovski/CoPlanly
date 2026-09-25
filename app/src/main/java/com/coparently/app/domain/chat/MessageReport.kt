package com.coparently.app.domain.chat

import com.coparently.app.domain.model.Message
import com.coparently.app.domain.model.MessageType
import java.net.URLEncoder
import java.time.Instant
import java.time.temporal.ChronoUnit

/**
 * Reporting a chat message (Play's user-generated content policy; play-final audit F-10).
 *
 * Deliberately the smallest thing that is a real report: the parent's own email app opens to the
 * support address with the message's id, its conversation's id and when it was sent. Nothing is
 * written to our servers, and **the message text is not put in the email** — the parent pastes it
 * if they want it read, which is why the body template says so. The ids are what lets support find
 * the message; the words are the parent's to share.
 */
object MessageReport {

    /**
     * Whether [message] can be reported by the parent reading it: one the co-parent wrote, not the
     * reader's own, and not a card the app composed (an announcement or a change-request link),
     * whose words come from this build's own resources rather than from a person.
     *
     * @param message The message on screen
     * @param isCurrentUser Whether the reader sent it
     */
    fun isReportable(message: Message, isCurrentUser: Boolean): Boolean =
        !isCurrentUser &&
            message.activity == null &&
            message.messageType != MessageType.ACTIVITY &&
            message.messageType != MessageType.EVENT_LINK

    /**
     * When the message was sent, as an ISO-8601 instant in UTC to the second
     * (`2026-09-25T10:15:30Z`). Support reads this, not the parent, so it is one unambiguous form
     * rather than the reader's clock (the export's reasoning, not the chat's).
     */
    fun sentAtUtc(sentAtMillis: Long): String =
        Instant.ofEpochMilli(sentAtMillis).truncatedTo(ChronoUnit.SECONDS).toString()

    /**
     * A `mailto:` URI (RFC 6068) addressed to [address] with [subject] and [body] prefilled.
     *
     * Every character outside the unreserved set is percent-encoded as UTF-8, and a space is `%20`,
     * never `+`: `+` means a space only in a form body, and a mail app would show it literally.
     *
     * @param address The support mailbox; used as given (it comes from the build, not a user)
     */
    fun mailtoUri(address: String, subject: String, body: String): String =
        "mailto:$address?subject=${encode(subject)}&body=${encode(body)}"

    private fun encode(text: String): String =
        URLEncoder.encode(text, Charsets.UTF_8.name())
            .replace("+", "%20")
            .replace("*", "%2A")
            .replace("%7E", "~")
}
