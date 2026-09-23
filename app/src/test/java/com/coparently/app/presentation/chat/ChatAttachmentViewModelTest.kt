package com.coparently.app.presentation.chat

import com.coparently.app.R
import com.coparently.app.data.chat.ChatAttachmentOutbox
import com.coparently.app.data.files.SharedFileCache
import com.coparently.app.data.files.SharedFileRejectedException
import com.coparently.app.data.remote.firebase.FirebaseAuthService
import com.coparently.app.domain.chat.ChatAttachment
import com.coparently.app.domain.chat.ChatAttachmentCodec
import com.coparently.app.domain.files.SharedFilePolicy
import com.coparently.app.domain.model.Message
import com.coparently.app.domain.model.MessageSendStatus
import com.coparently.app.domain.repository.MessageRepository
import com.coparently.app.domain.repository.UserRepository
import com.coparently.app.presentation.common.UiText
import com.google.firebase.auth.FirebaseUser
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.slot
import io.mockk.unmockkStatic
import io.mockk.verify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test
import kotlin.test.assertEquals

/**
 * Sending a file in chat (MON-23): the message goes into the outbox as SENDING carrying the file's
 * reference, and a file the policy refuses never becomes a message at all.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ChatAttachmentViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private val outbox = mockk<ChatAttachmentOutbox>(relaxed = true) {
        every { progress } returns MutableStateFlow(emptyMap())
    }
    private val cache = mockk<SharedFileCache>(relaxed = true)
    private val messageRepository = mockk<MessageRepository>(relaxed = true)
    private val userRepository = mockk<UserRepository> { coEvery { getCurrentUser() } returns null }
    private val authService = mockk<FirebaseAuthService> {
        every { getCurrentUser() } returns mockk<FirebaseUser> { every { uid } returns "alice" }
    }

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        mockkStatic(android.util.Log::class)
        every { android.util.Log.w(any<String>(), any<String>(), any<Throwable>()) } returns 0
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        unmockkStatic(android.util.Log::class)
    }

    private fun viewModel() = ChatAttachmentViewModel(outbox, cache, messageRepository, userRepository, authService)

    @Test
    fun `a staged file is queued as a SENDING message carrying its reference`() = runTest(dispatcher) {
        val messageId = slot<String>()
        coEvery { outbox.stage(CONVERSATION, capture(messageId), "content://x") } answers {
            attachment(messageId.captured)
        }
        val sent = slot<Message>()
        coEvery { messageRepository.sendMessage(capture(sent)) } returns Unit

        viewModel().send(CONVERSATION, "content://x")
        advanceUntilIdle()

        assertEquals(MessageSendStatus.SENDING, sent.captured.status)
        assertEquals("report.pdf", sent.captured.content)
        val carried = ChatAttachmentCodec.attachmentsOf(sent.captured.attachments)
        assertEquals(listOf(attachment(messageId.captured)), carried)
    }

    @Test
    fun `a refused file never becomes a message`() = runTest(dispatcher) {
        coEvery { outbox.stage(any(), any(), any()) } throws
            SharedFileRejectedException(SharedFilePolicy.Rejection.SIZE)
        val vm = viewModel()

        vm.send(CONVERSATION, "content://big")
        advanceUntilIdle()

        coVerify(exactly = 0) { messageRepository.sendMessage(any()) }
        verify { outbox.discard(any()) }
        assertEquals(UiText.Res(R.string.shared_file_error_size), vm.error.value)
    }

    private fun attachment(messageId: String) = ChatAttachment(
        storagePath = ChatAttachmentCodec.storagePath(CONVERSATION, messageId, "report.pdf"),
        contentType = "application/pdf",
        sizeBytes = 2048,
        sha256 = "b".repeat(64),
        fileName = "report.pdf"
    )

    private companion object {
        const val CONVERSATION = "alice__bob"
    }
}
