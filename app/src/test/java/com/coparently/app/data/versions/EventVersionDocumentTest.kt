package com.coparently.app.data.versions

import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * [EventVersionDocument] — the one definition of a revision's wire form (MON-4).
 *
 * The rule in `firestore.rules` names these keys and these three kinds; a drift on this side is
 * a revision the server refuses for ever, so the vocabulary is pinned here as well as there.
 */
class EventVersionDocumentTest {

    @Test
    fun `the three kinds are spelled as the rule accepts them`() {
        assertEquals(
            listOf("created", "updated", "deleted"),
            EventVersionKind.entries.map { it.wire }
        )
    }

    @Test
    fun `a kind this build does not know reads as nothing, never as a guess`() {
        assertNull(EventVersionKind.fromWire("restored"))
        assertNull(EventVersionKind.fromWire(null))
    }

    @Test
    fun `a document carries exactly the keys the rule allows, minus the server's own`() {
        val document = EventVersionDocument.document(
            eventId = "e1",
            kind = EventVersionKind.CREATED,
            editorUid = "alice",
            deviceTimeMillis = 1L,
            audience = listOf("alice"),
            familyId = null,
            snapshot = mapOf("id" to "e1")
        )

        assertEquals(
            setOf(
                "eventId",
                "kind",
                "editorUid",
                "deviceTimeMillis",
                "sharedWith",
                "familyId",
                "event",
                "formatVersion"
            ),
            document.keys
        )
        // Blank, not absent: the rule requires the key.
        assertEquals("", document[EventVersionDocument.FAMILY_ID])
    }

    @Test
    fun `a downloaded revision reads back with both clocks`() {
        val parsed = EventVersionDocument.Parsed.from(
            versionId = "v1",
            data = mapOf(
                "eventId" to "e1",
                "kind" to "deleted",
                "editorUid" to "bob",
                "deviceTimeMillis" to 1_787_000_000_000L,
                "familyId" to "alice__bob",
                "event" to mapOf("id" to "e1", "title" to "Dentist")
            ),
            recordedAtMillis = 1_787_000_004_000L
        )

        requireNotNull(parsed)
        assertEquals(EventVersionKind.DELETED, parsed.kind)
        assertEquals("bob", parsed.editorUid)
        assertEquals(1_787_000_000_000L, parsed.deviceTimeMillis)
        assertEquals(1_787_000_004_000L, parsed.recordedAtMillis)
        assertEquals("Dentist", parsed.snapshot["title"])
    }

    @Test
    fun `a revision missing its device time is not read at all`() {
        val parsed = EventVersionDocument.Parsed.from(
            versionId = "v1",
            data = mapOf(
                "eventId" to "e1",
                "kind" to "updated",
                "editorUid" to "bob",
                "event" to mapOf("id" to "e1")
            ),
            recordedAtMillis = null
        )

        assertNull(parsed)
    }

    @Test
    fun `the outbox round-trips whole numbers as whole numbers and keeps nulls`() {
        val json = EventVersionDocument.encodeSnapshot(
            mapOf("reminderMinutes" to 15, "endDateTime" to null, "isImportant" to true)
        )

        val back = EventVersionDocument.decodeSnapshot(json)

        assertEquals(15L, back["reminderMinutes"])
        assertEquals(true, back["isImportant"])
        assertEquals(true, back.containsKey("endDateTime"))
    }
}
