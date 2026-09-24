package com.coparently.app.data.sync

import com.coparently.app.domain.events.EventTimestamp
import com.coparently.app.domain.family.FamilyMemberRef
import org.junit.Test
import java.time.Instant
import java.time.LocalDateTime
import java.time.format.DateTimeParseException
import java.util.TimeZone
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

/**
 * The events wire format, at the one field that crosses between two builds in both directions.
 *
 * `EventDocument` is the single reader of an `events` document and, since the member reference
 * landed, the single converter of the Room JSON column into the document's array. Both halves
 * are pinned here because the failure they prevent is silent: a tag that vanishes on the other
 * parent's phone looks exactly like a tag nobody set.
 */
class EventDocumentTest {

    @Test
    fun `a document's members round-trip through the Room column`() {
        val json = EventDocument.membersJson(listOf("child:c1", "pet:p1"))
        assertEquals(listOf("child:c1", "pet:p1"), EventDocument.storedMembers(json))
    }

    @Test
    fun `an event about nobody is the whole family, in both directions`() {
        // Every event created before the reference type existed. An absent key must not become
        // anything other than "no members", or an upgrade starts hiding events behind a filter.
        assertEquals("[]", EventDocument.membersJson(null))
        assertEquals(emptyList(), EventDocument.storedMembers("[]"))
    }

    @Test
    fun `a reference this build does not understand is carried, not dropped`() {
        // A co-parent on a newer build tags an event with something this one has no name for.
        // Dropping it here would mean an edit made on this phone erases their tag.
        val json = EventDocument.membersJson(listOf("grandparent:g1", "child:c1"))
        assertEquals(listOf("grandparent:g1", "child:c1"), EventDocument.storedMembers(json))
    }

    @Test
    fun `the stored form does not depend on which build wrote the document`() {
        // Normalised on the way in: blanks dropped, duplicates collapsed, non-strings ignored.
        val json = EventDocument.membersJson(listOf("child:c1", "", "child:c1", 7, null))
        assertEquals(listOf("child:c1"), EventDocument.storedMembers(json))
    }

    @Test
    fun `a corrupt column reads as no members rather than throwing`() {
        // The column is NOT NULL with a '[]' default, so this should be unreachable — but it is
        // read on the path that draws the grid, and a throw there takes the calendar down.
        assertEquals(emptyList(), EventDocument.storedMembers("not json"))
    }

    @Test
    fun `an event with no end time is read, not skipped`() {
        // `EventRepositoryImpl.toFirestoreMap()` writes "" for a missing end, and the reader used
        // to parse that as a date-time and throw — so the co-parent's sync dropped the event.
        val document = mapOf<String, Any?>(
            "id" to "e1",
            "title" to "Pickup",
            "startDateTime" to "2026-09-24T15:00:00",
            "endDateTime" to "",
            "eventType" to "pickup",
            "parentOwner" to "mom",
            "createdAt" to "2026-09-23T09:00:00",
            "updatedAt" to "2026-09-23T09:00:00"
        )
        val entity = EventDocument.toEntity(document)
        assertEquals("e1", entity.id)
        assertNull(entity.endDateTime)
    }

    @Test
    fun `updatedAt is read as the instant its UTC text names`() {
        // MON-4: an upgraded build writes the instant as offset-free UTC text. The row carries
        // that instant for `ConflictResolver`, and this phone's own wall clock for display.
        val entity = withDefaultZone("GMT+02:00") {
            EventDocument.toEntity(minimalDocument(updatedAt = "2026-09-23T09:00:00"))
        }
        assertEquals(Instant.parse("2026-09-23T09:00:00Z").toEpochMilli(), entity.updatedAtMillis)
        assertEquals(LocalDateTime.of(2026, 9, 23, 11, 0), entity.updatedAt)
    }

    @Test
    fun `a legacy naive updatedAt is still read, as UTC`() {
        // An older build writes its own wall clock with no offset. Nothing recovers the offset it
        // was written in, so it is read as UTC — and, above all, read: throwing here would skip
        // every event a co-parent on an older build creates.
        val entity = EventDocument.toEntity(minimalDocument(updatedAt = "2026-09-23T09:00:00.250"))
        assertEquals(Instant.parse("2026-09-23T09:00:00.250Z").toEpochMilli(), entity.updatedAtMillis)
    }

    @Test
    fun `what this build writes is what it reads back`() {
        val millis = Instant.parse("2026-09-23T07:30:15.125Z").toEpochMilli()
        val entity = EventDocument.toEntity(minimalDocument(updatedAt = EventTimestamp.toWire(millis)))
        assertEquals(millis, entity.updatedAtMillis)
    }

    @Test
    fun `a document with an unreadable updatedAt is refused, not dated`() {
        // As before MON-4: the caller skips the document rather than inventing a time that would
        // win or lose a conflict it has no business deciding.
        assertFailsWith<DateTimeParseException> {
            EventDocument.toEntity(minimalDocument(updatedAt = "not a date"))
        }
    }

    private fun minimalDocument(updatedAt: String) = mapOf<String, Any?>(
        "id" to "e1",
        "title" to "Pickup",
        "startDateTime" to "2026-09-24T15:00:00",
        "eventType" to "pickup",
        "parentOwner" to "mom",
        "createdAt" to "2026-09-23T09:00:00",
        "updatedAt" to updatedAt
    )

    private inline fun <T> withDefaultZone(id: String, block: () -> T): T {
        val original = TimeZone.getDefault()
        TimeZone.setDefault(TimeZone.getTimeZone(id))
        return try {
            block()
        } finally {
            TimeZone.setDefault(original)
        }
    }

    @Test
    fun `the document keeps the prefixes the reference type defines`() {
        val json = EventDocument.membersJson(
            FamilyMemberRef.store(listOf(FamilyMemberRef.Child("c1"), FamilyMemberRef.Pet("p1")))
        )
        assertEquals(listOf("child:c1", "pet:p1"), EventDocument.storedMembers(json))
    }
}
