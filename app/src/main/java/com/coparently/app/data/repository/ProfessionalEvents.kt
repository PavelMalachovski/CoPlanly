package com.coparently.app.data.repository

import com.coparently.app.data.sync.EventDocument
import com.coparently.app.data.sync.Tombstone
import com.coparently.app.domain.events.CalendarVisibility
import com.coparently.app.domain.events.EventAcceptance
import com.coparently.app.domain.model.Event
import com.coparently.app.domain.usecase.RecurrenceExpander
import java.time.LocalDate
import java.time.LocalTime

/**
 * The events a professional's (MON-18) or a calendar friend's (phase 2) read-only calendar shows,
 * from raw `events` documents.
 *
 * Reads through [EventDocument], the one reader of the events wire format, so these views cannot
 * drift from what the parents' sync decodes. Pure, so the filtering is testable without Firestore.
 */
object ProfessionalEvents {

    /**
     * The occurrences overlapping [from]..[to], sorted by start.
     *
     * Four things are dropped: a **tombstone** (`deletedAtMillis` set — the event was deleted and
     * only waits for the co-parent's phone to collect it), anything marked private (none should
     * exist in Firestore, CLAUDE.md item 3, and if one did it would still not be shown), a
     * document this build cannot parse — one bad record must not blank the whole view — and an
     * event still waiting for the other parent's answer or declined by them, which the parents'
     * own grid hides too ([CalendarVisibility], applied to the masters before expansion as it
     * asks): a reader outside the pair must not see what the two have not agreed on.
     *
     * @param documents Raw documents from the family-scoped query.
     * @param from First day shown, inclusive.
     * @param to Last day shown, inclusive.
     */
    fun from(documents: List<Map<String, Any?>>, from: LocalDate, to: LocalDate): List<Event> {
        val masters = documents
            .filter { it[Tombstone.DELETED_AT_MILLIS] == null }
            .filter { it["isPrivate"] != true }
            .mapNotNull { runCatching { toEvent(it) }.getOrNull() }
        return RecurrenceExpander.expandAll(
            CalendarVisibility.visible(masters),
            from.atStartOfDay(),
            to.atTime(LocalTime.MAX)
        )
    }

    private fun toEvent(data: Map<String, Any?>): Event {
        val row = EventDocument.toEntity(data)
        return Event(
            id = row.id,
            title = row.title,
            description = row.description,
            startDateTime = row.startDateTime,
            endDateTime = row.endDateTime,
            eventType = row.eventType,
            parentOwner = row.parentOwner,
            isRecurring = row.isRecurring,
            recurrencePattern = row.recurrencePattern,
            createdAt = row.createdAt,
            updatedAt = row.updatedAt,
            syncedToFirestore = true,
            createdByFirebaseUid = row.createdByFirebaseUid,
            recurrenceEndDate = row.recurrenceEndDate,
            isImportant = row.isImportant,
            familyId = row.familyId,
            lastModifiedBy = row.lastModifiedBy,
            // As `EventRepositoryImpl` reads it: a status from a newer build shows rather than
            // throws, the safe direction on the path that draws the calendar.
            acceptance = runCatching { EventAcceptance.valueOf(row.acceptance) }
                .getOrDefault(EventAcceptance.NOT_REQUIRED),
            acceptedBy = row.acceptedBy,
            acceptedAt = row.acceptedAt,
            friendParticipates = row.friendParticipates
        )
    }
}
