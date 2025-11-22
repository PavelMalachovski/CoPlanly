package com.coparently.app.domain.usecase

import com.coparently.app.domain.model.Event
import com.coparently.app.domain.repository.EventRepository
import kotlinx.coroutines.flow.Flow
import java.time.LocalDateTime
import javax.inject.Inject

/**
 * Use case for retrieving events.
 */
class GetEventsUseCase @Inject constructor(
    private val eventRepository: EventRepository
) {
    /**
     * Gets all events.
     */
    operator fun invoke(): Flow<List<Event>> {
        return eventRepository.getAllEvents()
    }

    /**
     * Gets events for a specific date.
     */
    fun getByDate(date: LocalDateTime): Flow<List<Event>> {
        return eventRepository.getEventsByDate(date)
    }

    /**
     * Gets events for a date range.
     */
    fun getByDateRange(start: LocalDateTime, end: LocalDateTime): Flow<List<Event>> {
        return eventRepository.getEventsByDateRange(start, end)
    }

    /**
     * Gets events for a specific parent.
     */
    fun getByParent(parentOwner: String): Flow<List<Event>> {
        return eventRepository.getEventsByParent(parentOwner)
    }

    /**
     * Gets a single event by ID.
     */
    suspend fun getById(eventId: String): Event? {
        return eventRepository.getEventById(eventId)
    }
}
