package com.coparently.app.data.repository

import com.coparently.app.domain.events.EventAcceptance
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import kotlin.test.assertEquals

/**
 * What a professional's calendar decodes from raw `events` documents (MON-18) — and, since phase
 * 2, a family friend's: both read the family's events from Firestore, never from Room.
 */
class ProfessionalEventsTest {

    private val from = LocalDate.of(2026, 10, 1)
    private val to = LocalDate.of(2026, 10, 28)

    private fun doc(id: String, start: String, vararg overrides: Pair<String, Any?>): Map<String, Any?> = mapOf(
        "id" to id,
        "title" to "Event $id",
        "startDateTime" to start,
        "eventType" to "CUSTODY",
        "parentOwner" to "mom",
        "createdAt" to "2026-09-01T10:00:00",
        "updatedAt" to "2026-09-01T10:00:00",
        "createdByFirebaseUid" to "a",
        "familyId" to "a__b"
    ) + overrides

    @Test
    fun `keeps events in the window and drops those outside it`() {
        val events = ProfessionalEvents.from(
            listOf(doc("in", "2026-10-05T15:00:00"), doc("out", "2026-12-05T15:00:00")),
            from,
            to
        )
        assertEquals(listOf("in"), events.map { it.id })
    }

    @Test
    fun `drops a tombstone and anything marked private`() {
        val events = ProfessionalEvents.from(
            listOf(
                doc("gone", "2026-10-05T15:00:00", "deletedAtMillis" to 5L),
                doc("private", "2026-10-06T15:00:00", "isPrivate" to true),
                doc("kept", "2026-10-07T15:00:00")
            ),
            from,
            to
        )
        assertEquals(listOf("kept"), events.map { it.id })
    }

    @Test
    fun `an unparseable document is skipped, not fatal`() {
        val events = ProfessionalEvents.from(
            listOf(doc("broken", "not a date"), doc("fine", "2026-10-07T15:00:00")),
            from,
            to
        )
        assertEquals(listOf("fine"), events.map { it.id })
    }

    @Test
    fun `an event waiting for an answer or declined is not shown`() {
        // Phase 2, Review Focus 3: the parents' own grid hides these (CalendarVisibility), and a
        // reader outside the pair must not see an event the two have not agreed on.
        val events = ProfessionalEvents.from(
            listOf(
                doc("pending", "2026-10-05T15:00:00", "acceptance" to "PENDING"),
                doc("declined", "2026-10-06T15:00:00", "acceptance" to "DECLINED"),
                doc("accepted", "2026-10-07T15:00:00", "acceptance" to "ACCEPTED"),
                doc("plain", "2026-10-08T15:00:00"),
                doc("newer", "2026-10-09T15:00:00", "acceptance" to "SOMETHING_NEWER")
            ),
            from,
            to
        )
        assertEquals(listOf("accepted", "plain", "newer"), events.map { it.id })
    }

    @Test
    fun `who takes part survives the mapping`() {
        val event = ProfessionalEvents.from(
            listOf(
                doc(
                    "with-friend",
                    "2026-10-05T15:00:00",
                    "friendParticipates" to "f1",
                    "acceptance" to "ACCEPTED",
                    "acceptedBy" to "b",
                    "acceptedAt" to "2026-09-02T08:30:00",
                    "lastModifiedBy" to "b"
                )
            ),
            from,
            to
        ).single()
        assertEquals("f1", event.friendParticipates)
        assertEquals(EventAcceptance.ACCEPTED, event.acceptance)
        assertEquals("b", event.acceptedBy)
        assertEquals(LocalDateTime.of(2026, 9, 2, 8, 30), event.acceptedAt)
        assertEquals("b", event.lastModifiedBy)
    }

    @Test
    fun `a weekly event is expanded across the window`() {
        val events = ProfessionalEvents.from(
            listOf(
                doc(
                    "weekly",
                    "2026-10-01T15:00:00",
                    "isRecurring" to true,
                    "recurrencePattern" to "weekly"
                )
            ),
            from,
            to
        )
        assertEquals(4, events.size)
    }
}
