package com.coparently.app.presentation.chat

import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import app.cash.turbine.test
import com.coparently.app.data.chat.ChatPartnerSource
import com.coparently.app.data.chat.DepartedThreadSource
import com.coparently.app.data.family.SelectedFamilySource
import com.coparently.app.data.platform.SecurePreferences
import com.coparently.app.domain.chat.ConversationKey
import com.coparently.app.domain.chat.ReplyExcerpt
import com.coparently.app.domain.chat.ReplyQuote
import com.coparently.app.domain.model.Message
import com.coparently.app.domain.model.MessageType
import com.coparently.app.domain.model.PairingState
import com.coparently.app.domain.model.PartnerSummary
import com.coparently.app.domain.repository.EventRepository
import com.coparently.app.domain.repository.MessageRepository
import com.coparently.app.domain.repository.PairingRepository
import com.coparently.app.domain.repository.PreferencesRepository
import com.coparently.app.domain.repository.UserRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

/**
 * [ChatViewModel]'s replies: the message the composer answers is kept per conversation beside the
 * draft, stamped on what is sent, given back by an Undo, and never carried into another thread.
 * The quotes the thread draws come from the same ViewModel.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ChatViewModelReplyTest {

    private val dispatcher = StandardTestDispatcher()
    private val pauseBeforeSending = MutableStateFlow(false)
    private lateinit var messageRepository: MessageRepository

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        pauseBeforeSending.value = false
        messageRepository = mockk(relaxed = true) {
            every { observeConversation(any()) } returns MutableStateFlow(null)
            every { observeMessages(any(), any()) } returns flowOf(emptyList())
            coEvery { ensureConversation(any(), any(), any()) } answers {
                ConversationKey.of(firstArg(), secondArg())
            }
            coEvery { getMessageById(any()) } returns null
        }
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    // ---- replies: the chosen original, kept per conversation beside the draft ----

    @Test
    fun `startReply then send stamps the reply and clears it`() = runTest {
        val preferences = replyStore()
        val viewModel = openedThread(preferences)

        viewModel.startReply(message())
        runCurrent()
        assertEquals(shown(), viewModel.replyTarget.value)
        assertEquals("message-1", preferences.getChatReplyDraft(CONVERSATION))

        viewModel.sendMessage(DRAFT)
        advanceUntilIdle()

        coVerify(exactly = 1) {
            messageRepository.sendMessage(match { it.content == DRAFT && it.replyToMessageId == "message-1" })
        }
        assertNull(viewModel.replyTarget.value)
        assertEquals("", preferences.getChatReplyDraft(CONVERSATION))
    }

    @Test
    fun `a message sent after the reply was cancelled is no reply`() = runTest {
        val preferences = replyStore()
        val viewModel = openedThread(preferences)

        viewModel.startReply(message())
        viewModel.cancelReply()
        viewModel.sendMessage(DRAFT)
        advanceUntilIdle()

        assertNull(viewModel.replyTarget.value)
        assertEquals("", preferences.getChatReplyDraft(CONVERSATION))
        coVerify(exactly = 1) { messageRepository.sendMessage(match { it.replyToMessageId == null }) }
    }

    @Test
    fun `a reply to a message the thread does not draw is not started`() = runTest {
        val preferences = replyStore()
        val viewModel = openedThread(preferences)

        viewModel.startReply(message().copy(content = ""))
        runCurrent()

        assertNull(viewModel.replyTarget.value)
        assertEquals("", preferences.getChatReplyDraft(CONVERSATION))
    }

    @Test
    fun `a send before the restore has read Room leaves no reply behind`() = runTest {
        val preferences = replyStore()
        preferences.putChatReplyDraft(CONVERSATION, "message-1")
        val roomRead = CompletableDeferred<Unit>()
        coEvery { messageRepository.getMessageById("message-1") } coAnswers {
            roomRead.await()
            message()
        }
        val viewModel = createViewModel(preferences)
        advanceUntilIdle()

        viewModel.onThreadOpened(CONVERSATION)
        runCurrent()
        viewModel.sendMessage(DRAFT)
        runCurrent()
        roomRead.complete(Unit)
        advanceUntilIdle()

        assertNull("the restore must not put the reply bar up after a send", viewModel.replyTarget.value)
        assertEquals("", preferences.getChatReplyDraft(CONVERSATION))
    }

    @Test
    fun `a message that is not text neither carries nor consumes the reply`() = runTest {
        val preferences = replyStore()
        val viewModel = openedThread(preferences)
        viewModel.startReply(message())

        viewModel.sendMessage("a card", type = MessageType.ACTIVITY)
        advanceUntilIdle()

        coVerify(exactly = 1) { messageRepository.sendMessage(match { it.replyToMessageId == null }) }
        assertEquals(shown(), viewModel.replyTarget.value)
        assertEquals("message-1", preferences.getChatReplyDraft(CONVERSATION))
    }

    @Test
    fun `a reply survives reopening the conversation`() = runTest {
        val preferences = replyStore()
        openedThread(preferences).startReply(message())
        coEvery { messageRepository.getMessageById("message-1") } returns message()

        val reopened = createViewModel(preferences)
        advanceUntilIdle()
        assertNull("nothing is restored before the thread opens", reopened.replyTarget.value)
        reopened.onThreadOpened(CONVERSATION)
        advanceUntilIdle()

        assertEquals(shown(), reopened.replyTarget.value)
    }

    @Test
    fun `a stored reply whose message is gone is dropped`() = runTest {
        val preferences = replyStore()
        preferences.putChatReplyDraft(CONVERSATION, "message-gone")
        val viewModel = createViewModel(preferences)
        advanceUntilIdle()

        viewModel.onThreadOpened(CONVERSATION)
        advanceUntilIdle()

        assertNull(viewModel.replyTarget.value)
        assertEquals("", preferences.getChatReplyDraft(CONVERSATION))
    }

    @Test
    fun `opening another conversation drops a reply that is not its own`() = runTest {
        val preferences = replyStore()
        // A reply stored for the second thread that names a message of the first: nothing in
        // the app writes that, so it can only be a corrupt or forged store.
        preferences.putChatReplyDraft(OTHER_CONVERSATION, "message-1")
        coEvery { messageRepository.getMessageById("message-1") } returns message()
        val viewModel = createViewModel(preferences)
        advanceUntilIdle()

        viewModel.onThreadOpened(OTHER_CONVERSATION)
        advanceUntilIdle()
        viewModel.sendMessage(DRAFT)
        advanceUntilIdle()

        assertNull(viewModel.replyTarget.value)
        assertEquals("", preferences.getChatReplyDraft(OTHER_CONVERSATION))
        coVerify(exactly = 1) {
            messageRepository.sendMessage(
                match { it.conversationId == OTHER_CONVERSATION && it.replyToMessageId == null }
            )
        }
    }

    @Test
    fun `a reply chosen in one conversation is never sent in another`() = runTest {
        val preferences = replyStore()
        val viewModel = openedThread(preferences)
        viewModel.startReply(message())

        viewModel.onThreadOpened(OTHER_CONVERSATION)
        advanceUntilIdle()
        assertNull(viewModel.replyTarget.value)
        viewModel.sendMessage(DRAFT)
        advanceUntilIdle()

        coVerify(exactly = 1) {
            messageRepository.sendMessage(
                match { it.conversationId == OTHER_CONVERSATION && it.replyToMessageId == null }
            )
        }
        // Still waiting in the thread it was chosen in.
        assertEquals("message-1", preferences.getChatReplyDraft(CONVERSATION))
        viewModel.onThreadOpened(CONVERSATION)
        advanceUntilIdle()
        assertEquals(shown(), viewModel.replyTarget.value)
    }

    @Test
    fun `undoing a held send restores the reply`() = runTest {
        pauseBeforeSending.value = true
        val preferences = replyStore()
        val viewModel = openedThread(preferences)
        viewModel.startReply(message())

        viewModel.sendMessage(DRAFT)
        runCurrent()
        assertNull("the composer lets the reply go with the text", viewModel.replyTarget.value)

        val restored = viewModel.undoPendingSend(composerText = "")
        advanceUntilIdle()

        assertEquals(DRAFT, restored)
        assertEquals(shown(), viewModel.replyTarget.value)
        assertEquals("message-1", preferences.getChatReplyDraft(CONVERSATION))
        coVerify(exactly = 0) { messageRepository.sendMessage(any()) }
    }

    @Test
    fun `a held reply is sent as a reply when the pause ends`() = runTest {
        pauseBeforeSending.value = true
        val viewModel = openedThread()
        viewModel.startReply(message())

        viewModel.sendMessage(DRAFT)
        advanceUntilIdle()

        coVerify(exactly = 1) { messageRepository.sendMessage(match { it.replyToMessageId == "message-1" }) }
        assertNull(viewModel.replyTarget.value)
    }

    @Test
    fun `leaving the thread mid-pause keeps the reply with the text`() = runTest {
        pauseBeforeSending.value = true
        val preferences = replyStore()
        val store = ViewModelStore()
        val viewModel = ViewModelProvider.create(
            store,
            viewModelFactory { initializer { createViewModel(preferences) } }
        )[ChatViewModel::class]
        advanceUntilIdle()
        viewModel.onThreadOpened(CONVERSATION)
        advanceUntilIdle()
        viewModel.startReply(message())
        viewModel.sendMessage(DRAFT)
        runCurrent()

        // onCleared is protected; clearing the store it lives in is how the framework reaches it.
        store.clear()

        assertEquals("message-1", preferences.getChatReplyDraft(CONVERSATION))
    }

    @Test
    fun `quotes holds each reply's original from the window or from Room`() = runTest {
        val inWindow = message().copy(id = "reply-1", senderId = UID, replyToMessageId = "message-1")
        val older = message().copy(id = "reply-2", senderId = UID, replyToMessageId = "message-0")
        val orphan = message().copy(id = "reply-3", senderId = UID, replyToMessageId = "message-missing")
        val plain = message().copy(id = "plain")
        every { messageRepository.observeMessages(CONVERSATION, any()) } returns
            flowOf(listOf(message(), inWindow, older, orphan, plain))
        coEvery { messageRepository.getMessageById("message-0") } returns
            message().copy(id = "message-0", content = "Earlier")
        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.quotes.test {
            viewModel.onThreadOpened(CONVERSATION)
            advanceUntilIdle()
            val quotes = expectMostRecentItem()
            assertEquals(shown(), quotes["reply-1"])
            assertEquals(
                ReplyQuote.Shown("message-0", "Bob Novak", ReplyExcerpt.Text("Earlier")),
                quotes["reply-2"]
            )
            assertEquals(ReplyQuote.Unavailable, quotes["reply-3"])
            assertNull("a message that replies to nothing has no quote", quotes["plain"])
            assertNull(quotes["message-1"])
        }
    }

    /** What the quote of [message] shows, for the reply tests. */
    private fun shown() = ReplyQuote.Shown("message-1", "Bob Novak", ReplyExcerpt.Text("Can we swap Friday?"))

    /** An in-memory reply store over the mocked draft store: what a device that kept it would hold. */
    private fun replyStore(): SecurePreferences {
        val replies = mutableMapOf<String, String>()
        return mockk<SecurePreferences>(relaxed = true) {
            every { getChatDraft(any()) } returns ""
            every { putChatReplyDraft(any(), any()) } answers { replies[firstArg()] = secondArg() }
            every { getChatReplyDraft(any()) } answers { replies[firstArg()].orEmpty() }
        }
    }

    /** A ViewModel with [CONVERSATION] open and everything it started on construction settled. */
    private fun TestScope.openedThread(preferences: SecurePreferences = replyStore()): ChatViewModel {
        val viewModel = createViewModel(preferences)
        advanceUntilIdle()
        viewModel.onThreadOpened(CONVERSATION)
        advanceUntilIdle()
        return viewModel
    }

    private fun createViewModel(preferences: SecurePreferences = replyStore()): ChatViewModel {
        val userRepository = mockk<UserRepository>(relaxed = true) {
            every { observeCurrentUserId() } returns MutableStateFlow<String?>(UID)
        }
        val eventRepository = mockk<EventRepository> {
            every { getEventsByDateRange(any(), any()) } returns flowOf(emptyList())
        }
        val pairingRepository = mockk<PairingRepository> {
            every { observePairingState() } returns MutableStateFlow(
                PairingState.Paired(
                    PartnerSummary(
                        id = PARTNER,
                        name = "Bob Novak",
                        email = "bob@example.com",
                        pairedSinceMillis = null
                    )
                )
            )
        }
        val selectedFamilySource = mockk<SelectedFamilySource> {
            every { observe(any()) } returns MutableStateFlow(null)
        }
        coEvery { userRepository.getUserById(any()) } returns null
        return ChatViewModel(
            messageRepository,
            userRepository,
            eventRepository,
            ChatPartnerSource(userRepository, pairingRepository, selectedFamilySource),
            preferences,
            mockk<PreferencesRepository> {
                every { getPauseBeforeSendingFlow() } returns pauseBeforeSending
            },
            mockk<DepartedThreadSource> {
                every { observe() } returns flowOf(emptyList())
            }
        )
    }

    private fun message() = Message(
        id = "message-1",
        conversationId = CONVERSATION,
        senderId = PARTNER,
        senderName = "Bob Novak",
        content = "Can we swap Friday?",
        sentAtMillis = SENT_AT_MILLIS,
        messageType = MessageType.TEXT
    )

    private companion object {
        const val UID = "user-a"
        const val PARTNER = "user-b"

        /** What `ConversationKey.of(UID, PARTNER)` derives; kept literal so the test pins it. */
        const val CONVERSATION = "user-a__user-b"
        const val OTHER_CONVERSATION = "conversation-2"

        /** Unsent text in the composer. */
        const val DRAFT = "are you free on Friday?"

        /** 2026-08-01T10:00:00Z — the test message's send instant. */
        const val SENT_AT_MILLIS = 1_785_578_400_000L
    }
}
