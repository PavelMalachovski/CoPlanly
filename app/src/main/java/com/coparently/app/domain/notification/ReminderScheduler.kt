package com.coparently.app.domain.notification

import com.coparently.app.domain.model.Event

/**
 * Schedules local reminder notifications for events.
 * Implemented in the data layer (WorkManager); the domain layer only
 * depends on this abstraction.
 */
interface ReminderScheduler {

    /**
     * Schedules (or reschedules) the reminder for the event's next occurrence whose reminder
     * time is still ahead ([ReminderPlanner.nextReminder]), and cancels it when there is none —
     * no reminder set, or every occurrence's reminder time has passed. Idempotent per event id.
     */
    fun schedule(event: Event)

    /**
     * Cancels any pending reminder for the event id.
     */
    fun cancel(eventId: String)

    /**
     * Cancels every pending reminder on this device — for when the account's local data is
     * wiped or its session ends, so a previous account's event titles never pop up.
     */
    fun cancelAll()
}
