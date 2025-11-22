package com.coparently.app.domain.usecase

import com.coparently.app.data.analytics.AnalyticsManager
import com.coparently.app.data.crashlytics.CrashlyticsManager
import com.coparently.app.domain.model.Event
import com.coparently.app.domain.model.ValidationResult
import com.coparently.app.domain.repository.EventRepository
import java.time.LocalDateTime
import java.util.UUID
import javax.inject.Inject

/**
 * Use case for creating a new event.
 * Encapsulates validation, creation, and analytics logic.
 */
class CreateEventUseCase @Inject constructor(
    private val eventRepository: EventRepository,
    private val eventValidator: EventValidator,
    private val analyticsManager: AnalyticsManager,
    private val crashlyticsManager: CrashlyticsManager
) {
    /**
     * Creates a new event.
     * @return Result with created event or exception
     */
    suspend operator fun invoke(event: Event): Result<Event> {
        return try {
            // Validate event
            val validationResult = eventValidator.validate(event)
            if (validationResult is ValidationResult.Error) {
                return Result.failure(ValidationException(validationResult.message, validationResult.field))
            }

            // Ensure event has an ID
            val eventWithId = if (event.id.isEmpty()) {
                event.copy(id = UUID.randomUUID().toString())
            } else {
                event
            }

            // Set timestamps
            val now = LocalDateTime.now()
            val finalEvent = eventWithId.copy(
                createdAt = now,
                updatedAt = now
            )

            // Create event
            eventRepository.insertEvent(finalEvent)

            // Log analytics
            analyticsManager.logEventCreated(finalEvent.eventType)

            Result.success(finalEvent)
        } catch (e: Exception) {
            crashlyticsManager.recordException(e)
            Result.failure(e)
        }
    }
}

/**
 * Exception thrown when validation fails.
 */
class ValidationException(
    message: String,
    val field: String? = null
) : Exception(message)
