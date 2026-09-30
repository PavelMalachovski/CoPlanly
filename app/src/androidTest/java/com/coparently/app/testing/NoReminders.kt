package com.coparently.app.testing

import com.coparently.app.domain.model.Event
import com.coparently.app.domain.notification.ReminderScheduler

/**
 * A [ReminderScheduler] that schedules nothing, for the classes the e2e phones build by hand
 * (`SyncService`, `AccountSwitchGuard`, `AccountDeletionService`). Two phones share one process
 * and one WorkManager, and no e2e test is about a reminder firing; what arms which reminder is
 * covered on the JVM (`SyncServiceTest`, `ReminderPlannerTest`).
 */
object NoReminders : ReminderScheduler {
    override fun schedule(event: Event) = Unit

    override fun cancel(eventId: String) = Unit

    override fun cancelAll() = Unit
}
