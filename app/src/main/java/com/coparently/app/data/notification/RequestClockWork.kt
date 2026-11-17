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

    /**
     * The deadline passing, on the proposer's phone: a job only an older build enqueues, since the
     * server runs this step (MON-25). The worker finishes it without acting.
     */
    const val STEP_DEADLINE = "deadline"

    /**
     * The end of the request, on the proposer's phone: a job only an older build enqueues, since the
     * server runs this step (MON-25). The worker finishes it without acting.
     */
    const val STEP_END = "end"

    /**
     * The unique name of one job:
     * - `request_reminder_<id>_<at>`: one per planned reminder;
     * - `request_deadline_<id>` and `request_end_<id>_<at>`: the names older builds gave the
     *   deadline's and the end's jobs, which this build no longer enqueues (MON-25).
     *
     * Jobs are enqueued with `ExistingWorkPolicy.KEEP`, so a name that left out the time would make
     * WorkManager drop a second reminder of one clock and keep the first.
     */
    fun uniqueName(step: String, clockId: String, atMillis: Long): String =
        if (step == STEP_DEADLINE) "request_${step}_$clockId" else "request_${step}_${clockId}_$atMillis"
}
