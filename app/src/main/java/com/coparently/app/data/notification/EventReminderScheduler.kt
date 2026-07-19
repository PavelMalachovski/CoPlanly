package com.coparently.app.data.notification

import android.content.Context
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.coparently.app.domain.model.Event
import com.coparently.app.domain.notification.ReminderScheduler
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.Duration
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import javax.inject.Singleton

/**
 * WorkManager-based implementation of [ReminderScheduler].
 * Each event has at most one pending reminder, keyed by its id, so
 * rescheduling replaces the previous work request.
 */
@Singleton
class EventReminderScheduler @Inject constructor(
    @ApplicationContext private val context: Context
) : ReminderScheduler {

    override fun schedule(event: Event) {
        val reminderMinutes = event.reminderMinutes
        if (reminderMinutes == null) {
            cancel(event.id)
            return
        }

        val triggerAt = event.startDateTime.minusMinutes(reminderMinutes.toLong())
        val delay = Duration.between(LocalDateTime.now(), triggerAt)
        if (delay.isNegative || delay.isZero) {
            cancel(event.id)
            return
        }

        val request = OneTimeWorkRequestBuilder<ReminderWorker>()
            .setInitialDelay(delay)
            .setInputData(
                workDataOf(
                    ReminderWorker.KEY_EVENT_ID to event.id,
                    ReminderWorker.KEY_TITLE to event.title,
                    ReminderWorker.KEY_START_TIME to
                        event.startDateTime.format(DateTimeFormatter.ofPattern("HH:mm"))
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

    private fun uniqueWorkName(eventId: String) = "event_reminder_$eventId"
}
