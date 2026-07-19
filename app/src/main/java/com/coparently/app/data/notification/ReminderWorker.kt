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
import com.coparently.app.presentation.MainActivity
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

/**
 * Worker that fires a local reminder notification before an event starts.
 * Scheduled by [EventReminderScheduler] with a delay matching the
 * event's reminder offset (e.g. 30 min or 1 h before start).
 */
@HiltWorker
class ReminderWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val eventId = inputData.getString(KEY_EVENT_ID) ?: return Result.failure()
        val title = inputData.getString(KEY_TITLE) ?: return Result.failure()
        val startTime = inputData.getString(KEY_START_TIME) ?: ""

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
            .setSmallIcon(R.mipmap.ic_launcher)
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
        const val EXTRA_EVENT_ID = "reminder_event_id"
        private const val CHANNEL_ID = "coparently_event_reminders"
    }
}
