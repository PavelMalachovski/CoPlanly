package com.coparently.app.presentation.changerequests

import com.coparently.app.R
import com.coparently.app.domain.activity.ActivityAnnouncement
import com.coparently.app.domain.activity.ActivityAnnouncer
import com.coparently.app.domain.activity.ActivityKind
import com.coparently.app.domain.model.ChangeRequest
import com.coparently.app.domain.model.Event
import com.coparently.app.domain.model.User
import com.coparently.app.domain.repository.ChangeRequestRepository
import com.coparently.app.domain.repository.EventRepository
import com.coparently.app.domain.repository.UserRepository
import com.coparently.app.presentation.common.UiText
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test
import java.time.LocalDateTime
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Proposing a new time for an event: who it goes to, what the co-parent's thread is told, and a
 * localised error — never the exception's text — when it cannot be recorded.
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
        coEvery { announcer.announce(capture(announcement), any(), any()) } returns Unit
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
        coVerify(exactly = 1) { announcer.announce(any(), "Olya", any()) }
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
        coVerify(exactly = 0) { announcer.announce(any(), any(), any()) }
    }
}
