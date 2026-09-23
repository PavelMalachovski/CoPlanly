package com.coparently.app.data.repository

import com.coparently.app.data.local.dao.MessageDao
import com.coparently.app.data.local.entity.MessageEntity
import com.coparently.app.data.remote.firebase.FirebaseAuthService
import com.coparently.app.data.remote.firebase.FirestoreMessageDataSource
import com.coparently.app.domain.chat.AttachmentUploadGate
import com.coparently.app.domain.chat.ChatAttachment
import com.coparently.app.domain.chat.ChatAttachmentCodec
import com.coparently.app.domain.model.Message
import com.coparently.app.domain.model.MessageSendStatus
import com.google.firebase.auth.FirebaseUser
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import java.io.IOException
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/**
 * MON-23: a chat message is written to Firestore only after the files it names are stored.
 *
 * The honesty claim rests on the order inside `deliver` — gate first, document second — so it is
 * pinned here rather than trusted: a failed upload must leave the row `ERROR` in the outbox and
 * must never reach `FirestoreMessageDataSource.sendMessage`.
 */
class MessageRepositoryAttachmentGateTest {

    private val messageDao = mockk<MessageDao>(relaxed = true)
    private val authService = mockk<FirebaseAuthService>()
    private val dataSource = mockk<FirestoreMessageDataSource>(relaxed = true)
    private val gate = mockk<AttachmentUploadGate>()
    private val stored = mutableListOf<MessageEntity>()

    @Before
    fun setUp() {
        mockkStatic(android.util.Log::class)
        every { android.util.Log.w(any<String>(), any<String>(), any<Throwable>()) } returns 0
        every { authService.getCurrentUser() } returns mockk<FirebaseUser> { every { uid } returns UID }
        coEvery { messageDao.insertMessage(capture(stored)) } returns Unit
        coEvery { messageDao.getConversationById(any()) } returns null
    }

    @After
    fun tearDown() {
        unmockkStatic(android.util.Log::class)
    }

    @Test
    fun `a failed upload leaves the message in the outbox and writes no document`() = runTest {
        coEvery { gate.ensureUploaded(any()) } throws IOException("offline")
        val repository = MessageRepositoryImpl(messageDao, authService, dataSource, gate)

        assertFailsWith<IOException> { repository.sendMessage(message()) }

        coVerify(exactly = 0) { dataSource.sendMessage(any(), any()) }
        assertEquals(MessageSendStatus.ERROR.name, stored.last().status)
        assertEquals(false, stored.last().syncedToFirestore)
    }

    @Test
    fun `an uploaded file lets the message through, carrying its reference`() = runTest {
        coEvery { gate.ensureUploaded(any()) } returns Unit
        val repository = MessageRepositoryImpl(messageDao, authService, dataSource, gate)

        repository.sendMessage(message())

        coVerify(exactly = 1) {
            dataSource.sendMessage(MESSAGE_ID, match { (it["attachments"] as List<*>).size == 1 })
        }
        assertEquals(MessageSendStatus.SENT.name, stored.last().status)
    }

    private fun message() = Message(
        id = MESSAGE_ID,
        conversationId = CONVERSATION_ID,
        senderId = UID,
        senderName = "Alice",
        content = "report.pdf",
        attachments = listOf(
            ChatAttachmentCodec.encode(
                ChatAttachment(
                    storagePath = ChatAttachmentCodec.storagePath(CONVERSATION_ID, MESSAGE_ID, "report.pdf"),
                    contentType = "application/pdf",
                    sizeBytes = 2048,
                    sha256 = "a".repeat(64),
                    fileName = "report.pdf"
                )
            )
        ),
        status = MessageSendStatus.SENDING
    )

    private companion object {
        const val UID = "alice"
        const val CONVERSATION_ID = "alice__bob"
        const val MESSAGE_ID = "m-1"
    }
}
