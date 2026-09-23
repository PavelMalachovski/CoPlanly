package com.coparently.app.presentation.changerequests

import com.coparently.app.R
import com.coparently.app.data.repository.CustodyModelRepository
import com.coparently.app.domain.model.ChangeRequest
import com.coparently.app.domain.model.ChangeRequestStatus
import com.coparently.app.domain.model.Event
import com.coparently.app.domain.model.User
import com.coparently.app.domain.repository.ChangeRequestRepository
import com.coparently.app.domain.repository.EventRepository
import com.coparently.app.domain.repository.UserRepository
import com.coparently.app.domain.usecase.EventUseCases
import com.coparently.app.domain.usecase.UpdateEventUseCase
import com.coparently.app.presentation.common.UiText
import com.coparently.app.presentation.common.testParentsSource
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
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
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The inbox's answers: accepting a change request moves the event and only then marks the request
 * accepted, and every refusal reaches the parent as a localised sentence (CQ-14).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ChangeRequestViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private val requests = MutableSharedFlow<List<ChangeRequest>>()
    private val changeRequestRepository = mockk<ChangeRequestRepository>(relaxed = true) {
        every { getAllChangeRequests() } returns requests
    }
    private val eventRepository = mockk<EventRepository>(relaxed = true) {
        every { getAllEvents() } returns flowOf(emptyList())
    }
    private val updateEvent = mockk<UpdateEventUseCase>()
    private val custodyModelRepository = mockk<CustodyModelRepository>(relaxed = true) {
        every { observeDayOverrides() } returns flowOf(emptyMap())
        every { observeShared() } returns flowOf(null)
    }
    private val userRepository = mockk<UserRepository>(relaxed = true) {
        coEvery { getCurrentUser() } returns User(
            id = "u1",
            email = "olya@example.com",
            name = "Olya",
            role = "mom",
            colorCode = "",
            partnerId = "u2"
        )
    }

    private val event = Event(
        id = "e1",
        title = "Dentist",
        startDateTime = LocalDateTime.of(2026, 9, 10, 9, 0),
        endDateTime = LocalDateTime.of(2026, 9, 10, 10, 0),
        eventType = "medical",
        parentOwner = "mom",
        createdAt = LocalDateTime.of(2026, 9, 1, 9, 0),
        updatedAt = LocalDateTime.of(2026, 9, 1, 9, 0),
        lastModifiedBy = "u2"
    )
    private val request = ChangeRequest(
        id = "r1",
        eventId = "e1",
        eventTitle = "Dentist",
        requestedBy = "u2",
        requestedTo = "u1",
        currentStartDateTime = event.startDateTime,
        currentEndDateTime = event.endDateTime,
        proposedStartDateTime = LocalDateTime.of(2026, 9, 11, 14, 0),
        proposedEndDateTime = LocalDateTime.of(2026, 9, 11, 15, 0),
        createdAt = LocalDateTime.of(2026, 9, 2, 9, 0)
    )

    private fun viewModel() = ChangeRequestViewModel(
        changeRequestRepository = changeRequestRepository,
        eventRepository = eventRepository,
        eventUseCases = EventUseCases(
            createEvent = mockk(relaxed = true),
            updateEvent = updateEvent,
            deleteEvent = mockk(relaxed = true),
            getEvents = mockk(relaxed = true)
        ),
        userRepository = userRepository,
        custodyModelRepository = custodyModelRepository,
        parentsSource = testParentsSource()
    )

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `accepting fetches an unsynced event, moves it, then marks the request`() = runTest(dispatcher) {
        // change_requests is mirrored in realtime and events are not, so the request routinely
        // arrives first; accepting used to fail with "the event no longer exists".
        coEvery { changeRequestRepository.getChangeRequestById("r1") } returns request
        coEvery { eventRepository.getEventById("e1") } returns null
        coEvery { eventRepository.fetchRemoteEvent("e1") } returns event
        val moved = slot<Event>()
        coEvery { updateEvent.invoke(capture(moved)) } answers { Result.success(moved.captured) }
        val vm = viewModel()
        advanceUntilIdle()

        vm.accept("r1")
        advanceUntilIdle()

        assertEquals(request.proposedStartDateTime, moved.captured.startDateTime)
        assertEquals(request.proposedEndDateTime, moved.captured.endDateTime)
        assertEquals("u1", moved.captured.lastModifiedBy)
        coVerify(exactly = 1) { changeRequestRepository.updateStatus("r1", ChangeRequestStatus.ACCEPTED) }
    }

    @Test
    fun `an event missing everywhere is reported and the request stays open`() = runTest(dispatcher) {
        coEvery { changeRequestRepository.getChangeRequestById("r1") } returns request
        coEvery { eventRepository.getEventById("e1") } returns null
        coEvery { eventRepository.fetchRemoteEvent("e1") } returns null
        val vm = viewModel()
        advanceUntilIdle()

        vm.accept("r1")
        advanceUntilIdle()

        assertEquals(UiText.Res(R.string.change_request_error_event_missing), vm.errorMessage.value)
        coVerify(exactly = 0) { changeRequestRepository.updateStatus(any(), any()) }
    }

    @Test
    fun `a move that fails leaves the request pending and says so`() = runTest(dispatcher) {
        coEvery { changeRequestRepository.getChangeRequestById("r1") } returns request
        coEvery { eventRepository.getEventById("e1") } returns event
        coEvery { updateEvent.invoke(any()) } returns Result.failure(IllegalStateException("validation"))
        val vm = viewModel()
        advanceUntilIdle()

        vm.accept("r1")
        advanceUntilIdle()

        assertEquals(UiText.Res(R.string.change_request_error_apply_failed), vm.errorMessage.value)
        coVerify(exactly = 0) { changeRequestRepository.updateStatus(any(), any()) }

        vm.clearError()
        assertNull(vm.errorMessage.value)
    }

    @Test
    fun `a refused custody proposal answer is surfaced, not swallowed`() = runTest(dispatcher) {
        coEvery { custodyModelRepository.declineProposal(any()) } returns
            Result.failure(IllegalStateException("not yours to decide"))
        val vm = viewModel()
        advanceUntilIdle()

        vm.declineProposal()
        advanceUntilIdle()

        assertEquals(UiText.Res(R.string.change_request_error_proposal_decline_failed), vm.errorMessage.value)
    }

    @Test
    fun `an empty inbox is told apart from one that has not loaded`() = runTest(dispatcher) {
        val vm = viewModel()
        backgroundScope.launch { vm.changeRequests.collect {} }
        advanceUntilIdle()
        assertFalse(vm.hasLoaded.value)

        requests.emit(emptyList())
        advanceUntilIdle()

        assertTrue(vm.hasLoaded.value)
        assertEquals(emptyList<ChangeRequest>(), vm.changeRequests.value)
    }
}
