package com.coparently.app.data.notification

/**
 * The names a request clock's jobs are kept and found by in WorkManager: the tag they all carry,
 * the keys of the data they run with, the three steps, and the unique name of a job.
 *
 * What WorkManager persists is part of the stored state of the app: a pending job survives an
 * update and runs under the build that replaced the one that scheduled it. None of these is ever
 * renamed.
 */
object RequestClockWork {

    /** Carried by every job a clock schedules, so [WorkRequestClockScheduler.cancelAll] can find them. */
    const val TAG = "request_clock"

    /** The input data: the id of the clock the job is for. */
    const val KEY_CLOCK_ID = "clock_id"

    /** The input data: which step the job runs, one of the `STEP_` names. */
    const val KEY_STEP = "step"

    /** The input data: the moment the job was scheduled for, in epoch millis. */
    const val KEY_AT_MILLIS = "at_millis"

    /** A reminder to the answering parent. */
    const val STEP_REMINDER = "reminder"

    /** The deadline passing, on the proposer's phone. */
    const val STEP_DEADLINE = "deadline"

    /** The end of the request, on the proposer's phone. */
    const val STEP_END = "end"

    /**
     * The unique name of one job:
     * - `request_reminder_<id>_<at>`: one per planned reminder;
     * - `request_deadline_<id>`: one per clock, because the deadline never moves;
     * - `request_end_<id>_<at>`: one per end, because the end moves when a last chance is given.
     *
     * Jobs are enqueued with `ExistingWorkPolicy.KEEP`, so a name that left out the time of a moved
     * end would make WorkManager drop the new job and keep the old. The old one is left to fire
     * early and find the end still ahead.
     */
    fun uniqueName(step: String, clockId: String, atMillis: Long): String =
        if (step == STEP_DEADLINE) "request_${step}_$clockId" else "request_${step}_${clockId}_$atMillis"

    /**
     * Whether a job of [step] waits for a network before it runs. The deadline and the end decide on
     * the server's copy of the request and answer "try again" without one, so unconstrained they
     * spent their few runs offline and were given up (final review I-1); WorkManager now starts them
     * only once the phone is online, however long that takes. A reminder reads this phone's copy and
     * must post on time, so it never waits.
     */
    fun needsNetwork(step: String): Boolean = step != STEP_REMINDER
}
