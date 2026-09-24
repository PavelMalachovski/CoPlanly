package com.coparently.app.wire

import com.coparently.app.data.repository.toFirestoreMap
import com.coparently.app.data.repository.toMessageOrNull
import com.coparently.app.domain.activity.ActivityAnnouncement
import com.coparently.app.domain.activity.ActivityEntityType
import com.coparently.app.domain.activity.ActivityKind
import com.coparently.app.domain.model.Message
import com.coparently.app.domain.model.MessageSendStatus
import com.coparently.app.domain.model.MessageType

/**
 * `messages`: read by `toMessageOrNull` (both chat mirrors), written by `Message.toFirestoreMap`
 * (`MessageRepositoryImpl.deliver`). A message is never edited — the rules refuse it — so the
 * round trip is this phone re-sending its own outbox; what it pins is that the send time is
 * epoch millis whichever of the two formats (CLAUDE.md item 13) the document arrived in.
 */
internal object MessageWireContract : WireContract {

    override val collection = "messages"

    override val alwaysWrites = setOf("id", "conversationId", "senderId", "timestamp", "messageType", "attachments")

    override fun read(document: Map<String, Any?>): Map<String, Any?>? {
        @Suppress("UNCHECKED_CAST")
        val message = (document as Map<String, Any>).toMessageOrNull() ?: return null
        return mapOf(
            "id" to message.id,
            "conversationId" to message.conversationId,
            "senderId" to message.senderId,
            "senderName" to message.senderName,
            "content" to message.content,
            "sentAtMillis" to message.sentAtMillis,
            "messageType" to message.messageType.name,
            "attachments" to message.attachments,
            "isRead" to message.isRead,
            "replyToMessageId" to message.replyToMessageId,
            "activityKind" to message.activity?.kind?.name,
            "activityPlanCitation" to message.activity?.planCitation
        )
    }

    override fun roundTrip(document: Map<String, Any?>): Map<String, Any?>? {
        @Suppress("UNCHECKED_CAST")
        return (document as Map<String, Any>).toMessageOrNull()?.toFirestoreMap()
    }

    override fun currentWrites(): List<CurrentWrite> = listOf(
        CurrentWrite(
            case = "text",
            about = "A text message with a reply and an attachment reference (Message.toFirestoreMap).",
            document = Message(
                id = "msg-current-1",
                conversationId = CONVERSATION,
                senderId = "uidA",
                senderName = "Alice",
                content = "Pick-up moved to 17:30",
                sentAtMillis = 1_778_580_000_000L,
                messageType = MessageType.TEXT,
                attachments = listOf(ATTACHMENT),
                replyToMessageId = "msg-0",
                status = MessageSendStatus.SENT
            ).toFirestoreMap()
        ),
        CurrentWrite(
            case = "activity-custody-proposed",
            about = "A CUSTODY_PROPOSED card carrying the parenting-plan citation the export reads (item 32).",
            document = Message(
                id = "msg-current-2",
                conversationId = CONVERSATION,
                senderId = "uidB",
                senderName = "Bob",
                content = "Custody schedule proposed",
                sentAtMillis = 1_778_583_600_000L,
                messageType = MessageType.ACTIVITY,
                activity = ActivityAnnouncement(
                    kind = ActivityKind.CUSTODY_PROPOSED,
                    entityType = ActivityEntityType.CUSTODY_PROPOSAL,
                    entityId = "uidA__uidB",
                    title = "Week on, week off",
                    planCitation = "p1|care_weekday|0123456789abcdef"
                )
            ).toFirestoreMap()
        )
    )

    private const val CONVERSATION = "uidA_uidB"

    /** A `ChatAttachmentCodec` reference, as the message carries it. */
    private val ATTACHMENT = "att1|chat_attachments/c/m/receipt.pdf|application/pdf|1024|${"0".repeat(64)}|receipt.pdf"
}
