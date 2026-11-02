package com.coparently.app.data.notification

import com.coparently.app.data.local.entity.EventEntity
import com.coparently.app.domain.model.Event

/**
 * The part of a stored event a reminder reads — when its occurrences start, its title and its
 * lead time — as the [Event] [com.coparently.app.domain.notification.ReminderScheduler] takes.
 *
 * For the two callers that hold a Room row rather than a domain event: [ReminderWorker], which
 * re-arms a recurring event after it fires, and the sync, which arms the co-parent's events as
 * they arrive. Deliberately not a full mapping (audience, members and the rest are left at their
 * defaults): the result is handed to the scheduler and never saved, and the full mapping is
 * `EventRepositoryImpl`'s.
 */
internal fun EventEntity.toReminderEvent(): Event = Event(
    id = id,
    title = title,
    startDateTime = startDateTime,
    endDateTime = endDateTime,
    eventType = eventType,
    parentOwner = parentOwner,
    isRecurring = isRecurring,
    recurrencePattern = recurrencePattern,
    recurrenceEndDate = recurrenceEndDate,
    reminderMinutes = reminderMinutes,
    isPrivate = isPrivate,
    createdAt = createdAt,
    updatedAt = updatedAt
)
