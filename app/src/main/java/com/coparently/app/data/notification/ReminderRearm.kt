@file:OptIn(InternalSharedApi::class)

package com.coparently.app.data.notification

import com.coparently.app.data.local.dao.EventDao
import com.coparently.app.domain.InternalSharedApi
import com.coparently.app.domain.notification.ReminderScheduler

/**
 * Arms again every reminder this phone had armed, from the events as Room holds them now.
 *
 * Run by `ReminderRearmReceiver` after a reboot (alarms do not survive one), a change of the
 * clock or the time zone (an event's time is a naive local time, so its instant moved), an update of
 * the app, and when exact alarms become allowed (an inexact one is then armed exact).
 *
 * The first run also moves the reminders an older build queued as WorkManager work onto alarms:
 * that build's work is still waiting for every event it armed, and would fire as late as before.
 * Only events with queued work are moved, so an account that signed out (whose work was cancelled)
 * gets nothing back.
 */
class ReminderRearm(
    private val eventDao: EventDao,
    private val scheduler: ReminderScheduler,
    private val alarms: ReminderAlarms,
    private val armed: ArmedReminders
) {

    /** Arms every remembered reminder again, or cancels it when its event is gone. */
    suspend fun rearm() {
        val ids = armed.ids() + movedFromLegacyWork()
        for (id in ids) {
            val stored = eventDao.getEventById(id)
            if (stored == null || stored.deletedAtMillis != null) {
                scheduler.cancel(id)
            } else {
                scheduler.schedule(stored.toReminderEvent())
            }
        }
    }

    /** The events an older build's reminder work was waiting for — once; see the class comment. */
    private suspend fun movedFromLegacyWork(): Set<String> {
        if (armed.legacyWorkMoved) return emptySet()
        val moved = alarms.takeLegacyWork(eventDao.getEventIdsWithReminders())
        alarms.cancelLegacyWork()
        armed.legacyWorkMoved = true
        return moved
    }
}
