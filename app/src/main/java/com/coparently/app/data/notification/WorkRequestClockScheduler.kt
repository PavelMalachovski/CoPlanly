package com.coparently.app.data.notification

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
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
 * WorkManager-based implementation of [RequestClockScheduler].
 *
 * One one-time job per step, named by [RequestClockWork.uniqueName] and kept rather than replaced,
 * so asking twice for the same step at the same time leaves one. The delay is the time left to the
 * step's moment; a moment that has passed runs the job at once. The deadline and the end wait for a
 * network ([RequestClockWork.needsNetwork]): they read the server, and a job that ran offline spent
 * its retries and was lost. A reminder reads this phone's copy and runs on time, online or not.
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
            .setConstraints(constraintsFor(step))
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

    private fun constraintsFor(step: String): Constraints =
        Constraints.Builder()
            .setRequiredNetworkType(
                if (RequestClockWork.needsNetwork(step)) NetworkType.CONNECTED else NetworkType.NOT_REQUIRED
            )
            .build()
}
