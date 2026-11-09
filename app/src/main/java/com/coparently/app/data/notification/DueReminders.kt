@file:OptIn(InternalSharedApi::class)

package com.coparently.app.data.notification

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat
import com.coparently.app.R
import com.coparently.app.data.local.dao.EventDao
import com.coparently.app.data.local.entity.EventEntity
import com.coparently.app.data.remote.firebase.PushDestination
import com.coparently.app.data.remote.firebase.PushPayload
import com.coparently.app.domain.InternalSharedApi
import com.coparently.app.domain.notification.ReminderPlanner
import com.coparently.app.domain.notification.ReminderScheduler
import com.coparently.app.domain.time.IsoText
import com.coparently.app.utils.inAppLanguage

/**
 * What a due reminder does, whichever clock woke it: [ReminderAlarmReceiver]'s alarm, or
 * [ReminderWorker]'s work that an older build queued before reminders moved onto alarms.
 *
 * The event is looked up in Room when the reminder is due, not trusted from the moment it was
 * armed: a sync that deleted or moved it re-arms the alarm too, but a stale reminder that got
 * through anyway would announce itself at the old time and under the old title ([shouldFire]).
 *
 * A reminder is for one occurrence. Once it has fired — or been skipped because that occurrence
 * no longer exists — a recurring event is armed for its next one, which is how a weekly event keeps
 * reminding every week; a single event that has fired is forgotten.
 *
 * The notification and its channel are worded in the language the app speaks when it fires
 * ([inAppLanguage]).
 */
class DueReminders(
    private val context: Context,
    private val eventDao: EventDao,
    private val reminderScheduler: ReminderScheduler
) {

    /**
     * Delivers the reminder armed for [eventId]'s occurrence at [scheduledStartAt]
     * (`IsoText.dateTimeShortest`, the text `java.time`'s `LocalDateTime.toString()` wrote; null on
     * an older build's work, which then checks existence only), worded with [startTime], the start
     * on the reader's clock.
     */
    suspend fun deliver(eventId: String, scheduledStartAt: String?, startTime: String?) {
        val stored = eventDao.getEventById(eventId)
        // Deleted or tombstoned: nothing to remind of, now or later.
        if (stored == null || stored.deletedAtMillis != null) {
            reminderScheduler.cancel(eventId)
            return
        }
        val fire = shouldFire(stored, scheduledStartAt)
        if (fire) post(stored, startTime.orEmpty())
        // A recurring event goes on to its next occurrence; one whose occurrence moved or lost its
        // reminder is re-planned from what Room holds now (which may cancel). A single event that
        // has just fired has no next one.
        if (!fire || stored.isRecurring) {
            reminderScheduler.schedule(stored.toReminderEvent())
        } else {
            reminderScheduler.cancel(eventId)
        }
    }

    /** Posts the reminder for [stored], unless notifications are not allowed. */
    private fun post(stored: EventEntity, startTime: String) {
        val words = context.inAppLanguage()
        ensureChannel(words)
        // Permission revoked, notifications or the channel turned off since it was armed.
        if (!notificationsAllowed(context, CHANNEL_ID)) return
        val notificationManager = context.getSystemService(NotificationManager::class.java)
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            // The title as it stands now, not as it stood when the reminder was armed.
            .setContentTitle(stored.title)
            .setContentText(words.getString(R.string.reminder_notification_text, startTime))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setAutoCancel(true)
            .apply { tapIntent(stored)?.let { setContentIntent(it) } }
            .build()
        notificationManager.notify(stored.id.hashCode(), notification)
    }

    /**
     * Opens the calendar on the event's own family (as a push about an event does), where the
     * reminder's tap used to open the app wherever it was left: the event id it carried was never
     * read. A private event belongs to no family (design item 46), so it opens on the one on screen.
     */
    private fun tapIntent(stored: EventEntity): PendingIntent? {
        val launch = context.packageManager.getLaunchIntentForPackage(context.packageName) ?: return null
        launch.putExtra(PushDestination.EXTRA, PushDestination.CALENDAR.key)
        stored.familyId?.takeIf { it.isNotBlank() && !stored.isPrivate }?.let {
            launch.putExtra(PushPayload.FAMILY_ID, it)
        }
        return PendingIntent.getActivity(
            context,
            stored.id.hashCode(),
            launch,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    /** Creates the reminder channel, or renames it into [words]' language when it exists. */
    private fun ensureChannel(words: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                words.getString(R.string.reminder_channel_name),
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = words.getString(R.string.reminder_channel_description)
            }
            context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    companion object {
        /** The reminders' channel. Stable: the system keeps the channel's settings under it. */
        private const val CHANNEL_ID = "coparently_event_reminders"

        /**
         * Whether a reminder armed for [scheduledStartAt] should still fire for [stored], the
         * event's Room row now: not for a missing row or a pending tombstone, not once the
         * reminder has been removed, and not once [scheduledStartAt] is no longer one of the
         * event's occurrences ([ReminderPlanner.isOccurrence] — the start itself for a single
         * event). An unreadable or absent [scheduledStartAt] (an older build's work) checks
         * existence only.
         */
        internal fun shouldFire(stored: EventEntity?, scheduledStartAt: String?): Boolean {
            if (stored == null || stored.deletedAtMillis != null || stored.reminderMinutes == null) return false
            // `IsoText.parseDateTime` reads what `LocalDateTime.parse` read, so work an older build
            // enqueued (its `toString()`) is read as before.
            val scheduled = scheduledStartAt?.let { runCatching { IsoText.parseDateTime(it) }.getOrNull() }
                ?: return true
            return ReminderPlanner.isOccurrence(stored.toReminderEvent(), scheduled)
        }
    }
}
