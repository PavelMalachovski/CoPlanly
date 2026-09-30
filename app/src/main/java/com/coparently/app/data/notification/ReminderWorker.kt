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
 * scheduled: only this phone's own saves reschedule or cancel the work, so an event the co-parent
 * deleted or moved — arriving through sync — would otherwise still announce itself, at the old
 * time and under the old title (see [shouldFire]).
 */
@HiltWorker
class ReminderWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val eventDao: EventDao
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val eventId = inputData.getString(KEY_EVENT_ID) ?: return Result.failure()
        val startTime = inputData.getString(KEY_START_TIME) ?: ""

        val stored = eventDao.getEventById(eventId)
        if (stored == null || !shouldFire(stored, inputData.getString(KEY_START_AT))) {
            // Deleted, tombstoned or moved since it was scheduled: a stale reminder is worse than
            // none. (A move this phone saved itself has already replaced this work; one that
            // arrived through sync is not rescheduled here — that is a separate change.)
            return Result.success()
        }
        // The title as it stands now, not as it stood when the reminder was scheduled.
        val title = stored.title

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(
                applicationContext,
                Manifest.permission.POST_NOTIFICATIONS
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            // Permission revoked after scheduling; nothing to show
            return Result.success()
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

        return Result.success()
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
         * The event's start as it was scheduled, `LocalDateTime.toString()`. Absent on work an
         * older build enqueued, which is then checked for existence only.
         */
        const val KEY_START_AT = "start_at"
        const val EXTRA_EVENT_ID = "reminder_event_id"
        private const val CHANNEL_ID = "coparently_event_reminders"

        /**
         * Whether a reminder scheduled for [scheduledStartAt] should still fire for [stored], the
         * event's Room row now: not for a missing row or a pending tombstone, and not once the
         * start has moved. An unreadable or absent [scheduledStartAt] (an older build's work)
         * checks existence only.
         */
        internal fun shouldFire(stored: EventEntity?, scheduledStartAt: String?): Boolean {
            if (stored == null || stored.deletedAtMillis != null) return false
            val scheduled = scheduledStartAt?.let { runCatching { LocalDateTime.parse(it) }.getOrNull() }
                ?: return true
            return stored.startDateTime == scheduled
        }
    }
}
