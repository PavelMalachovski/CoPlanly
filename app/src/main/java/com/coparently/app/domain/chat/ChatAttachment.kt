package com.coparently.app.domain.chat

import com.coparently.app.domain.files.SharedFilePolicy

/**
 * A file sent in chat (MON-23): where its bytes are, what they are, and their digest.
 *
 * @property storagePath `chat_attachments/{conversationId}/{messageId}/{fileName}` — the only
 *   address the file has. Never a download URL: a token URL bypasses Storage rules, so anyone
 *   who ever saw one could fetch the file for good.
 * @property contentType One of [SharedFilePolicy.CONTENT_TYPES].
 * @property sizeBytes Size of the bytes at [storagePath].
 * @property sha256 Lowercase hex SHA-256 of those bytes — what the export prints, and what a
 *   download is checked against.
 * @property fileName The name shown in the bubble, already a safe path segment.
 */
data class ChatAttachment(
    val storagePath: String,
    val contentType: String,
    val sizeBytes: Long,
    val sha256: String,
    val fileName: String
) {
    /** True for an image, drawn as a thumbnail; a PDF is drawn as a chip. */
    val isImage: Boolean get() = SharedFilePolicy.isImage(contentType)
}

/**
 * The wire form of a [ChatAttachment]: one string in the `attachments` list `Message` has always
 * carried, in Room (`attachmentsJson`) and in Firestore.
 *
 * **Why a string in an existing list, and not a new field.** A field would need a Room column,
 * which is a schema version this change does not take; the list is already stored in both places
 * and already survives a round trip unchanged. Its other occupants are event ids (a change-request
 * card, an announcement), which are UUIDs and can never start with [PREFIX], so the two kinds
 * cannot be mistaken for each other. A build that predates this reads the entry as an opaque
 * string it never looks at, and shows the message's `content` — the file name — as text.
 *
 * Format: `att1|{storagePath}|{contentType}|{sizeBytes}|{sha256}|{fileName}`. The name is last so
 * the split is bounded, though [SharedFilePolicy.safeFileName] already removed every `|`.
 * Never Gson over the data class: R8 renamed a Gson model's fields once already and it shipped.
 */
object ChatAttachmentCodec {

    /** Marks an entry as an attachment reference; the `1` is the format version. */
    const val PREFIX = "att1|"

    /** Fields after the prefix: path, type, size, digest, name. */
    private const val FIELD_COUNT = 5
    private const val SEPARATOR = '|'
    private const val SHA256_HEX_LENGTH = 64
    private val HEX = Regex("^[0-9a-f]+$")

    /** The stored string for [attachment]. */
    fun encode(attachment: ChatAttachment): String = listOf(
        attachment.storagePath,
        attachment.contentType,
        attachment.sizeBytes.toString(),
        attachment.sha256,
        attachment.fileName
    ).joinToString(SEPARATOR.toString(), prefix = PREFIX)

    /** The attachment [entry] describes, or null for an event id or a malformed entry. */
    fun decode(entry: String): ChatAttachment? {
        val parts = entry.takeIf { it.startsWith(PREFIX) }
            ?.removePrefix(PREFIX)
            ?.split(SEPARATOR, limit = FIELD_COUNT)
            ?.takeIf { it.size == FIELD_COUNT }
            ?: return null
        val (path, type, sizeText, sha, name) = parts
        val size = sizeText.toLongOrNull() ?: return null
        return ChatAttachment(
            storagePath = path,
            contentType = type,
            sizeBytes = size,
            sha256 = sha,
            fileName = name
        ).takeIf { isWellFormed(it) }
    }

    /** Every attachment in a message's `attachments` list, skipping event ids. */
    fun attachmentsOf(entries: List<String>): List<ChatAttachment> = entries.mapNotNull(::decode)

    /** Where a message's file lives — derived, like every other id in this app. */
    fun storagePath(conversationId: String, messageId: String, fileName: String): String =
        "chat_attachments/$conversationId/$messageId/$fileName"

    /**
     * True when [attachment] is stored under the message that carries it.
     *
     * The Storage rule is the gate on the bytes; this is the client refusing to *follow* a
     * reference a sender pointed somewhere else — at another thread's file, or at a vault
     * document — so a bubble can only ever open the file its own message uploaded.
     */
    fun belongsTo(attachment: ChatAttachment, conversationId: String, messageId: String): Boolean =
        attachment.storagePath == storagePath(conversationId, messageId, attachment.fileName)

    private fun isWellFormed(attachment: ChatAttachment): Boolean =
        attachment.storagePath.startsWith("chat_attachments/") &&
            attachment.contentType in SharedFilePolicy.CONTENT_TYPES &&
            attachment.sizeBytes > 0 &&
            attachment.sha256.length == SHA256_HEX_LENGTH &&
            HEX.matches(attachment.sha256) &&
            attachment.fileName.isNotBlank()
}
