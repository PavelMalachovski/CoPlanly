package com.coparently.app.domain.usecase

import com.coparently.app.data.platform.Analytics
import com.coparently.app.data.platform.CrashReporter
import com.coparently.app.domain.model.Event
import com.coparently.app.domain.repository.EventRepository

/**
 * Use case for deleting an event.
 */
class DeleteEventUseCase(
    private val eventRepository: EventRepository,
    private val analytics: Analytics,
    private val crashReporter: CrashReporter,
    private val reminderScheduler: com.coparently.app.domain.notification.ReminderScheduler
) {
    /**
     * Deletes an event.
     * @param announce Whether the co-parent's chat is told; see `EventRepository.insertEvent`.
     * @return Result indicating success or failure
     */
    suspend operator fun invoke(event: Event, announce: Boolean = true): Result<Unit> {
        return try {
            eventRepository.deleteEvent(event, announce)
            reminderScheduler.cancel(event.id)

            // Log analytics
            analytics.log("event_deleted", mapOf("event_type" to event.eventType))

            Result.success(Unit)
        } catch (e: Exception) {
            crashReporter.record(e)
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
            crashReporter.record(e)
            Result.failure(e)
        }
    }
}
