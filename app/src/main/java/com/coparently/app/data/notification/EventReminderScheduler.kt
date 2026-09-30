package com.coparently.app.data.notification

import android.content.Context
import android.text.format.DateFormat
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.coparently.app.domain.model.Event
import com.coparently.app.domain.notification.ReminderPlanner
import com.coparently.app.domain.notification.ReminderScheduler
import com.coparently.app.utils.shortTime
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.Duration
import java.time.LocalDateTime
import javax.inject.Inject
import javax.inject.Singleton

/**
 * WorkManager-based implementation of [ReminderScheduler].
 * Each event has at most one pending reminder, keyed by its id, so
 * rescheduling replaces the previous work request. For a recurring event it is the next
 * occurrence's ([ReminderPlanner]); [ReminderWorker] schedules the one after when it fires.
 */
@Singleton
class EventReminderScheduler @Inject constructor(
    @ApplicationContext private val context: Context
) : ReminderScheduler {

    override fun schedule(event: Event) {
        val now = LocalDateTime.now()
        val plan = ReminderPlanner.nextReminder(event, now)
        if (plan == null) {
            cancel(event.id)
            return
        }

        val request = OneTimeWorkRequestBuilder<ReminderWorker>()
            .setInitialDelay(Duration.between(now, plan.remindAt))
            .addTag(WORK_TAG)
            .setInputData(
                workDataOf(
                    ReminderWorker.KEY_EVENT_ID to event.id,
                    ReminderWorker.KEY_TITLE to event.title,
                    // The occurrence this reminder is for, which the worker checks the stored
                    // event against when it is due: the master start for a single event, one of
                    // its occurrences for a recurring one.
                    ReminderWorker.KEY_START_AT to plan.occurrenceStart.toString(),
                    ReminderWorker.KEY_START_TIME to
                        // The reader's clock (release audit R-9), read here: no activity need
                        // have run in the process that schedules a reminder.
                        plan.occurrenceStart.format(shortTime(is24Hour = DateFormat.is24HourFormat(context)))
                )
            )
            .build()

        WorkManager.getInstance(context).enqueueUniqueWork(
            uniqueWorkName(event.id),
            ExistingWorkPolicy.REPLACE,
            request
        )
    }

    override fun cancel(eventId: String) {
        WorkManager.getInstance(context).cancelUniqueWork(uniqueWorkName(eventId))
    }

    /**
     * By tag rather than by name: the names are per event, and the rows that would list them are
     * what is being wiped. Work an older build enqueued carries no tag and is not reached here;
     * [ReminderWorker] finds no row for it after a wipe and stays silent.
     */
    override fun cancelAll() {
        WorkManager.getInstance(context).cancelAllWorkByTag(WORK_TAG)
    }

    private fun uniqueWorkName(eventId: String) = "event_reminder_$eventId"

    private companion object {
        /** Carried by every reminder this build enqueues, for [cancelAll]. */
        const val WORK_TAG = "event_reminder"
    }
}
