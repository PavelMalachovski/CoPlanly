package com.coparently.app.data.requests

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

/**
 * Runs one step of a request's clock — a reminder, the deadline or the end — when the job that
 * `WorkRequestClockScheduler` enqueued for it comes due. The job carries the id of the clock, the
 * step and the moment it was scheduled for, under the keys `RequestClockWork` names.
 *
 * It does nothing yet: the steps are not written, so a job that fires has nothing to run and
 * finishes at once.
 */
@HiltWorker
class RequestClockWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters
) : CoroutineWorker(context, params) {

    /** Finishes without doing anything. */
    override suspend fun doWork(): Result = Result.success()
}
