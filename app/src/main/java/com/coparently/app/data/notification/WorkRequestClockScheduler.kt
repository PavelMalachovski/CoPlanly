package com.coparently.app.data.notification

import android.content.Context
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.coparently.app.data.requests.RequestClockWorker
import com.coparently.app.domain.notification.RequestClockScheduler
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

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
}

/**
 * WorkManager-based implementation of [RequestClockScheduler].
 *
 * One one-time job per step, named by [RequestClockWork.uniqueName] and kept rather than replaced,
 * so asking twice for the same step at the same time leaves one. The delay is the time left to the
 * step's moment; a moment that has passed runs the job at once. No constraints: a reminder is local
 * and a step reads from Firestore's own cache offline, so none of them needs a network to start.
 * The work is run by [RequestClockWorker].
 */
@Singleton
class WorkRequestClockScheduler @Inject constructor(
    @ApplicationContext private val context: Context
) : RequestClockScheduler {

    override fun scheduleReminder(clockId: String, atMillis: Long) =
        enqueue(RequestClockWork.STEP_REMINDER, clockId, atMillis)

    override fun scheduleDeadline(clockId: String, atMillis: Long) =
        enqueue(RequestClockWork.STEP_DEADLINE, clockId, atMillis)

    override fun scheduleEnd(clockId: String, atMillis: Long) =
        enqueue(RequestClockWork.STEP_END, clockId, atMillis)

    /**
     * By tag rather than by name: the names are per clock and per time, and the clocks that would
     * list them belong to the account whose session is ending.
     */
    override fun cancelAll() {
        WorkManager.getInstance(context).cancelAllWorkByTag(RequestClockWork.TAG)
    }

    private fun enqueue(step: String, clockId: String, atMillis: Long) {
        val request = OneTimeWorkRequestBuilder<RequestClockWorker>()
            .apply {
                val delayMillis = atMillis - System.currentTimeMillis()
                if (delayMillis > 0) setInitialDelay(delayMillis, TimeUnit.MILLISECONDS)
            }
            .addTag(RequestClockWork.TAG)
            .setInputData(
                workDataOf(
                    RequestClockWork.KEY_CLOCK_ID to clockId,
                    RequestClockWork.KEY_STEP to step,
                    RequestClockWork.KEY_AT_MILLIS to atMillis
                )
            )
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(
            RequestClockWork.uniqueName(step, clockId, atMillis),
            ExistingWorkPolicy.KEEP,
            request
        )
    }
}
