package com.coparently.app.data.notification

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.coparently.app.R
import com.coparently.app.data.local.dao.EventDao
import com.coparently.app.data.local.entity.EventEntity
import com.coparently.app.domain.notification.ReminderPlanner
import com.coparently.app.domain.notification.ReminderScheduler
import com.coparently.app.presentation.MainActivity
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.time.LocalDateTime

/**
 * Worker that fires a local reminder notification before an event starts.
 * Scheduled by [EventReminderScheduler] with a delay matching the
 * event's reminder offset (e.g. 30 min or 1 h before start).
 *
 * The event is looked up in Room when the reminder is due, not trusted from the moment it was
 * scheduled: a sync that deleted or moved it reschedules the work too, but a stale reminder that
 * got through anyway would announce itself at the old time and under the old title (see
 * [shouldFire]).
 *
 * A reminder is for one occurrence. Once it has fired — or been skipped because that occurrence
 * no longer exists — the worker asks the scheduler for the event's next one, which is how a
 * recurring event keeps reminding every week. The request replaces this very work under its
 * unique name, so it is the last thing done.
 */
@HiltWorker
class ReminderWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val eventDao: EventDao,
    private val reminderScheduler: ReminderScheduler
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val eventId = inputData.getString(KEY_EVENT_ID) ?: return Result.failure()

        val stored = eventDao.getEventById(eventId)
        // Deleted or tombstoned: nothing to remind of, now or later.
        if (stored == null || stored.deletedAtMillis != null) return Result.success()

        val scheduledStartAt = inputData.getString(KEY_START_AT)
        val fire = shouldFire(stored, scheduledStartAt)
        if (fire) notify(stored)

        // A recurring event goes on to its next occurrence; one whose occurrence moved or lost its
        // reminder is re-planned from what Room holds now (which may cancel). A single event that
        // has just fired has no next one and is left alone.
        if (!fire || stored.isRecurring) reminderScheduler.schedule(stored.toReminderEvent())
        return Result.success()
    }

    /** Posts the reminder for [stored], unless notifications are not allowed. */
    private fun notify(stored: EventEntity) {
        val eventId = stored.id
        // The title as it stands now, not as it stood when the reminder was scheduled.
        val title = stored.title
        val startTime = inputData.getString(KEY_START_TIME) ?: ""

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(
                applicationContext,
                Manifest.permission.POST_NOTIFICATIONS
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            // Permission revoked after scheduling; nothing to show
            return
        }

        ensureChannel()

        val intent = Intent(applicationContext, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(EXTRA_EVENT_ID, eventId)
        }
        val pendingIntent = PendingIntent.getActivity(
            applicationContext,
            eventId.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(
                applicationContext.getString(R.string.reminder_notification_text, startTime)
            )
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        val notificationManager =
            applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(eventId.hashCode(), notification)
    }

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                applicationContext.getString(R.string.reminder_channel_name),
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = applicationContext.getString(R.string.reminder_channel_description)
            }
            val notificationManager =
                applicationContext.getSystemService(NotificationManager::class.java)
            notificationManager.createNotificationChannel(channel)
        }
    }

    companion object {
        const val KEY_EVENT_ID = "event_id"
        const val KEY_TITLE = "title"
        const val KEY_START_TIME = "start_time"

        /**
         * The start of the occurrence the reminder is for, `LocalDateTime.toString()`: the
         * event's start, or one of a recurring event's occurrences. Absent on work an older
         * build enqueued, which is then checked for existence only.
         */
        const val KEY_START_AT = "start_at"
        const val EXTRA_EVENT_ID = "reminder_event_id"
        private const val CHANNEL_ID = "coparently_event_reminders"

        /**
         * Whether a reminder scheduled for [scheduledStartAt] should still fire for [stored], the
         * event's Room row now: not for a missing row or a pending tombstone, not once the
         * reminder has been removed, and not once [scheduledStartAt] is no longer one of the
         * event's occurrences ([ReminderPlanner.isOccurrence] — the start itself for a single
         * event). An unreadable or absent [scheduledStartAt] (an older build's work) checks
         * existence only.
         */
        internal fun shouldFire(stored: EventEntity?, scheduledStartAt: String?): Boolean {
            if (stored == null || stored.deletedAtMillis != null || stored.reminderMinutes == null) return false
            val scheduled = scheduledStartAt?.let { runCatching { LocalDateTime.parse(it) }.getOrNull() }
                ?: return true
            return ReminderPlanner.isOccurrence(stored.toReminderEvent(), scheduled)
        }
    }
}
