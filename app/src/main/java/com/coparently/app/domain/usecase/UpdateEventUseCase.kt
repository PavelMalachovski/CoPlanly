package com.coparently.app.domain.usecase

import com.coparently.app.data.analytics.AnalyticsManager
import com.coparently.app.data.crashlytics.CrashlyticsManager
import com.coparently.app.domain.model.Event
import com.coparently.app.domain.model.ValidationResult
import com.coparently.app.domain.repository.EventRepository
import java.time.LocalDateTime
import javax.inject.Inject

/**
 * Use case for updating an existing event.
 */
class UpdateEventUseCase @Inject constructor(
    private val eventRepository: EventRepository,
    private val eventValidator: EventValidator,
    private val analyticsManager: AnalyticsManager,
    private val crashlyticsManager: CrashlyticsManager,
    private val reminderScheduler: com.coparently.app.domain.notification.ReminderScheduler
) {
    /**
     * Updates an existing event.
     * @return Result with updated event or exception
     */
    suspend operator fun invoke(event: Event): Result<Event> {
        return try {
            // Validate event
            val validationResult = eventValidator.validate(event)
            if (validationResult is ValidationResult.Error) {
                return Result.failure(ValidationException(validationResult.message, validationResult.field))
            }

            // Update timestamp
            val updatedEvent = event.copy(updatedAt = LocalDateTime.now())

            // Update event
            eventRepository.updateEvent(updatedEvent)

            // Reschedule (or cancel) the reminder to match the new state
            reminderScheduler.schedule(updatedEvent)

            // Log analytics
            analyticsManager.logEventUpdated(updatedEvent.eventType)

            Result.success(updatedEvent)
        } catch (e: Exception) {
            crashlyticsManager.recordException(e)
            Result.failure(e)
        }
    }
}
