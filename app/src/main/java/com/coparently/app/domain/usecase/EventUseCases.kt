package com.coparently.app.domain.usecase

/**
 * Data class grouping all event-related use cases.
 * Simplifies dependency injection in ViewModels.
 */
data class EventUseCases(
    val createEvent: CreateEventUseCase,
    val updateEvent: UpdateEventUseCase,
    val deleteEvent: DeleteEventUseCase,
    val getEvents: GetEventsUseCase
)
