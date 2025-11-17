package com.coparently.app.presentation.event

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import app.cash.turbine.test
import com.coparently.app.domain.model.Event
import com.coparently.app.domain.repository.EventRepository
import io.mockk.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.time.LocalDateTime
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull

/**
 * Unit tests for EventViewModel.
 * Tests event loading, creation, update, and deletion operations.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class EventViewModelTest {

    @get:Rule
    val instantExecutorRule = InstantTaskExecutorRule()

    private val testDispatcher = StandardTestDispatcher()

    private lateinit var eventRepository: EventRepository
    private lateinit var viewModel: EventViewModel

    private val testEvent1 = Event(
        id = "1",
        title = "Test Event 1",
        description = "Description 1",
        startTime = LocalDateTime.now(),
        endTime = LocalDateTime.now().plusHours(1),
        createdByFirebaseUid = "user1",
        createdAt = LocalDateTime.now(),
        updatedAt = LocalDateTime.now()
    )

    private val testEvent2 = Event(
        id = "2",
        title = "Test Event 2",
        description = "Description 2",
        startTime = LocalDateTime.now().plusDays(1),
        endTime = LocalDateTime.now().plusDays(1).plusHours(2),
        createdByFirebaseUid = "user1",
        createdAt = LocalDateTime.now(),
        updatedAt = LocalDateTime.now()
    )

    private val testEvents = listOf(testEvent1, testEvent2)

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        eventRepository = mockk()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        clearAllMocks()
    }

    @Test
    fun `loadEvents loads all events successfully`() = runTest {
        // Given
        coEvery { eventRepository.getAllEvents() } returns flowOf(testEvents)

        // When
        viewModel = EventViewModel(eventRepository)
        testScheduler.advanceUntilIdle()

        // Then
        viewModel.uiState.test {
            val state = awaitItem()
            assertIs<EventUiState.Success>(state)
            assertEquals(testEvents, state.events)
        }

        viewModel.events.test {
            val events = awaitItem()
            assertEquals(testEvents, events)
        }

        coVerify { eventRepository.getAllEvents() }
    }

    @Test
    fun `loadEvents handles error`() = runTest {
        // Given
        val errorMessage = "Network error"
        coEvery { eventRepository.getAllEvents() } throws Exception(errorMessage)

        // When
        viewModel = EventViewModel(eventRepository)
        testScheduler.advanceUntilIdle()

        // Then
        viewModel.uiState.test {
            val state = awaitItem()
            assertIs<EventUiState.Error>(state)
            assertEquals(errorMessage, state.message)
        }
    }

    @Test
    fun `loadEvents shows loading state initially`() = runTest {
        // Given
        coEvery { eventRepository.getAllEvents() } returns flowOf(emptyList())

        // When
        viewModel = EventViewModel(eventRepository)

        // Then - should be loading before advancing
        viewModel.uiState.test {
            val state = awaitItem()
            assertIs<EventUiState.Loading>(state)
        }
    }

    @Test
    fun `loadEventsForDate loads events for specific date`() = runTest {
        // Given
        val testDate = LocalDateTime.now()
        val dateEvents = listOf(testEvent1)
        coEvery { eventRepository.getAllEvents() } returns flowOf(testEvents)
        coEvery { eventRepository.getEventsByDate(testDate) } returns flowOf(dateEvents)

        viewModel = EventViewModel(eventRepository)
        testScheduler.advanceUntilIdle()

        // When
        viewModel.loadEventsForDate(testDate)
        testScheduler.advanceUntilIdle()

        // Then
        viewModel.uiState.test {
            val state = awaitItem()
            assertIs<EventUiState.Success>(state)
            assertEquals(dateEvents, state.events)
        }

        viewModel.events.test {
            val events = awaitItem()
            assertEquals(dateEvents, events)
        }

        coVerify { eventRepository.getEventsByDate(testDate) }
    }

    @Test
    fun `loadEventsForDate handles error`() = runTest {
        // Given
        val testDate = LocalDateTime.now()
        val errorMessage = "Database error"
        coEvery { eventRepository.getAllEvents() } returns flowOf(testEvents)
        coEvery { eventRepository.getEventsByDate(testDate) } throws Exception(errorMessage)

        viewModel = EventViewModel(eventRepository)
        testScheduler.advanceUntilIdle()

        // When
        viewModel.loadEventsForDate(testDate)
        testScheduler.advanceUntilIdle()

        // Then
        viewModel.uiState.test {
            val state = awaitItem()
            assertIs<EventUiState.Error>(state)
            assertEquals("Failed to load events for date", state.message)
        }
    }

    @Test
    fun `loadEventsForDateRange loads events for date range`() = runTest {
        // Given
        val startDate = LocalDateTime.now()
        val endDate = startDate.plusDays(7)
        coEvery { eventRepository.getAllEvents() } returns flowOf(testEvents)
        coEvery { eventRepository.getEventsByDateRange(startDate, endDate) } returns flowOf(testEvents)

        viewModel = EventViewModel(eventRepository)
        testScheduler.advanceUntilIdle()

        // When
        viewModel.loadEventsForDateRange(startDate, endDate)
        testScheduler.advanceUntilIdle()

        // Then
        viewModel.uiState.test {
            val state = awaitItem()
            assertIs<EventUiState.Success>(state)
            assertEquals(testEvents, state.events)
        }

        coVerify { eventRepository.getEventsByDateRange(startDate, endDate) }
    }

    @Test
    fun `loadEventsForDateRange handles error`() = runTest {
        // Given
        val startDate = LocalDateTime.now()
        val endDate = startDate.plusDays(7)
        val errorMessage = "Query failed"
        coEvery { eventRepository.getAllEvents() } returns flowOf(testEvents)
        coEvery {
            eventRepository.getEventsByDateRange(startDate, endDate)
        } throws Exception(errorMessage)

        viewModel = EventViewModel(eventRepository)
        testScheduler.advanceUntilIdle()

        // When
        viewModel.loadEventsForDateRange(startDate, endDate)
        testScheduler.advanceUntilIdle()

        // Then
        viewModel.uiState.test {
            val state = awaitItem()
            assertIs<EventUiState.Error>(state)
            assertEquals("Failed to load events for date range", state.message)
        }
    }

    @Test
    fun `createEvent creates event successfully`() = runTest {
        // Given
        val newEvent = testEvent1.copy(id = "")
        coEvery { eventRepository.getAllEvents() } returns flowOf(testEvents)
        coEvery { eventRepository.insertEvent(any()) } just Runs

        viewModel = EventViewModel(eventRepository)
        testScheduler.advanceUntilIdle()

        // When
        viewModel.createEvent(newEvent)
        testScheduler.advanceUntilIdle()

        // Then
        viewModel.uiState.test {
            val state = awaitItem()
            assertIs<EventUiState.OperationSuccess>(state)
            assertEquals("Event created successfully", state.message)
        }

        coVerify {
            eventRepository.insertEvent(match {
                it.id.isNotEmpty() &&
                it.title == newEvent.title &&
                it.createdAt != null &&
                it.updatedAt != null
            })
        }
    }

    @Test
    fun `createEvent preserves existing ID`() = runTest {
        // Given
        val newEvent = testEvent1
        coEvery { eventRepository.getAllEvents() } returns flowOf(testEvents)
        coEvery { eventRepository.insertEvent(any()) } just Runs

        viewModel = EventViewModel(eventRepository)
        testScheduler.advanceUntilIdle()

        // When
        viewModel.createEvent(newEvent)
        testScheduler.advanceUntilIdle()

        // Then
        coVerify {
            eventRepository.insertEvent(match { it.id == testEvent1.id })
        }
    }

    @Test
    fun `createEvent handles error`() = runTest {
        // Given
        val newEvent = testEvent1
        val errorMessage = "Insert failed"
        coEvery { eventRepository.getAllEvents() } returns flowOf(testEvents)
        coEvery { eventRepository.insertEvent(any()) } throws Exception(errorMessage)

        viewModel = EventViewModel(eventRepository)
        testScheduler.advanceUntilIdle()

        // When
        viewModel.createEvent(newEvent)
        testScheduler.advanceUntilIdle()

        // Then
        viewModel.uiState.test {
            val state = awaitItem()
            assertIs<EventUiState.Error>(state)
            assertEquals(errorMessage, state.message)
        }
    }

    @Test
    fun `updateEvent updates event successfully`() = runTest {
        // Given
        val updatedEvent = testEvent1.copy(title = "Updated Title")
        coEvery { eventRepository.getAllEvents() } returns flowOf(testEvents)
        coEvery { eventRepository.updateEvent(any()) } just Runs

        viewModel = EventViewModel(eventRepository)
        testScheduler.advanceUntilIdle()

        // When
        viewModel.updateEvent(updatedEvent)
        testScheduler.advanceUntilIdle()

        // Then
        viewModel.uiState.test {
            val state = awaitItem()
            assertIs<EventUiState.OperationSuccess>(state)
            assertEquals("Event updated successfully", state.message)
        }

        coVerify {
            eventRepository.updateEvent(match {
                it.title == "Updated Title" &&
                it.updatedAt != null
            })
        }
    }

    @Test
    fun `updateEvent handles error`() = runTest {
        // Given
        val updatedEvent = testEvent1
        val errorMessage = "Update failed"
        coEvery { eventRepository.getAllEvents() } returns flowOf(testEvents)
        coEvery { eventRepository.updateEvent(any()) } throws Exception(errorMessage)

        viewModel = EventViewModel(eventRepository)
        testScheduler.advanceUntilIdle()

        // When
        viewModel.updateEvent(updatedEvent)
        testScheduler.advanceUntilIdle()

        // Then
        viewModel.uiState.test {
            val state = awaitItem()
            assertIs<EventUiState.Error>(state)
            assertEquals(errorMessage, state.message)
        }
    }

    @Test
    fun `deleteEvent deletes event successfully`() = runTest {
        // Given
        coEvery { eventRepository.getAllEvents() } returns flowOf(testEvents)
        coEvery { eventRepository.deleteEvent(any()) } just Runs

        viewModel = EventViewModel(eventRepository)
        testScheduler.advanceUntilIdle()

        // When
        viewModel.deleteEvent(testEvent1)
        testScheduler.advanceUntilIdle()

        // Then
        viewModel.uiState.test {
            val state = awaitItem()
            assertIs<EventUiState.OperationSuccess>(state)
            assertEquals("Event deleted successfully", state.message)
        }

        coVerify { eventRepository.deleteEvent(testEvent1) }
    }

    @Test
    fun `deleteEvent handles error`() = runTest {
        // Given
        val errorMessage = "Delete failed"
        coEvery { eventRepository.getAllEvents() } returns flowOf(testEvents)
        coEvery { eventRepository.deleteEvent(any()) } throws Exception(errorMessage)

        viewModel = EventViewModel(eventRepository)
        testScheduler.advanceUntilIdle()

        // When
        viewModel.deleteEvent(testEvent1)
        testScheduler.advanceUntilIdle()

        // Then
        viewModel.uiState.test {
            val state = awaitItem()
            assertIs<EventUiState.Error>(state)
            assertEquals(errorMessage, state.message)
        }
    }

    @Test
    fun `deleteEventById deletes event by ID successfully`() = runTest {
        // Given
        val eventId = "test_id"
        coEvery { eventRepository.getAllEvents() } returns flowOf(testEvents)
        coEvery { eventRepository.deleteEventById(eventId) } just Runs

        viewModel = EventViewModel(eventRepository)
        testScheduler.advanceUntilIdle()

        // When
        viewModel.deleteEventById(eventId)
        testScheduler.advanceUntilIdle()

        // Then
        viewModel.uiState.test {
            val state = awaitItem()
            assertIs<EventUiState.OperationSuccess>(state)
            assertEquals("Event deleted successfully", state.message)
        }

        coVerify { eventRepository.deleteEventById(eventId) }
    }

    @Test
    fun `deleteEventById handles error`() = runTest {
        // Given
        val eventId = "test_id"
        val errorMessage = "Delete by ID failed"
        coEvery { eventRepository.getAllEvents() } returns flowOf(testEvents)
        coEvery { eventRepository.deleteEventById(eventId) } throws Exception(errorMessage)

        viewModel = EventViewModel(eventRepository)
        testScheduler.advanceUntilIdle()

        // When
        viewModel.deleteEventById(eventId)
        testScheduler.advanceUntilIdle()

        // Then
        viewModel.uiState.test {
            val state = awaitItem()
            assertIs<EventUiState.Error>(state)
            assertEquals(errorMessage, state.message)
        }
    }

    @Test
    fun `getEventById returns event`() = runTest {
        // Given
        val eventId = "test_id"
        coEvery { eventRepository.getAllEvents() } returns flowOf(testEvents)
        coEvery { eventRepository.getEventById(eventId) } returns testEvent1

        viewModel = EventViewModel(eventRepository)
        testScheduler.advanceUntilIdle()

        // When
        val result = viewModel.getEventById(eventId)

        // Then
        assertNotNull(result)
        assertEquals(testEvent1, result)
        coVerify { eventRepository.getEventById(eventId) }
    }

    @Test
    fun `getEventById returns null when not found`() = runTest {
        // Given
        val eventId = "nonexistent_id"
        coEvery { eventRepository.getAllEvents() } returns flowOf(testEvents)
        coEvery { eventRepository.getEventById(eventId) } returns null

        viewModel = EventViewModel(eventRepository)
        testScheduler.advanceUntilIdle()

        // When
        val result = viewModel.getEventById(eventId)

        // Then
        assertEquals(null, result)
        coVerify { eventRepository.getEventById(eventId) }
    }
}

