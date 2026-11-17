package com.coparently.app.data.requests

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.coparently.app.data.notification.RequestClockWork

/**
 * Runs one step of a request's clock — a reminder — when the job that `WorkRequestClockScheduler`
 * enqueued for it comes due (phase 3a, spec §5.3). The job carries the id of the clock, the step and
 * the moment it was scheduled for, under the keys [RequestClockWork] names; [RequestClockSteps]
 * reads the clock and the request again and decides.
 *
 * A step that answers [StepResult.RETRY] is tried again with WorkManager's backoff, up to
 * [MAX_ATTEMPTS] runs in all, and then given up; the keeper's sweep on the next full sync arms the
 * reminders still ahead again. A job this build cannot read — no clock id, or a step it does not
 * know, as a newer build might have enqueued before a downgrade — fails at once.
 *
 * **A deadline or an end job an older build left queued finishes without acting** (MON-25): the
 * server runs both steps now, and a phone that ran them too would tell the proposer twice.
 */
class RequestClockWorker(
    context: Context,
    params: WorkerParameters,
    private val steps: RequestClockSteps
) : CoroutineWorker(context, params) {

    /** Runs the job's step, and retries it a few times when it asks to be. */
    override suspend fun doWork(): Result {
        val clockId = inputData.getString(RequestClockWork.KEY_CLOCK_ID) ?: return Result.failure()
        val outcome = runRequestClockStep(
            steps,
            inputData.getString(RequestClockWork.KEY_STEP),
            clockId,
            inputData.getLong(RequestClockWork.KEY_AT_MILLIS, 0L)
        ) ?: return Result.failure()
        return when {
            outcome == StepResult.DONE -> Result.success()
            runAttemptCount + 1 < MAX_ATTEMPTS -> Result.retry()
            else -> Result.failure()
        }
    }

    private companion object {
        /** How many runs a step gets in all before it is left to the next sweep. */
        const val MAX_ATTEMPTS = 3
    }
}

/**
 * Runs the [step] of a job for clock [clockId], planned for [atMillis]: a reminder through [steps];
 * an older build's [RequestClockWork.STEP_DEADLINE] or [RequestClockWork.STEP_END] job as
 * [StepResult.DONE] without acting, because the server runs those steps since MON-25; null for a
 * step this build does not know.
 */
internal suspend fun runRequestClockStep(
    steps: RequestClockSteps,
    step: String?,
    clockId: String,
    atMillis: Long
): StepResult? = when (step) {
    RequestClockWork.STEP_REMINDER -> steps.reminder(clockId, atMillis)
    RequestClockWork.STEP_DEADLINE, RequestClockWork.STEP_END -> StepResult.DONE
    else -> null
}
