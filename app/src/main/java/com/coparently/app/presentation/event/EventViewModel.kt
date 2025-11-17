package com.coparently.app.presentation.event

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.coparently.app.data.analytics.AnalyticsManager
import com.coparently.app.data.crashlytics.CrashlyticsManager
import com.coparently.app.domain.model.Event
import com.coparently.app.domain.repository.EventRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.LocalDateTime
import java.util.UUID
import javax.inject.Inject

/**
 * ViewModel for managing events.
 * Handles UI state and business logic for event operations.
 */
@HiltViewModel
class EventViewModel @Inject constructor(
    private val eventRepository: EventRepository,
    private val analyticsManager: AnalyticsManager,
    private val crashlyticsManager: CrashlyticsManager
) : ViewModel() {

    private val _uiState = MutableStateFlow<EventUiState>(EventUiState.Loading)
    val uiState: StateFlow<EventUiState> = _uiState.asStateFlow()

    private val _events = MutableStateFlow<List<Event>>(emptyList())
    val events: StateFlow<List<Event>> = _events.asStateFlow()

    init {
        loadEvents()
    }

    /**
     * Loads all events.
     */
    fun loadEvents() {
        viewModelScope.launch {
            _uiState.value = EventUiState.Loading
            try {
                eventRepository.getAllEvents().collect { eventList ->
                    _events.value = eventList
                    _uiState.value = EventUiState.Success(eventList)
                }
            } catch (e: Exception) {
                _uiState.value = EventUiState.Error(e.message ?: "Failed to load events")
            }
        }
    }

    /**
     * Loads events for a specific date.
     */
    fun loadEventsForDate(date: LocalDateTime) {
        viewModelScope.launch {
            _uiState.value = EventUiState.Loading
            try {
                eventRepository.getEventsByDate(date).collect { eventList ->
                    _events.value = eventList
                    _uiState.value = EventUiState.Success(eventList)
                }
            } catch (e: Exception) {
                _uiState.value = EventUiState.Error(e.message ?: "Failed to load events for date")
            }
        }
    }

    /**
     * Loads events for a date range.
     */
    fun loadEventsForDateRange(start: LocalDateTime, end: LocalDateTime) {
        viewModelScope.launch {
            _uiState.value = EventUiState.Loading
            try {
                eventRepository.getEventsByDateRange(start, end).collect { eventList ->
                    _events.value = eventList
                    _uiState.value = EventUiState.Success(eventList)
                }
            } catch (e: Exception) {
                _uiState.value = EventUiState.Error(e.message ?: "Failed to load events for date range")
            }
        }
    }

    /**
     * Creates a new event.
     */
    fun createEvent(event: Event) {
        viewModelScope.launch {
            try {
                val eventWithId = if (event.id.isEmpty()) {
                    event.copy(id = UUID.randomUUID().toString())
                } else {
                    event
                }
                val now = LocalDateTime.now()
                val finalEvent = eventWithId.copy(
                    createdAt = now,
                    updatedAt = now
                )
                eventRepository.insertEvent(finalEvent)
                analyticsManager.logEventCreated(event.eventType)
                _uiState.value = EventUiState.OperationSuccess("Event created successfully")
                // После короткой задержки вернемся к Success состоянию
                kotlinx.coroutines.delay(2000)
                _uiState.value = EventUiState.Success(_events.value)
            } catch (e: Exception) {
                _uiState.value = EventUiState.Error(e.message ?: "Failed to create event")
            }
        }
    }

    /**
     * Updates an existing event.
     */
    fun updateEvent(event: Event) {
        viewModelScope.launch {
            try {
                val updatedEvent = event.copy(updatedAt = LocalDateTime.now())
                eventRepository.updateEvent(updatedEvent)
                analyticsManager.logEventUpdated(event.eventType)
                _uiState.value = EventUiState.OperationSuccess("Event updated successfully")
                kotlinx.coroutines.delay(2000)
                _uiState.value = EventUiState.Success(_events.value)
            } catch (e: Exception) {
                _uiState.value = EventUiState.Error(e.message ?: "Failed to update event")
            }
        }
    }

    /**
     * Deletes an event.
     */
    fun deleteEvent(event: Event) {
        viewModelScope.launch {
            try {
                eventRepository.deleteEvent(event)
                analyticsManager.logEventDeleted(event.eventType)
                _uiState.value = EventUiState.OperationSuccess("Event deleted successfully")
                kotlinx.coroutines.delay(2000)
                _uiState.value = EventUiState.Success(_events.value)
            } catch (e: Exception) {
                _uiState.value = EventUiState.Error(e.message ?: "Failed to delete event")
            }
        }
    }

    /**
     * Deletes an event by ID.
     */
    fun deleteEventById(id: String) {
        viewModelScope.launch {
            try {
                eventRepository.deleteEventById(id)
                _uiState.value = EventUiState.OperationSuccess("Event deleted successfully")
                kotlinx.coroutines.delay(2000)
                _uiState.value = EventUiState.Success(_events.value)
            } catch (e: Exception) {
                _uiState.value = EventUiState.Error(e.message ?: "Failed to delete event")
            }
        }
    }

    /**
     * Gets an event by ID.
     */
    suspend fun getEventById(id: String): Event? {
        return eventRepository.getEventById(id)
    }
}

/**
 * UI state for events.
 */
sealed class EventUiState {
    /**
     * Loading state - показываем Skeleton Loading
     */
    data object Loading : EventUiState()

    /**
     * Success state - данные загружены успешно
     */
    data class Success(val events: List<Event>) : EventUiState()

    /**
     * Operation Success state - операция (создание/обновление/удаление) выполнена успешно
     * Показываем Lottie Success анимацию
     */
    data class OperationSuccess(val message: String = "Operation completed successfully") : EventUiState()

    /**
     * Error state - произошла ошибка, показываем Lottie Error анимацию
     */
    data class Error(val message: String) : EventUiState()
}

