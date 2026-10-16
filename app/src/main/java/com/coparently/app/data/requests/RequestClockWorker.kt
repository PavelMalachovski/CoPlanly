package com.coparently.app.data.requests

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.coparently.app.data.notification.RequestClockWork
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

/**
 * Runs one step of a request's clock — a reminder, the deadline or the end — when the job that
 * `WorkRequestClockScheduler` enqueued for it comes due (phase 3a, spec §5.3). The job carries the
 * id of the clock, the step and the moment it was scheduled for, under the keys [RequestClockWork]
 * names; [RequestClockSteps] reads the clock and the request again and decides.
 *
 * A step that answers [StepResult.RETRY] is tried again with WorkManager's backoff, up to
 * [MAX_ATTEMPTS] runs in all, and then given up. The deadline and the end wait for a network before
 * each run (`WorkRequestClockScheduler`), so being offline does not spend those runs. After a
 * give-up, the keeper's sweep on the next full sync arms the end and the reminders still ahead
 * again; a deadline that has passed is not armed again. A job this build cannot read — no clock id, or a step it does not know,
 * as a newer build might have enqueued before a downgrade — fails at once.
 */
@HiltWorker
class RequestClockWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val steps: RequestClockSteps
) : CoroutineWorker(context, params) {

    /** Runs the job's step, and retries it a few times when it asks to be. */
    override suspend fun doWork(): Result {
        val clockId = inputData.getString(RequestClockWork.KEY_CLOCK_ID) ?: return Result.failure()
        val outcome = when (inputData.getString(RequestClockWork.KEY_STEP)) {
            RequestClockWork.STEP_REMINDER ->
                steps.reminder(clockId, inputData.getLong(RequestClockWork.KEY_AT_MILLIS, 0L))
            RequestClockWork.STEP_DEADLINE -> steps.deadline(clockId)
            RequestClockWork.STEP_END -> steps.end(clockId)
            else -> return Result.failure()
        }
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
