package com.coparently.app.data.requests

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.coparently.app.data.notification.RequestClockWork

/**
 * Finishes a request clock's job that an older build enqueued (phase 3a, spec §5.3), without acting.
 *
 * Every step of a clock runs on the server now: the deadline notice and the end since MON-25, and the
 * reminders to the answering parent since iPhone phase 4 part C (`functions/request-clock-steps.js`,
 * the `request_reminder` push). A phone that still ran a reminder, a deadline or an end job queued
 * before the update would tell its parent twice, so each finishes as done. A job this build cannot
 * read — no clock id, or a step it does not know, as a newer build might have enqueued before a
 * downgrade — fails at once.
 *
 * Kept, with its name, because WorkManager instantiates a queued job's worker by class name: a
 * removed class would leave an older build's jobs failing on every run until they were cancelled.
 */
class RequestClockWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    /** Finishes the job: done for a step this build recognises, failed for anything else. */
    override suspend fun doWork(): Result {
        val readable = inputData.getString(RequestClockWork.KEY_CLOCK_ID) != null &&
            finishesWithoutActing(inputData.getString(RequestClockWork.KEY_STEP))
        return if (readable) Result.success() else Result.failure()
    }
}

/**
 * Whether a job of [step] is one an older build enqueued — a reminder, a deadline or an end — which
 * this build finishes without acting, because the server runs every step (MON-25; iPhone phase 4
 * part C); false for a step this build does not know.
 */
internal fun finishesWithoutActing(step: String?): Boolean = when (step) {
    RequestClockWork.STEP_REMINDER, RequestClockWork.STEP_DEADLINE, RequestClockWork.STEP_END -> true
    else -> false
}
