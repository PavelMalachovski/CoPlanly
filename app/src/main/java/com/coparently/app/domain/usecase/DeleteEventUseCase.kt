package com.coparently.app.domain.usecase

import com.coparently.app.data.analytics.AnalyticsManager
import com.coparently.app.data.crashlytics.CrashlyticsManager
import com.coparently.app.domain.model.Event
import com.coparently.app.domain.repository.EventRepository
import javax.inject.Inject

/**
 * Use case for deleting an event.
 */
class DeleteEventUseCase @Inject constructor(
    private val eventRepository: EventRepository,
    private val analyticsManager: AnalyticsManager,
    private val crashlyticsManager: CrashlyticsManager,
    private val reminderScheduler: com.coparently.app.domain.notification.ReminderScheduler
) {
    /**
     * Deletes an event.
     * @return Result indicating success or failure
     */
    suspend operator fun invoke(event: Event): Result<Unit> {
        return try {
            eventRepository.deleteEvent(event)
            reminderScheduler.cancel(event.id)

            // Log analytics
            analyticsManager.logEventDeleted(event.eventType)

            Result.success(Unit)
        } catch (e: Exception) {
            crashlyticsManager.recordException(e)
            Result.failure(e)
        }
    }

    /**
     * Deletes an event by ID.
     */
    suspend fun deleteById(eventId: String): Result<Unit> {
        return try {
            eventRepository.deleteEventById(eventId)
            reminderScheduler.cancel(eventId)
            Result.success(Unit)
        } catch (e: Exception) {
            crashlyticsManager.recordException(e)
            Result.failure(e)
        }
    }
}
