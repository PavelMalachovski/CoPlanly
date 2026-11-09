package com.coparently.app.data.notification

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/** What the reminder receivers need from the app's graph; a receiver cannot be injected directly. */
@EntryPoint
@InstallIn(SingletonComponent::class)
interface ReminderReceiverEntryPoint {
    /** Delivers a due reminder. */
    fun dueReminders(): DueReminders

    /** Arms every remembered reminder again. */
    fun reminderRearm(): ReminderRearm
}

/**
 * Receives a reminder's alarm ([SystemReminderAlarms]) and delivers it ([DueReminders]).
 *
 * Not exported: only the app's own `PendingIntent`, which `AlarmManager` sends as the app, reaches
 * it. The event id is the intent's data, which is also what tells two events' alarms apart.
 */
class ReminderAlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_REMINDER_DUE) return
        val eventId = intent.data?.schemeSpecificPart ?: return
        val startAt = intent.getStringExtra(EXTRA_START_AT)
        val startTime = intent.getStringExtra(EXTRA_START_TIME)
        val entryPoint = EntryPointAccessors.fromApplication(
            context.applicationContext,
            ReminderReceiverEntryPoint::class.java
        )
        runAsync(TAG) { entryPoint.dueReminders().deliver(eventId, startAt, startTime) }
    }

    /** The intents the alarms carry, built in one place so arming and cancelling agree. */
    companion object {
        private const val TAG = "ReminderAlarmReceiver"
        private const val ACTION_REMINDER_DUE = "com.coparently.app.action.REMINDER_DUE"
        private const val URI_SCHEME = "coplanly-reminder"
        private const val EXTRA_START_AT = "start_at"
        private const val EXTRA_START_TIME = "start_time"

        /**
         * The intent that cancels [eventId]'s alarm: it matches the armed one by action, component
         * and data, which is what `PendingIntent` compares (extras are not).
         */
        fun intentFor(context: Context, eventId: String): Intent =
            Intent(context, ReminderAlarmReceiver::class.java)
                .setAction(ACTION_REMINDER_DUE)
                .setData(Uri.fromParts(URI_SCHEME, eventId, null))

        /** The intent that arms [due]'s alarm. */
        fun intentFor(context: Context, due: DueReminder): Intent =
            intentFor(context, due.eventId)
                .putExtra(EXTRA_START_AT, due.occurrenceStart.toString())
                .putExtra(EXTRA_START_TIME, due.startTime)
    }
}

/**
 * Arms the remembered reminders again ([ReminderRearm]) after a reboot, a clock or time-zone change,
 * an update of the app, and when the phone starts allowing exact alarms.
 *
 * Not exported, like WorkManager's own reschedule receiver: the system still delivers these
 * broadcasts to it, and no other app can make it re-arm.
 */
class ReminderRearmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action !in REARM_ACTIONS) return
        val entryPoint = EntryPointAccessors.fromApplication(
            context.applicationContext,
            ReminderReceiverEntryPoint::class.java
        )
        runAsync(TAG) { entryPoint.reminderRearm().rearm() }
    }

    private companion object {
        const val TAG = "ReminderRearmReceiver"

        val REARM_ACTIONS = setOf(
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED,
            // AlarmManager.ACTION_SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED, API 31: spelled out
            // so the set reads the same on every API level, where the constant is an InlinedApi.
            "android.app.action.SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED"
        )
    }
}

/** How long a receiver may keep its broadcast open: well inside the system's ten seconds. */
private const val RECEIVE_BUDGET_MILLIS = 9_000L

/**
 * Runs [work] off the main thread while the broadcast stays open ([BroadcastReceiver.goAsync]),
 * and closes it when [work] ends, fails or runs out of time. A failure is logged, never thrown: an
 * uncaught one would take the process down with the reminder.
 */
private fun BroadcastReceiver.runAsync(tag: String, work: suspend () -> Unit) {
    val pending = goAsync()
    val onFailure = CoroutineExceptionHandler { _, e -> Log.e(tag, "Reminder work failed", e) }
    CoroutineScope(SupervisorJob() + Dispatchers.IO + onFailure).launch {
        try {
            if (withTimeoutOrNull(RECEIVE_BUDGET_MILLIS) { work() } == null) {
                Log.w(tag, "Reminder work ran out of time")
            }
        } finally {
            pending.finish()
        }
    }
}
