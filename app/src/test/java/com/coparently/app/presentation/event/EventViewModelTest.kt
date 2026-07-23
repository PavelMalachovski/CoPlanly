package com.coparently.app.presentation.event

import com.coparently.app.data.local.preferences.EncryptedPreferences
import com.coparently.app.domain.error.ErrorHandler
import com.coparently.app.domain.model.Event
import com.coparently.app.domain.model.User
import com.coparently.app.domain.repository.UserRepository
import com.coparently.app.domain.usecase.CreateEventUseCase
import com.coparently.app.domain.usecase.DeleteEventUseCase
import com.coparently.app.domain.usecase.EventUseCases
import com.coparently.app.domain.usecase.GetEventsUseCase
import com.coparently.app.domain.usecase.UpdateEventUseCase
import com.google.gson.Gson
import io.mockk.clearAllMocks
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
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
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/**
 * Unit tests for EventViewModel.
 * Covers loading, deletion and pickup confirmation flows.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class EventViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    private lateinit var createEvent: CreateEventUseCase
    private lateinit var updateEvent: UpdateEventUseCase
    private lateinit var deleteEvent: DeleteEventUseCase
    private lateinit var getEvents: GetEventsUseCase
    private lateinit var errorHandler: ErrorHandler
    private lateinit var encryptedPreferences: EncryptedPreferences
    private lateinit var userRepository: UserRepository
    private lateinit var viewModel: EventViewModel

    private val sampleEvent = Event(
        id = "e1",
        title = "Soccer",
        startDateTime = LocalDateTime.of(2026, 7, 20, 16, 0),
        endDateTime = LocalDateTime.of(2026, 7, 20, 17, 0),
        eventType = "sports",
        parentOwner = "mom",
        createdAt = LocalDateTime.now(),
        updatedAt = LocalDateTime.now()
    )

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        createEvent = mockk(relaxed = true)
        updateEvent = mockk(relaxed = true)
        deleteEvent = mockk(relaxed = true)
        getEvents = mockk {
            every { this@mockk.invoke() } returns flowOf(listOf(sampleEvent))
        }
        errorHandler = mockk(relaxed = true)
        encryptedPreferences = mockk(relaxed = true)
        userRepository = mockk {
            coEvery { getCurrentUser() } returns User(
                id = "u1",
                email = "dad@example.com",
                name = "Dad",
                role = "dad",
                colorCode = "#2196F3"
            )
        }
        viewModel = EventViewModel(
            EventUseCases(createEvent, updateEvent, deleteEvent, getEvents),
            errorHandler,
            encryptedPreferences,
            Gson(),
            userRepository,
            eventImageStorage = mockk(relaxed = true)
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        clearAllMocks()
    }

    @Test
    fun `loadEvents populates events state`() = runTest {
        advanceUntilIdle()
        assertEquals(listOf(sampleEvent), viewModel.events.value)
    }

    @Test
    fun `deleteEventById delegates to use case`() = runTest {
        coEvery { deleteEvent.deleteById("e1") } returns Result.success(Unit)

        viewModel.deleteEventById("e1")
        advanceUntilIdle()

        coVerify { deleteEvent.deleteById("e1") }
    }

    @Test
    fun `confirmPickup stamps current user role`() = runTest {
        coEvery { getEvents.getById("e1") } returns sampleEvent
        val saved = slot<Event>()
        coEvery { updateEvent.invoke(capture(saved)) } answers { Result.success(saved.captured) }

        viewModel.confirmPickup("e1")
        advanceUntilIdle()

        assertEquals("dad", saved.captured.pickupConfirmedBy)
        assertNotNull(saved.captured.pickupConfirmedAt)
    }

    @Test
    fun `undoPickupConfirmation clears confirmation`() = runTest {
        coEvery { getEvents.getById("e1") } returns sampleEvent.copy(
            pickupConfirmedBy = "dad",
            pickupConfirmedAt = LocalDateTime.now()
        )
        val saved = slot<Event>()
        coEvery { updateEvent.invoke(capture(saved)) } answers { Result.success(saved.captured) }

        viewModel.undoPickupConfirmation("e1")
        advanceUntilIdle()

        assertNull(saved.captured.pickupConfirmedBy)
        assertNull(saved.captured.pickupConfirmedAt)
    }
}
