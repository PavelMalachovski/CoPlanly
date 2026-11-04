package com.coparently.app.data.support

/**
 * Starts and stops the child support reminder job. An interface so the screen can be tested.
 *
 * In `:shared-data` since MON-24 phase 2 (Task 10, slice 10d), where the child support screen's
 * ViewModel, now in `:shared-ui`, reaches it; the app's `WorkManagerSupportReminderScheduler` (beside
 * `SupportReminderWorker`) answers it on Android, and the iPhone schedules nothing yet.
 */
interface SupportReminderScheduler {
    /** Makes sure the job runs; a no-op when it already does. */
    fun schedule()

    /** Stops the job. */
    fun cancel()
}
