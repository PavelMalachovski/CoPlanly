package com.coparently.app.data.chat

import android.content.Context
import com.coparently.app.data.files.SharedFileCache
import com.coparently.app.data.files.SharedFileStager
import com.coparently.app.data.files.SharedFileStorage
import com.coparently.app.data.remote.firebase.FirebaseAuthService
import com.coparently.app.domain.chat.AttachmentUploadGate
import com.coparently.app.domain.chat.ChatAttachment
import com.coparently.app.domain.chat.ChatAttachmentCodec
import com.coparently.app.domain.model.Message
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.io.File
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Files waiting to leave the phone with their chat message (MON-23).
 *
 * A picked file is copied into `files/chat_outbox/{messageId}/` — `files`, not `cache`, because a
 * message in the outbox has to survive a restart and the system may clear a cache whenever it
 * likes — and stays there until [ensureUploaded] has put it in Storage. Only then does
 * `MessageRepositoryImpl` write the message document, so the other parent never receives a
 * message whose file is not there, and the sender never sees "sent" for one still on the phone:
 * the row stays `SENDING` or `ERROR`, and the outbox retries it like any other.
 *
 * The staged copy is app-private storage, not the SQLCipher database (CLAUDE.md item 20); it lives
 * only until the upload lands, then moves to [SharedFileCache] so the sender can open it without
 * downloading it back.
 */
@Singleton
class ChatAttachmentOutbox @Inject constructor(
    @ApplicationContext private val context: Context,
    private val stager: SharedFileStager,
    private val storage: SharedFileStorage,
    private val cache: SharedFileCache,
    private val authService: FirebaseAuthService
) : AttachmentUploadGate {

    private val root: File get() = File(context.filesDir, OUTBOX_DIRECTORY)

    private val _progress = MutableStateFlow<Map<String, Float>>(emptyMap())

    /** Upload progress by message id, 0 to 1, for the messages uploading right now. */
    val progress: StateFlow<Map<String, Float>> = _progress.asStateFlow()

    /**
     * Copies the picked file into the outbox of message [messageId] and describes it.
     *
     * @throws com.coparently.app.data.files.SharedFileRejectedException for a type or size the
     *   rules would refuse — nothing is queued then
     */
    suspend fun stage(conversationId: String, messageId: String, contentUri: String): ChatAttachment {
        val staged = stager.stage(contentUri, File(root, messageId))
        return ChatAttachment(
            storagePath = ChatAttachmentCodec.storagePath(conversationId, messageId, staged.fileName),
            contentType = staged.contentType,
            sizeBytes = staged.sizeBytes,
            sha256 = staged.sha256,
            fileName = staged.fileName
        )
    }

    /** The staged copy of [attachment] while its message is still in the outbox, else null. */
    fun stagedFile(messageId: String, attachment: ChatAttachment): File? =
        File(File(root, messageId), attachment.fileName).takeIf { it.exists() }

    /** Drops everything staged for [messageId] — the message was never queued. */
    fun discard(messageId: String) {
        File(root, messageId).deleteRecursively()
    }

    override suspend fun ensureUploaded(message: Message) {
        val attachments = ChatAttachmentCodec.attachmentsOf(message.attachments)
            .filter { ChatAttachmentCodec.belongsTo(it, message.conversationId, message.id) }
        if (attachments.isEmpty()) return
        val uid = authService.getCurrentUser()?.uid ?: throw IOException("Not signed in")
        try {
            attachments.forEach { attachment -> upload(message.id, attachment, uid) }
        } finally {
            _progress.update { it - message.id }
        }
        attachments.forEach { attachment ->
            stagedFile(message.id, attachment)?.let { cache.adopt(it, attachment.fileName, attachment.sha256) }
        }
        discard(message.id)
    }

    private suspend fun upload(messageId: String, attachment: ChatAttachment, uid: String) {
        val local = stagedFile(messageId, attachment)
        if (local == null) {
            // Staged files go only after a landed upload, so a missing one should mean the file is
            // already stored. If it is not — app data cleared mid-send — the message can never be
            // delivered, and it stays in the outbox as ERROR rather than arriving without its file.
            if (!storage.isStored(attachment.storagePath, attachment.sha256)) {
                throw IOException("The file for message $messageId is no longer on this phone")
            }
            return
        }
        storage.upload(
            path = attachment.storagePath,
            file = local,
            contentType = attachment.contentType,
            sha256 = attachment.sha256,
            uploaderUid = uid
        ) { fraction -> _progress.update { it + (messageId to fraction) } }
    }

    private companion object {
        const val OUTBOX_DIRECTORY = "chat_outbox"
    }
}
