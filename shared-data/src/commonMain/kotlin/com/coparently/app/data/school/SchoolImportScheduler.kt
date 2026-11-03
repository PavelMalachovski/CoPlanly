package com.coparently.app.data.school

/**
 * Starts and stops the daily school import (MON-8). An interface so the connect flow can be tested,
 * and so the import can be common code while the job is the platform's: WorkManager's unique
 * periodic work on Android (`WorkManagerSchoolImportScheduler`, in the app).
 */
interface SchoolImportScheduler {
    /** Makes sure the daily import runs; a no-op when it is already scheduled. */
    fun schedule()

    /** Stops the daily import. */
    fun cancel()
}
