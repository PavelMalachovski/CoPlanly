package com.coparently.app.data.notification

import android.content.Context
import androidx.work.WorkManager
import com.coparently.app.domain.notification.RequestClockScheduler

/**
 * WorkManager-based implementation of [RequestClockScheduler].
 *
 * This build enqueues no job for a request's clock: the reminders, the deadline notice and the end
 * are the server's (MON-25; iPhone phase 4 part C). What an older build left queued under
 * [RequestClockWork.TAG] finishes without acting in `RequestClockWorker`, and [cancelAll] clears it
 * when the account's session ends.
 */
class WorkRequestClockScheduler(
    private val context: Context
) : RequestClockScheduler {

    /**
     * By tag rather than by name: the names are per clock and per time, and the clocks that would
     * list them belong to the account whose session is ending.
     */
    override fun cancelAll() {
        WorkManager.getInstance(context).cancelAllWorkByTag(RequestClockWork.TAG)
    }
}
