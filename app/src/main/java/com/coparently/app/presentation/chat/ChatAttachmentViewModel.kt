package com.coparently.app.presentation.chat

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.coparently.app.R
import com.coparently.app.data.chat.ChatAttachmentOutbox
import com.coparently.app.data.files.SharedFileCache
import com.coparently.app.data.remote.firebase.FirebaseAuthService
import com.coparently.app.domain.chat.ChatAttachment
import com.coparently.app.domain.chat.ChatAttachmentCodec
import com.coparently.app.domain.model.Message
import com.coparently.app.domain.model.MessageSendStatus
import com.coparently.app.domain.model.MessageType
import com.coparently.app.domain.repository.MessageRepository
import com.coparently.app.domain.repository.UserRepository
import com.coparently.app.presentation.common.OpenedFile
import com.coparently.app.presentation.common.UiText
import com.coparently.app.presentation.common.sharedFileError
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File
import java.util.UUID
import javax.inject.Inject

/**
 * Files in the chat thread (MON-23): sending one, drawing its thumbnail, opening it.
 *
 * Separate from `ChatViewModel` on purpose — that class is at the size where one more concern
 * makes it two — and scoped to the same back-stack entry, so the composer's attach button and the
 * thread's bubbles share one instance.
 *
 * **Sending is the outbox's job, not this class's.** A file is staged, the message is written to
 * Room as `SENDING` with the file's reference, and `MessageRepository.sendMessage` delivers it —
 * which uploads the file first through `ChatAttachmentOutbox` and writes the message only after.
 * A failure leaves the row `ERROR` with its file staged, and the existing outbox retries it; the
 * bubble never claims "sent" for a file still on the phone. Attachments do not wait out the MON-19
 * pause: the confirmation dialog before sending is their deliberate step.
 */
@HiltViewModel
class ChatAttachmentViewModel @Inject constructor(
    private val outbox: ChatAttachmentOutbox,
    private val cache: SharedFileCache,
    private val messageRepository: MessageRepository,
    private val userRepository: UserRepository,
    private val authService: FirebaseAuthService
) : ViewModel() {

    /** Upload progress by message id, 0 to 1, while a file is going up. */
    val progress: StateFlow<Map<String, Float>> = outbox.progress

    private val _thumbnails = MutableStateFlow<Map<String, File>>(emptyMap())

    /** Local copies of image attachments by storage path, as they become available. */
    val thumbnails: StateFlow<Map<String, File>> = _thumbnails.asStateFlow()

    private val requested = mutableSetOf<String>()

    private val _error = MutableStateFlow<UiText?>(null)

    /** The last failure, for the screen to show once. */
    val error: StateFlow<UiText?> = _error.asStateFlow()

    private val _opened = Channel<OpenedFile>(Channel.BUFFERED)

    /** Files ready to open, one event per tap. */
    val opened: Flow<OpenedFile> = _opened.receiveAsFlow()

    /** Sends the file at [contentUri] into [conversationId] as a message of its own. */
    fun send(conversationId: String, contentUri: String) {
        viewModelScope.launch {
            val uid = authService.getCurrentUser()?.uid ?: return@launch
            val messageId = UUID.randomUUID().toString()
            val attachment = try {
                outbox.stage(conversationId, messageId, contentUri)
            } catch (e: CancellationException) {
                throw e
            } catch (
                // A provider can refuse with SecurityException as well as IOException.
                @Suppress("TooGenericExceptionCaught") e: Exception
            ) {
                outbox.discard(messageId)
                Log.w(TAG, "Staging an attachment failed", e)
                _error.value = sharedFileError(e, R.string.chat_attach_stage_error)
                return@launch
            }
            val message = Message(
                id = messageId,
                conversationId = conversationId,
                senderId = uid,
                // A stored fallback, not UI text — the same one ChatViewModel writes.
                senderName = userRepository.getCurrentUser()?.name ?: "Unknown",
                // What a build that knows nothing of attachments shows: the file's name.
                content = attachment.fileName,
                sentAtMillis = System.currentTimeMillis(),
                messageType = MessageType.TEXT,
                attachments = listOf(ChatAttachmentCodec.encode(attachment)),
                status = MessageSendStatus.SENDING
            )
            deliver(message)
        }
    }

    /** Fetches a local copy of an image attachment for its thumbnail, once per attachment. */
    fun requestThumbnail(message: Message, attachment: ChatAttachment) {
        if (!attachment.isImage || !requested.add(attachment.storagePath)) return
        viewModelScope.launch {
            val local = outbox.stagedFile(message.id, attachment)
                ?: cache.cached(attachment.fileName, attachment.sha256)
                ?: fetch(attachment)
            if (local != null) _thumbnails.update { it + (attachment.storagePath to local) }
        }
    }

    /** Opens [attachment]: the staged copy while it is still on the phone, else a checked download. */
    fun open(message: Message, attachment: ChatAttachment) {
        if (!ChatAttachmentCodec.belongsTo(attachment, message.conversationId, message.id)) return
        viewModelScope.launch {
            val local = outbox.stagedFile(message.id, attachment)
            if (local != null) {
                _opened.send(OpenedFile(local, attachment.contentType))
                return@launch
            }
            runCatching { cache.localCopy(attachment.storagePath, attachment.fileName, attachment.sha256) }
                .onSuccess { _opened.trySend(OpenedFile(it, attachment.contentType)) }
                .onFailure { e -> _error.value = sharedFileError(e, R.string.shared_file_error_open) }
        }
    }

    /** No installed app can open the file. */
    fun noViewer() {
        _error.value = UiText.Res(R.string.shared_file_no_viewer)
    }

    /** The screen has shown [error]. */
    fun errorShown() {
        _error.value = null
    }

    private suspend fun fetch(attachment: ChatAttachment): File? = try {
        cache.localCopy(attachment.storagePath, attachment.fileName, attachment.sha256)
    } catch (e: CancellationException) {
        throw e
    } catch (
        @Suppress("TooGenericExceptionCaught") e: Exception
    ) {
        Log.w(TAG, "Thumbnail download failed", e)
        null
    }

    /**
     * Hands [message] to the repository. A failure is already recorded on the row as `ERROR`, which
     * the bubble shows with its retry; here it is only logged.
     */
    private suspend fun deliver(message: Message) {
        try {
            messageRepository.sendMessage(message)
        } catch (e: CancellationException) {
            throw e
        } catch (
            @Suppress("TooGenericExceptionCaught") e: Exception
        ) {
            Log.w(TAG, "Attachment message left in the outbox", e)
        }
    }

    private companion object {
        const val TAG = "ChatAttachments"
    }
}
