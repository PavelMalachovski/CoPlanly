package com.coparently.app.data.requests

/**
 * The request clocks' pass that every full sync runs (phase 3a, spec §4.3; MON-24 phase 2, Task
 * 8g): how a phone that was not running delivers a clock, recording its reminder plan. The
 * reminders themselves, the proposer's deadline and the end are the server's (MON-25; iPhone phase
 * 4 part C, spec §7.3), sent as pushes from the sweep.
 *
 * [RequestClockKeeper] answers it on both platforms (in `:shared-data` beside the sync since Task
 * 8h).
 */
interface RequestClockSweep {

    /**
     * Delivers every open clock that [myUid] must answer, whose other parent is in [coParents], and
     * that this phone has not delivered yet.
     */
    suspend fun sweep(myUid: String, coParents: Set<String>)
}
