package com.coparently.app.data.requests

/**
 * The request clocks' pass that every full sync runs (phase 3a, spec §4.3; MON-24 phase 2, Task
 * 8g): how a phone that was not running delivers a clock and arms its reminders, and how they
 * come back after a job was lost. The proposer's deadline and end are the server's since MON-25.
 *
 * [RequestClockKeeper] answers it on both platforms (in `:shared-data` beside the sync since Task
 * 8h); the jobs it schedules and the notices its steps post are the platform's
 * (`RequestClockScheduler`, [RequestClockNotices]).
 */
interface RequestClockSweep {

    /**
     * Keeps every open clock that [myUid] must answer and whose other parent is in [coParents]:
     * delivers it, or arms its reminders still ahead again.
     */
    suspend fun sweep(myUid: String, coParents: Set<String>)
}
