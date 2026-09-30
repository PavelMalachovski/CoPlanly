package com.coparently.app.domain.notification

import com.coparently.app.domain.model.Event
import com.coparently.app.domain.usecase.RecurrenceExpander
import java.time.LocalDateTime

/**
 * The one reminder an event has pending: the occurrence it is for and when it is due.
 *
 * @property occurrenceStart The start of the occurrence the reminder announces — the master start
 * for a single event, one of its [RecurrenceExpander] occurrences for a recurring one.
 * @property remindAt [occurrenceStart] minus the event's lead time; always after the `now` it was
 * planned against.
 */
data class ReminderPlan(
    val occurrenceStart: LocalDateTime,
    val remindAt: LocalDateTime
)

/**
 * Decides which occurrence of an event the next reminder is for. Pure, so the JVM tests reach
 * every rule; `EventReminderScheduler` only turns the answer into WorkManager work.
 *
 * A recurring event is expanded through [RecurrenceExpander] — the same occurrences the calendar
 * draws, recurrence end date included — so a weekly event reminds before each week's occurrence
 * rather than only before its first one. `ReminderWorker` asks again after each reminder fires.
 */
object ReminderPlanner {

    /**
     * How far past `now` a recurring event is searched for its next occurrence. The longest gap
     * between two occurrences of any supported pattern is a month (31 days); twice that leaves
     * room for the lead time. A pattern with no occurrence inside it has none left to remind of.
     */
    private const val SEARCH_HORIZON_DAYS = 62L

    /**
     * The next reminder for [event] still due after [now], or null when there is none: no
     * reminder set (`reminderMinutes` null — the form's "None" — or negative), a single event
     * whose reminder time has passed, or a recurring one whose occurrences have ended.
     * A reminder due exactly at [now] counts as passed, as the scheduler always treated it.
     */
    fun nextReminder(event: Event, now: LocalDateTime): ReminderPlan? {
        val lead = event.reminderMinutes?.takeIf { it >= 0 }?.toLong() ?: return null
        // Start - lead > now  <=>  start > now + lead.
        val earliestStart = now.plusMinutes(lead)
        val start: LocalDateTime? = if (isRecurring(event)) {
            RecurrenceExpander.expand(instant(event), earliestStart, earliestStart.plusDays(SEARCH_HORIZON_DAYS))
                .asSequence()
                .map { it.startDateTime }
                .firstOrNull { it > earliestStart }
        } else {
            event.startDateTime.takeIf { it > earliestStart }
        }
        return start?.let { ReminderPlan(occurrenceStart = it, remindAt = it.minusMinutes(lead)) }
    }

    /**
     * Whether [start] is the start of one of [event]'s occurrences as the event stands now —
     * its master start for a single event. What a due reminder is checked against: a reminder
     * for an occurrence the event no longer has (moved, or past a new end date) stays silent.
     */
    fun isOccurrence(event: Event, start: LocalDateTime): Boolean =
        if (isRecurring(event)) {
            RecurrenceExpander.expand(instant(event), start, start).any { it.startDateTime == start }
        } else {
            event.startDateTime == start
        }

    private fun isRecurring(event: Event): Boolean =
        event.isRecurring && !event.recurrencePattern.isNullOrBlank()

    /**
     * The event without its end: a reminder is about when an occurrence starts, so occurrences are
     * expanded as instants — an occurrence still running at `now` is not a future one, and a
     * malformed end before the start cannot hide one.
     */
    private fun instant(event: Event): Event = event.copy(endDateTime = null)
}
