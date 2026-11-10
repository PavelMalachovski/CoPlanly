package com.coparently.app.data.notification

import android.content.Context
import android.text.format.DateFormat
import com.coparently.app.domain.model.Event
import com.coparently.app.domain.notification.ReminderPlanner
import com.coparently.app.domain.notification.ReminderScheduler
import com.coparently.app.utils.format
import com.coparently.app.utils.inAppLanguage
import com.coparently.app.utils.shortTime
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock

/**
 * [ReminderScheduler] on alarms ([ReminderAlarms]; exact where the phone allows it).
 *
 * Each event has at most one alarm armed, for its next occurrence whose reminder is still ahead
 * ([ReminderPlanner]); arming again replaces it, and [DueReminders] arms the one after when it
 * fires. Three things not to undo.
 * - **The wall clock becomes an instant here, in the system zone, for the reminder's own date**:
 *   `remindAt.toInstant(zone)` resolves the offset in force *at* the reminder, so a reminder armed
 *   before a daylight-saving change fires at its wall-clock time after it. The WorkManager version
 *   waited `Duration.between` two wall clocks, and was an hour off across the change. A clock or
 *   zone change re-arms everything ([ReminderRearm]), because an event's time is a naive local time.
 * - **Every armed event is remembered** ([ArmedReminders]): alarms do not survive a reboot, and
 *   [cancelAll] has no tag to cancel by.
 * - **[cancelAll] forgets them all**, so nothing re-arms a signed-out account's reminders.
 */
class EventReminderScheduler(
    private val context: Context,
    private val alarms: ReminderAlarms,
    private val armed: ArmedReminders
) : ReminderScheduler {

    override fun schedule(event: Event) {
        val zone = TimeZone.currentSystemDefault()
        val plan = ReminderPlanner.nextReminder(event, Clock.System.now().toLocalDateTime(zone))
            ?: return cancel(event.id)
        val due = DueReminder(
            eventId = event.id,
            occurrenceStart = plan.occurrenceStart,
            // The reader's clock (release audit R-9), read here: no activity need have run in the
            // process that arms or delivers a reminder. In the language the app speaks
            // (inAppLanguage), as the notice's words and every push are, so "use locale default"
            // means that language's clock.
            startTime = startTimeText(plan.occurrenceStart)
        )
        alarms.set(due, triggerAtMillis = plan.remindAt.toInstant(zone).toEpochMilliseconds())
        armed.add(event.id)
    }

    override fun cancel(eventId: String) {
        alarms.cancel(eventId)
        armed.remove(eventId)
    }

    /**
     * Cancels the alarm of every event [ArmedReminders] names, and every reminder work an older
     * build queued. The rows that would name the events are what is being wiped, which is why the
     * armed list exists.
     */
    override fun cancelAll() {
        armed.takeAll().forEach(alarms::cancel)
        alarms.cancelLegacyWork()
    }

    /** [start]'s time on the reader's clock, in the language the app speaks now. */
    private fun startTimeText(start: LocalDateTime): String {
        val words = context.inAppLanguage()
        return start.format(shortTime(words.resources.configuration.locales[0], DateFormat.is24HourFormat(words)))
    }
}
