package com.coparently.app.presentation.changerequests

import androidx.lifecycle.viewModelScope
import com.coparently.app.R
import com.coparently.app.domain.activity.ActivityAnnouncement
import com.coparently.app.domain.activity.ActivityAnnouncer
import com.coparently.app.domain.activity.ActivityKind
import com.coparently.app.domain.changerequests.AnswerByPick
import com.coparently.app.domain.changerequests.AnswerByPreset
import com.coparently.app.domain.chat.ConversationKey
import com.coparently.app.domain.model.ChangeRequest
import com.coparently.app.domain.model.Event
import com.coparently.app.domain.model.Message
import com.coparently.app.domain.model.User
import com.coparently.app.domain.repository.ChangeRequestRepository
import com.coparently.app.domain.repository.EventRepository
import com.coparently.app.domain.repository.MessageRepository
import com.coparently.app.domain.repository.UserRepository
import com.coparently.app.presentation.common.UiText
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withContext
import org.junit.After
import org.junit.Before
import org.junit.Test
import java.time.LocalDateTime
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Proposing a new time for an event: who it goes to, what the co-parent's thread is told and in
 * which family's thread, the deadline the parent chose (phase 3a), what survives the parent leaving
 * the form once the request is written, and a localised error — never the exception's text — when
 * it cannot be recorded.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class RequestChangeViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private val eventRepository = mockk<EventRepository>()
    private val changeRequestRepository = mockk<ChangeRequestRepository>()
    private val userRepository = mockk<UserRepository>()
    private val announcer = mockk<ActivityAnnouncer>(relaxed = true)

    private val me = User(
        id = "u1",
        email = "olya@example.com",
        name = "Olya",
        role = "mom",
        colorCode = "",
        partnerId = "u2"
    )
    private val event = Event(
        id = "e1",
        title = "Dentist",
        startDateTime = LocalDateTime.of(2026, 9, 10, 9, 0),
        endDateTime = LocalDateTime.of(2026, 9, 10, 10, 0),
        eventType = "medical",
        parentOwner = "mom",
        createdAt = LocalDateTime.of(2026, 9, 1, 9, 0),
        updatedAt = LocalDateTime.of(2026, 9, 1, 9, 0)
    )
    private val proposedStart = LocalDateTime.of(2026, 9, 11, 9, 0)

    private fun viewModel() =
        RequestChangeViewModel(eventRepository, changeRequestRepository, userRepository, announcer)

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `an event that is not on this device is said to be missing`() = runTest(dispatcher) {
        coEvery { eventRepository.getEventById("gone") } returns null
        val vm = viewModel()

        vm.loadEvent("gone")
        advanceUntilIdle()

        assertEquals(RequestChangeUiState.Error(UiText.Res(R.string.event_form_not_found)), vm.uiState.value)
    }

    @Test
    fun `an unpaired parent is told so and nothing is written`() = runTest(dispatcher) {
        coEvery { userRepository.getCurrentUser() } returns me.copy(partnerId = null)
        val vm = viewModel()

        vm.submit(event, proposedStart, null, null)
        advanceUntilIdle()

        assertEquals(
            RequestChangeUiState.Error(UiText.Res(R.string.change_request_error_not_paired)),
            vm.uiState.value
        )
        coVerify(exactly = 0) { changeRequestRepository.createChangeRequest(any()) }
    }

    @Test
    fun `a request goes to the co-parent and is announced on the event`() = runTest(dispatcher) {
        coEvery { userRepository.getCurrentUser() } returns me
        val request = slot<ChangeRequest>()
        coEvery { changeRequestRepository.createChangeRequest(capture(request)) } returns Unit
        val announcement = slot<ActivityAnnouncement>()
        coEvery { announcer.announce(capture(announcement), any(), any(), any()) } returns Unit
        val vm = viewModel()

        vm.submit(event, proposedStart, null, note = "  ")
        advanceUntilIdle()

        assertEquals(RequestChangeUiState.Saved, vm.uiState.value)
        assertEquals("u1", request.captured.requestedBy)
        assertEquals("u2", request.captured.requestedTo)
        assertEquals(event.startDateTime, request.captured.currentStartDateTime)
        assertEquals(proposedStart, request.captured.proposedStartDateTime)
        assertNull(request.captured.note)
        assertEquals(ActivityKind.CHANGE_REQUESTED, announcement.captured.kind)
        // The event's id, not the request's: one event collects several requests over its life.
        assertEquals("e1", announcement.captured.entityId)
        // To the thread of the family the request was sent in: u2, the co-parent it was addressed to.
        coVerify(exactly = 1) { announcer.announce(any(), "Olya", any(), "u2") }
    }

    @Test
    fun `submit passes the deadline on`() = runTest(dispatcher) {
        coEvery { userRepository.getCurrentUser() } returns me
        coEvery { changeRequestRepository.createChangeRequest(any(), any()) } returns Unit
        val aWeek = AnswerByPick.Preset(AnswerByPreset.WEEK)
        val vm = viewModel()

        vm.submit(event, proposedStart, null, null, answerBy = aWeek)
        advanceUntilIdle()

        // The pick travels as chosen: the repository resolves it when the request is sent, because
        // what a deadline is depends on that moment and on the request's cap.
        assertEquals(RequestChangeUiState.Saved, vm.uiState.value)
        coVerify(exactly = 1) {
            changeRequestRepository.createChangeRequest(match { it.eventId == "e1" && it.requestedTo == "u2" }, aWeek)
        }
    }

    @Test
    fun `submit without a deadline asks for none`() = runTest(dispatcher) {
        coEvery { userRepository.getCurrentUser() } returns me
        coEvery { changeRequestRepository.createChangeRequest(any(), any()) } returns Unit
        val vm = viewModel()

        vm.submit(event, proposedStart, null, null)
        advanceUntilIdle()

        // Null is today's behaviour exactly: no clock, and the push unchanged.
        coVerify(exactly = 1) { changeRequestRepository.createChangeRequest(any(), null) }
    }

    @Test
    fun `a failed write is a localised error and is not announced`() = runTest(dispatcher) {
        coEvery { userRepository.getCurrentUser() } returns me
        coEvery { changeRequestRepository.createChangeRequest(any()) } throws IllegalStateException("PERMISSION_DENIED")
        val vm = viewModel()

        vm.submit(event, proposedStart, null, null)
        advanceUntilIdle()

        assertEquals(
            RequestChangeUiState.Error(UiText.Res(R.string.change_request_error_send_failed)),
            vm.uiState.value
        )
        coVerify(exactly = 0) { announcer.announce(any(), any(), any(), any()) }
    }

    // ---- leaving the form while the request is sent -----------------------------------------
    //
    // The up arrow stays enabled while a request is being sent, and the request's clock waits for the
    // server for up to ten seconds, so both the family on screen and the screen itself can change
    // between the request being written and its card being posted.

    @Test
    fun `the card goes to the thread of the family the request was sent in, though another is on screen by then`() =
        runTest(dispatcher) {
            // Olya is in two families, with Pavel (u2) and with Kim (u3). She sends to Pavel's, which is on
            // screen; while the request is written a push tap switches the screen to Kim's.
            var onScreen = "u2"
            coEvery { userRepository.getCurrentUser() } answers { me.copy(partnerId = onScreen) }
            coEvery { userRepository.getCurrentUserId() } returns "u1"
            coEvery { userRepository.getUserById("u1") } answers { me.copy(partnerId = onScreen) }
            val request = slot<ChangeRequest>()
            coEvery { changeRequestRepository.createChangeRequest(capture(request), any()) } coAnswers {
                onScreen = "u3"
            }
            // The real announcer, which finds the thread from the user's row unless it is told whose it is.
            val sent = slot<Message>()
            val messages = mockk<MessageRepository> { coEvery { sendMessage(capture(sent)) } returns Unit }
            val vm = RequestChangeViewModel(
                eventRepository,
                changeRequestRepository,
                userRepository,
                ActivityAnnouncer(messages, userRepository)
            )

            vm.submit(event, proposedStart, null, null)
            advanceUntilIdle()

            assertEquals("u3", onScreen)
            assertEquals("u2", request.captured.requestedTo)
            assertEquals(ConversationKey.of("u1", "u2"), sent.captured.conversationId)
        }

    @Test
    fun `the card is still posted when the screen is left after the request was written`() = runTest(dispatcher) {
        coEvery { userRepository.getCurrentUser() } returns me
        val inTail = CompletableDeferred<Unit>()
        val tailDone = CompletableDeferred<Unit>()
        // The repository's stretch after the server has the request: not interrupted by the caller.
        coEvery { changeRequestRepository.createChangeRequest(any(), any()) } coAnswers {
            withContext(NonCancellable) {
                inTail.complete(Unit)
                tailDone.await()
            }
        }
        // A real announcer suspends on Room and the network, so this one does: a cancelled scope stops it
        // at its first checkpoint unless the caller has shielded it.
        val posted = mutableListOf<ActivityKind>()
        coEvery { announcer.announce(any(), any(), any(), any()) } coAnswers {
            delay(1)
            posted += firstArg<ActivityAnnouncement>().kind
        }
        val vm = viewModel()

        vm.submit(event, proposedStart, null, null)
        runCurrent()
        assertTrue(inTail.isCompleted)
        vm.viewModelScope.cancel() // the up arrow: the form is left, and its scope with it
        tailDone.complete(Unit)
        advanceUntilIdle()

        assertEquals(listOf(ActivityKind.CHANGE_REQUESTED), posted)
    }

    @Test
    fun `leaving before the request is written cancels it as ever, and posts no card`() = runTest(dispatcher) {
        coEvery { userRepository.getCurrentUser() } returns me
        var cancelled = false
        coEvery { changeRequestRepository.createChangeRequest(any(), any()) } coAnswers {
            try {
                awaitCancellation()
            } catch (e: CancellationException) {
                cancelled = true
                throw e
            }
        }
        val vm = viewModel()

        vm.submit(event, proposedStart, null, null)
        runCurrent()
        vm.viewModelScope.cancel()
        advanceUntilIdle()

        // Nothing is shielded until the server has the request: the outbox sends it later.
        assertTrue(cancelled)
        coVerify(exactly = 0) { announcer.announce(any(), any(), any(), any()) }
    }
}
