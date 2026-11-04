package com.coparently.app.data.requests

/**
 * The request clocks' pass that every full sync runs (phase 3a, spec §4.3; MON-24 phase 2, Task
 * 8g): how a phone that was not running delivers a clock and arms its reminders, and how the
 * proposer's deadline and end come back after a job was lost.
 *
 * [RequestClockKeeper] answers it on both platforms (in `:shared-data` beside the sync since Task
 * 8h); the jobs it schedules and the notices its steps post are the platform's
 * (`RequestClockScheduler`, [RequestClockNotices]).
 */
interface RequestClockSweep {

    /**
     * Keeps every open clock of [myUid] whose other parent is in [coParents]: delivers what this
     * parent must answer, and schedules or runs what they asked.
     */
    suspend fun sweep(myUid: String, coParents: Set<String>)
}
