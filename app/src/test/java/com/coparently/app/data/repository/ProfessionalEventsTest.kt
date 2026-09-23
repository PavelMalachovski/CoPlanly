package com.coparently.app.data.repository

import org.junit.Test
import java.time.LocalDate
import kotlin.test.assertEquals

/** What a professional's calendar decodes from raw `events` documents (MON-18). */
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
