package com.coparently.app.domain.chat

import com.coparently.app.domain.model.Message

/**
 * What stands between a chat message and its Firestore write: every file it names must be in
 * Storage first (MON-23).
 *
 * `MessageRepositoryImpl` calls [ensureUploaded] before it writes the message document, on the
 * first send and on every outbox retry. It throws when a file is not there yet — and a message
 * whose delivery throws stays `SENDING`/`ERROR` in Room, is never marked `SENT`, and is retried by
 * the outbox. That is the whole honesty argument: the co-parent can only ever receive a message
 * whose file they can open, and the sender is never told "sent" about one that is still on the
 * phone.
 */
interface AttachmentUploadGate {

    /**
     * Returns once every attachment [message] carries is stored; throws otherwise.
     *
     * A no-op for a message without attachments.
     */
    suspend fun ensureUploaded(message: Message)

    /** No attachments are ever uploaded — the default for a repository built without one. */
    object None : AttachmentUploadGate {
        override suspend fun ensureUploaded(message: Message) = Unit
    }
}
