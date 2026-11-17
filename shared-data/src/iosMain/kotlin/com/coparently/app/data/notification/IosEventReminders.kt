package com.coparently.app.data.notification

import com.coparently.app.data.remote.firebase.PushDestination
import com.coparently.app.data.remote.firebase.PushPayload
import com.coparently.app.domain.model.Event
import com.coparently.app.domain.notification.ReminderPlanner
import com.coparently.app.domain.notification.ReminderScheduler
import com.coparently.app.pushwording.ReminderWording
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Instant

/**
 * [ReminderScheduler] on the iPhone (iPhone phase 4 part C, design §7.4): local notifications,
 * planned by [EventReminderPlanner] and kept by [IosLocalReminders]. In place of the stand-in
 * `NoEventReminders`.
 *
 * Every call only asks the book to plan again ([IosLocalReminders.requestRearm]), which reads the
 * events from Room: a reminder is one per event and for its next occurrence, as on Android, and the
 * nearest ones only fit the iPhone's limit, so no single event can be armed on its own. [cancelAll]
 * removes every reminder, at sign-out, an account switch and deletion (CLAUDE.md item 9).
 */
class IosEventReminders(private val book: IosLocalReminders) : ReminderScheduler {

    override fun schedule(event: Event) = book.requestRearm()

    override fun cancel(eventId: String) = book.requestRearm()

    override fun cancelAll() = book.requestCancelAll()
}

/**
 * Plans the iPhone's event reminders (design §7.4): for each event with a reminder, the next
 * occurrence whose reminder is still ahead ([ReminderPlanner.nextReminder], the same rule Android's
 * alarm is armed by), its wall clock made an instant in the phone's zone for the reminder's own date
 * (as Android's `EventReminderScheduler` does), worded by [ReminderWording.event] — the title and
 * "Starts at <time>" on the reader's clock, in the language the app speaks — when it is armed, since
 * nothing runs when it is shown.
 *
 * What Android checks when its alarm fires (that the event still exists, and the occurrence is still
 * one) is decided at the last re-arm: an event deleted on the other phone while this app was never
 * opened can still remind once (device check).
 *
 * A tap opens the calendar on the event's own family, as Android's reminder does, and on the family
 * showing for a private event, which belongs to none (design item 46). It names the signed-in
 * account, so a reminder left from the previous one opens nothing.
 *
 * @param events The events this phone holds that carry a reminder (Room's, tombstones left out).
 * @param uid The signed-in account; nothing is planned while nobody is signed in.
 * @param wording The reminders' wording, in the language the app speaks now.
 * @param zone The phone's zone.
 */
class EventReminderPlanner(
    private val events: suspend () -> List<Event>,
    private val uid: () -> String?,
    private val wording: () -> ReminderWording,
    private val zone: () -> TimeZone = { TimeZone.currentSystemDefault() },
) {

    /** The reminders due after [nowMillis], one per event at most, in no particular order. */
    suspend fun plan(nowMillis: Long): List<LocalReminder> {
        val account = uid()?.takeIf { it.isNotBlank() } ?: return emptyList()
        val zone = zone()
        val now = Instant.fromEpochMilliseconds(nowMillis).toLocalDateTime(zone)
        val candidates = events()
        if (candidates.isEmpty()) return emptyList()
        val words = wording()
        return candidates.mapNotNull { event ->
            val plan = ReminderPlanner.nextReminder(event, now) ?: return@mapNotNull null
            val text = words.event(event.title, plan.occurrenceStart.toInstant(zone).toEpochMilliseconds())
            LocalReminder(
                id = idOf(event.id),
                atMillis = plan.remindAt.toInstant(zone).toEpochMilliseconds(),
                title = text.title,
                body = text.body,
                data = tapData(account, event),
            )
        }
    }

    /** What a tap opens: the calendar, on the event's own family unless it is private. */
    private fun tapData(account: String, event: Event): Map<String, String> = buildMap {
        put(PushPayload.TARGET_USER_ID, account)
        put(PushDestination.EXTRA, PushDestination.CALENDAR.key)
        event.familyId?.takeIf { it.isNotBlank() && !event.isPrivate }?.let { put(PushPayload.FAMILY_ID, it) }
    }

    /** The ids' shape. */
    companion object {
        /** The reminder's id for [eventId]: one per event, as Android's alarm is. */
        fun idOf(eventId: String): String = IosLocalReminders.EVENT_PREFIX + eventId
    }
}
