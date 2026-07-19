package com.coparently.app.domain.notification

import com.coparently.app.domain.model.Event

/**
 * Schedules local reminder notifications for events.
 * Implemented in the data layer (WorkManager); the domain layer only
 * depends on this abstraction.
 */
interface ReminderScheduler {

    /**
     * Schedules (or reschedules) the reminder for the event.
     * No-op when the event has no reminder or its trigger time is in the past.
     */
    fun schedule(event: Event)

    /**
     * Cancels any pending reminder for the event id.
     */
    fun cancel(eventId: String)
}
