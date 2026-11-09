package com.coparently.app.data.notification

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters

/**
 * The reminder work an older build queued, before reminders moved onto alarms (9 November 2026).
 *
 * Nothing queues it any more, and [ReminderRearm] cancels what is still waiting on the first run
 * after the update. It stays because WorkManager stores the class name of every queued request: a
 * request that slipped through would otherwise fail to start rather than remind. When it does run,
 * it steps aside for an alarm already armed for its event — the two would announce the same
 * occurrence twice — and otherwise delivers the reminder as an alarm would, which arms the next one
 * on an alarm.
 */
class ReminderWorker(
    context: Context,
    params: WorkerParameters,
    private val dueReminders: DueReminders,
    private val armed: ArmedReminders
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val eventId = inputData.getString(KEY_EVENT_ID) ?: return Result.failure()
        if (armed.contains(eventId)) return Result.success()
        dueReminders.deliver(eventId, inputData.getString(KEY_START_AT), inputData.getString(KEY_START_TIME))
        return Result.success()
    }

    /** The input keys an older build wrote. */
    private companion object {
        const val KEY_EVENT_ID = "event_id"
        const val KEY_START_TIME = "start_time"
        const val KEY_START_AT = "start_at"
    }
}
