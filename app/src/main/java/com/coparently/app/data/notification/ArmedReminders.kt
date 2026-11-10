package com.coparently.app.data.notification

import android.content.Context

/**
 * The events this phone has a reminder alarm armed for, remembered across process deaths.
 *
 * Two things only this list can answer, because the platform cannot. **Alarms do not survive a
 * reboot**, so [ReminderRearm] arms exactly this set again after one, after a clock or time-zone
 * change and after an update. And **AlarmManager cannot list or cancel by tag**, so
 * [EventReminderScheduler.cancelAll] cancels each id here at sign-out, an account switch and
 * deletion, and empties the list — a signed-out account's reminders then never come back with the
 * next reboot. Event ids only, never a title or a time: those are read from Room when a reminder is
 * due.
 *
 * Plain [android.content.SharedPreferences]: an event id is a random UUID or a hash, not something
 * about the family, and the app's backup is off, so the list never reaches another phone.
 *
 * One per process (`notificationModule`'s `single`): its lock is what keeps two edits apart.
 */
class ArmedReminders(
    context: Context
) {
    private val prefs by lazy { context.getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE) }
    private val lock = Any()

    /** Records that [eventId] has an alarm armed. */
    fun add(eventId: String) {
        edit { it + eventId }
    }

    /** Records that [eventId] has no alarm armed any more. */
    fun remove(eventId: String) {
        edit { it - eventId }
    }

    /** Whether [eventId] has an alarm armed. */
    fun contains(eventId: String): Boolean = eventId in ids()

    /** Every event with an alarm armed. */
    fun ids(): Set<String> = synchronized(lock) {
        prefs.getStringSet(KEY_IDS, null).orEmpty().toSet()
    }

    /** Every event with an alarm armed, forgetting them all: what [EventReminderScheduler.cancelAll] cancels. */
    fun takeAll(): Set<String> = synchronized(lock) {
        val all = ids()
        prefs.edit().remove(KEY_IDS).apply()
        all
    }

    /**
     * Whether the reminders an older build queued as WorkManager work have been moved onto alarms
     * ([ReminderRearm]). Set once; the work it looks for is never queued again.
     */
    var legacyWorkMoved: Boolean
        get() = prefs.getBoolean(KEY_LEGACY_WORK_MOVED, false)
        set(value) = prefs.edit().putBoolean(KEY_LEGACY_WORK_MOVED, value).apply()

    private fun edit(change: (Set<String>) -> Set<String>) {
        synchronized(lock) {
            val before = ids()
            val after = change(before)
            if (after != before) prefs.edit().putStringSet(KEY_IDS, after).apply()
        }
    }

    private companion object {
        const val FILE_NAME = "event_reminder_alarms"
        const val KEY_IDS = "armed_event_ids"
        const val KEY_LEGACY_WORK_MOVED = "legacy_work_moved"
    }
}
