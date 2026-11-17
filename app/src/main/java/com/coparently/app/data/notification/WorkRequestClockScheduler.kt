package com.coparently.app.data.notification

import android.content.Context
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.coparently.app.data.requests.RequestClockWorker
import com.coparently.app.domain.notification.RequestClockScheduler
import java.util.concurrent.TimeUnit

/**
 * WorkManager-based implementation of [RequestClockScheduler].
 *
 * One one-time job per reminder, named by [RequestClockWork.uniqueName] and kept rather than
 * replaced, so asking twice for the same reminder leaves one. The delay is the time left to the
 * reminder's moment; a moment that has passed runs the job at once. A reminder reads this phone's
 * copy and runs on time, online or not. The work is run by [RequestClockWorker]. The deadline and
 * the end are the server's since MON-25, so no job is enqueued for them; one an older build left is
 * finished without acting by the worker, and cancelled with the rest by [cancelAll].
 */
class WorkRequestClockScheduler(
    private val context: Context
) : RequestClockScheduler {

    override fun scheduleReminder(clockId: String, atMillis: Long) {
        val step = RequestClockWork.STEP_REMINDER
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

    /**
     * By tag rather than by name: the names are per clock and per time, and the clocks that would
     * list them belong to the account whose session is ending.
     */
    override fun cancelAll() {
        WorkManager.getInstance(context).cancelAllWorkByTag(RequestClockWork.TAG)
    }
}
