package com.coparently.app.data.notification

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.Uri
import com.coparently.app.data.platform.logger
import com.coparently.app.domain.time.IsoText
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import org.koin.core.component.KoinComponent
import org.koin.core.component.get
import org.koin.core.context.GlobalContext

/**
 * Receives a reminder's alarm ([SystemReminderAlarms]) and delivers it ([DueReminders]).
 *
 * Not exported: only the app's own `PendingIntent`, which `AlarmManager` sends as the app, reaches
 * it. The event id is the intent's data, which is also what tells two events' alarms apart.
 */
class ReminderAlarmReceiver : BroadcastReceiver(), KoinComponent {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_REMINDER_DUE) return
        val eventId = intent.data?.schemeSpecificPart ?: return
        val startAt = intent.getStringExtra(EXTRA_START_AT)
        val startTime = intent.getStringExtra(EXTRA_START_TIME)
        if (!hasReminderGraph(TAG)) return
        runAsync(TAG) { get<DueReminders>().deliver(eventId, startAt, startTime) }
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
                // The text `java.time`'s `LocalDateTime.toString()` wrote, which `DueReminders` reads.
                .putExtra(EXTRA_START_AT, IsoText.dateTimeShortest(due.occurrenceStart))
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
class ReminderRearmReceiver : BroadcastReceiver(), KoinComponent {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action !in REARM_ACTIONS) return
        if (!hasReminderGraph(TAG)) return
        runAsync(TAG) { get<ReminderRearm>().rearm() }
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

/**
 * Whether the process has the app's Koin graph for a reminder receiver to resolve from.
 *
 * In the app it always has one: `CoPlanlyApplication` starts Koin before any receiver runs. An
 * instrumentation run is the exception, and it is what took CI down on 9 November 2026: installing
 * the test APK over the app delivers `MY_PACKAGE_REPLACED` into the test process, whose
 * `KoinTestApplication` starts no graph — a test's `KoinTestRule` starts one for that test alone
 * (`HiltTestApplication` before MON-24 phase 2, which had no component until a `HiltAndroidRule`
 * created one) — and an exception thrown out of `onReceive` crashed the process before the first
 * test. A broadcast with nothing to act on is logged and dropped, never thrown. What the graph is
 * asked for is resolved inside [runAsync], so a definition it lacks is logged there too.
 */
private fun hasReminderGraph(tag: String): Boolean {
    if (GlobalContext.getOrNull() != null) return true
    logger.w(tag, "No Koin graph; the broadcast is dropped")
    return false
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
    val onFailure = CoroutineExceptionHandler { _, e -> logger.e(tag, "Reminder work failed", e) }
    CoroutineScope(SupervisorJob() + Dispatchers.IO + onFailure).launch {
        try {
            if (withTimeoutOrNull(RECEIVE_BUDGET_MILLIS) { work() } == null) {
                logger.w(tag, "Reminder work ran out of time")
            }
        } finally {
            pending.finish()
        }
    }
}
