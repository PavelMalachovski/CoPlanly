package com.coparently.app.wire

import com.coparently.app.data.sync.EventDocument
import com.coparently.app.data.versions.EventVersionDocument
import com.coparently.app.data.versions.EventVersionKind
import com.coparently.app.domain.model.Event
import java.time.LocalDateTime

/**
 * `event_versions`: read by `EventVersionDocument.Parsed.from` (the export), written by
 * `EventVersionDocument.document` (`EventVersionRecorder`). A revision is create-only for every
 * client (CLAUDE.md item 25), so there is no round trip: no build ever writes back a revision it
 * read. What [invariants] pins instead is the outbox — the event snapshot survives
 * `encodeSnapshot`/`decodeSnapshot` through Room unchanged, numbers included.
 */
internal object EventVersionWireContract : WireContract {

    override val collection = "event_versions"

    override val alwaysWrites = setOf(
        EventVersionDocument.EVENT_ID,
        EventVersionDocument.KIND,
        EventVersionDocument.EDITOR_UID,
        EventVersionDocument.DEVICE_TIME_MILLIS,
        EventVersionDocument.SHARED_WITH,
        EventVersionDocument.FAMILY_ID,
        EventVersionDocument.EVENT,
        EventVersionDocument.FORMAT_VERSION
    )

    override fun read(document: Map<String, Any?>): Map<String, Any?>? {
        // `FirestoreEventVersionDataSource` converts the server Timestamp before parsing; so do we.
        val recordedAt = (document[EventVersionDocument.RECORDED_AT] as? WireTimestamp)?.millis
        val parsed = EventVersionDocument.Parsed.from(VERSION_ID, document, recordedAt) ?: return null
        return mapOf(
            "eventId" to parsed.eventId,
            "kind" to parsed.kind.wire,
            "editorUid" to parsed.editorUid,
            "deviceTimeMillis" to parsed.deviceTimeMillis,
            "recordedAtMillis" to parsed.recordedAtMillis,
            "familyId" to parsed.familyId,
            "recordedByServer" to parsed.recordedByServer,
            "eventTitle" to parsed.snapshot["title"],
            "writeKey" to EventVersionDocument.writeKey(parsed.snapshot)
        )
    }

    override fun roundTrip(document: Map<String, Any?>): Map<String, Any?>? = null

    override fun invariants(document: Map<String, Any?>): List<String> {
        @Suppress("UNCHECKED_CAST")
        val snapshot = document[EventVersionDocument.EVENT] as? Map<String, Any?> ?: return emptyList()
        val throughOutbox = EventVersionDocument.decodeSnapshot(EventVersionDocument.encodeSnapshot(snapshot))
        val lost = WireCompare.lostPaths(snapshot, throughOutbox)
        return if (lost.isEmpty()) emptyList() else listOf("the Room outbox changes the event snapshot at $lost")
    }

    override fun currentWrites(): List<CurrentWrite> = listOf(
        CurrentWrite(
            case = "updated",
            about = "EventVersionRecorder's revision of an edit, before the data source adds recordedAt.",
            document = EventVersionDocument.document(
                eventId = "evt-current-1",
                kind = EventVersionKind.UPDATED,
                editorUid = "uidA",
                deviceTimeMillis = 1_778_000_000_000L,
                audience = listOf("uidA", "uidB"),
                familyId = "uidA__uidB",
                snapshot = EventDocument.fromEvent(
                    Event(
                        id = "evt-current-1",
                        title = "Dentist",
                        startDateTime = LocalDateTime.of(2026, 5, 12, 9, 30),
                        eventType = "medical",
                        parentOwner = "mom",
                        createdAt = LocalDateTime.of(2026, 5, 1, 8, 0),
                        updatedAt = LocalDateTime.of(2026, 5, 5, 16, 53, 20),
                        reminderMinutes = 15,
                        familyId = "uidA__uidB"
                    ),
                    creatorUid = "uidA",
                    audience = listOf("uidA", "uidB")
                )
            )
        )
    )

    private const val VERSION_ID = "fixture"
}
