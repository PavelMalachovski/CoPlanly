package com.coparently.app.data.notification

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.os.Build
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.coparently.app.data.platform.logger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.datetime.LocalDateTime

/**
 * One reminder as its alarm carries it: which event, which occurrence, and the start time already
 * worded on the reader's clock.
 *
 * @property eventId The event the reminder is for.
 * @property occurrenceStart The occurrence it announces, which [DueReminders.shouldFire] checks
 *   the stored event against when it is due.
 * @property startTime [occurrenceStart]'s time on the reader's clock (release audit R-9), worded
 *   when the reminder is armed: no activity need have run in the process that delivers it.
 */
data class DueReminder(
    val eventId: String,
    val occurrenceStart: LocalDateTime,
    val startTime: String
)

/**
 * The platform's half of a reminder: an alarm per event, and the work older builds queued.
 * An interface so [EventReminderScheduler] and [ReminderRearm] are tested on the JVM.
 */
interface ReminderAlarms {

    /** Arms (or re-arms) [due]'s alarm for [triggerAtMillis], replacing any the event had. */
    fun set(due: DueReminder, triggerAtMillis: Long)

    /** Cancels the event's alarm, and any reminder work an older build queued for it. */
    fun cancel(eventId: String)

    /** Cancels every reminder work an older build queued. */
    fun cancelLegacyWork()

    /**
     * Which of [eventIds] an older build had a reminder queued for as WorkManager work, still
     * waiting — that work is cancelled here, for the caller to arm alarms in its place.
     */
    suspend fun takeLegacyWork(eventIds: Collection<String>): Set<String>
}

/**
 * [ReminderAlarms] on [AlarmManager] (owner decision, 9 November 2026).
 *
 * Reminders used to be WorkManager work with an initial delay, which WorkManager hands to
 * JobScheduler: inexact by design, so under Doze or app standby a job waits for the next
 * maintenance window, and the owner's 30-minute reminder arrived 25 minutes before the event. An
 * alarm set with [AlarmManager.setExactAndAllowWhileIdle] fires on the minute, in Doze too.
 *
 * **Exact needs a permission, and not having it is not an error.** API 33 and later hold
 * `USE_EXACT_ALARM`, which Google Play allows a calendar that shows event notifications (declared in
 * the Play Console); API 31 and 32 hold `SCHEDULE_EXACT_ALARM`, granted at install and revocable in
 * system settings; API 26–30 need nothing. Where it is not held, the alarm is armed inexact with
 * [AlarmManager.setAndAllowWhileIdle] — still fired in Doze, minutes late at worst — rather than not
 * at all; `ReminderRearmReceiver` re-arms when the permission comes back.
 *
 * **One alarm per event, told apart by the intent's data**, never by the request code: an
 * `eventId.hashCode()` request code collides, and two events sharing a `PendingIntent` would cancel
 * each other's reminder.
 */
class SystemReminderAlarms(
    private val context: Context
) : ReminderAlarms {

    private val alarmManager: AlarmManager
        get() = context.getSystemService(AlarmManager::class.java)

    override fun set(due: DueReminder, triggerAtMillis: Long) {
        val operation = PendingIntent.getBroadcast(
            context,
            REQUEST_CODE,
            ReminderAlarmReceiver.intentFor(context, due),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        if (exactAllowed()) {
            try {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, operation)
                return
            } catch (e: SecurityException) {
                // Revoked between the check and the call; the inexact alarm below still fires.
                logger.w(TAG, "Exact alarms refused; arming an inexact reminder", e)
            }
        }
        alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, operation)
    }

    override fun cancel(eventId: String) {
        PendingIntent.getBroadcast(
            context,
            REQUEST_CODE,
            ReminderAlarmReceiver.intentFor(context, eventId),
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
        )?.let { operation ->
            alarmManager.cancel(operation)
            operation.cancel()
        }
        WorkManager.getInstance(context).cancelUniqueWork(legacyWorkName(eventId))
    }

    override fun cancelLegacyWork() {
        WorkManager.getInstance(context).cancelAllWorkByTag(LEGACY_WORK_TAG)
    }

    override suspend fun takeLegacyWork(eventIds: Collection<String>): Set<String> = withContext(Dispatchers.IO) {
        val workManager = WorkManager.getInstance(context)
        eventIds.filterTo(mutableSetOf()) { id ->
            val queued = workManager.getWorkInfosForUniqueWork(legacyWorkName(id)).get()
                .any { it.state == WorkInfo.State.ENQUEUED }
            if (queued) workManager.cancelUniqueWork(legacyWorkName(id))
            queued
        }
    }

    /** Whether this phone lets the app arm an exact alarm; always below API 31. */
    private fun exactAllowed(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarmManager.canScheduleExactAlarms()

    private companion object {
        const val TAG = "ReminderAlarms"

        /** The same for every reminder: the intent's data tells them apart. */
        const val REQUEST_CODE = 0

        /** The unique work name an older build queued a reminder under. */
        fun legacyWorkName(eventId: String) = "event_reminder_$eventId"

        /** The tag an older build put on every reminder work. */
        const val LEGACY_WORK_TAG = "event_reminder"
    }
}
