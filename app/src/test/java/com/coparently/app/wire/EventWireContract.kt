package com.coparently.app.wire

import com.coparently.app.data.local.entity.EventEntity
import com.coparently.app.data.sync.EventDocument
import com.coparently.app.data.sync.Tombstone
import com.coparently.app.domain.events.EventAcceptance
import com.coparently.app.domain.events.EventTimestamp
import com.coparently.app.domain.family.FamilyMemberRef
import com.coparently.app.domain.model.Event
import com.google.gson.Gson
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/**
 * `events`: read by `EventDocument.toEntity` (the sync's download and the change-request inbox),
 * written back by `EventDocument.uploadDocument` (the sync's `set()` of a queued row) — the path
 * that replaces the whole document, so it is the one that can lose something. A save through the
 * repository writes `EventDocument.fromEvent`, pinned as a current fixture; its edits go out as
 * `update()`, which merges and cannot drop a key.
 */
internal object EventWireContract : WireContract {

    private val gson = Gson()
    private val formatter: DateTimeFormatter = DateTimeFormatter.ISO_LOCAL_DATE_TIME

    override val collection = "events"

    override val alwaysWrites = setOf(
        "id", "title", "startDateTime", "eventType", "parentOwner", "createdAt", "updatedAt",
        "createdByFirebaseUid", "sharedWith", "familyId", "forMembers", "reminderMinutes"
    )

    /** Every optional text the reader takes through `ifBlank { null }`; the two writers disagree on `""`. */
    override val blankMeansAbsent = setOf(
        "endDateTime", "recurrencePattern", "recurrenceEndDate", "pickupConfirmedBy", "pickupConfirmedAt",
        "imageUrl", "acceptedBy", "acceptedAt", "friendParticipates", "familyId"
    )

    override fun read(document: Map<String, Any?>): Map<String, Any?>? {
        // The sync answers a tombstone from the raw document before it maps anything.
        if (Tombstone.isDeleted(document)) {
            return mapOf(
                "id" to document["id"],
                "deleted" to true,
                "deletedAtMillis" to Tombstone.deletedAtMillisIn(document)
            )
        }
        val entity = runCatching { EventDocument.toEntity(document) }.getOrNull() ?: return null
        return mapOf(
            "id" to entity.id,
            "title" to entity.title,
            "description" to entity.description,
            "startDateTime" to entity.startDateTime.format(formatter),
            "endDateTime" to entity.endDateTime?.format(formatter),
            "eventType" to entity.eventType,
            "parentOwner" to entity.parentOwner,
            "isRecurring" to entity.isRecurring,
            "recurrencePattern" to entity.recurrencePattern,
            "updatedAtMillis" to entity.updatedAtMillis,
            "createdByFirebaseUid" to entity.createdByFirebaseUid,
            "sharedWith" to sharedWith(entity),
            "acceptance" to entity.acceptance,
            "isImportant" to entity.isImportant,
            "reminderMinutes" to entity.reminderMinutes,
            "forMembers" to EventDocument.storedMembers(entity.forMembersJson),
            "familyId" to entity.familyId,
            "deleted" to false
        )
    }

    override fun roundTrip(document: Map<String, Any?>): Map<String, Any?>? {
        // A deletion is never written back: the row is removed as soon as the tombstone arrives.
        if (Tombstone.isDeleted(document)) return null
        val entity = EventDocument.toEntity(document)
        return EventDocument.uploadDocument(entity, sharedWith(entity))
    }

    override fun currentWrites(): List<CurrentWrite> = listOf(
        CurrentWrite(
            case = "repository-save",
            about = "A save through EventRepositoryImpl (EventDocument.fromEvent): every optional field set.",
            document = EventDocument.fromEvent(savedEvent(), creatorUid = ALICE, audience = listOf(ALICE, BOB))
        ),
        CurrentWrite(
            case = "repository-save-minimal",
            about = "A save through EventRepositoryImpl of an event with nothing optional: blanks, not absent keys.",
            document = EventDocument.fromEvent(minimalEvent(), creatorUid = ALICE, audience = listOf(ALICE))
        ),
        CurrentWrite(
            case = "sync-upload",
            about = "SyncService uploading a queued row with set() (EventDocument.uploadDocument).",
            document = EventDocument.uploadDocument(queuedRow(), audience = listOf(ALICE, BOB))
        ),
        CurrentWrite(
            case = "tombstone",
            about = "A deleted event: the saved document with Tombstone.fields merged in by update().",
            document = EventDocument.fromEvent(minimalEvent(), creatorUid = ALICE, audience = listOf(ALICE, BOB)) +
                Tombstone.fields(deletedAtMillis = DELETED_AT, deletedBy = ALICE)
        )
    )

    private fun sharedWith(entity: EventEntity): List<String> =
        gson.fromJson(entity.sharedWithJson, Array<String>::class.java)?.toList().orEmpty()

    private fun savedEvent() = Event(
        id = "evt-current-1",
        title = "Dentist",
        description = "Bring the card",
        startDateTime = LocalDateTime.of(2026, 5, 12, 9, 30),
        endDateTime = LocalDateTime.of(2026, 5, 12, 10, 15),
        eventType = "medical",
        parentOwner = "mom",
        isRecurring = true,
        recurrencePattern = "WEEKLY",
        recurrenceEndDate = LocalDate.of(2026, 6, 30),
        createdAt = LocalDateTime.of(2026, 5, 1, 8, 0),
        updatedAt = LocalDateTime.of(2026, 5, 2, 18, 45, 10),
        createdByFirebaseUid = ALICE,
        lastModifiedBy = BOB,
        pickupConfirmedBy = BOB,
        pickupConfirmedAt = LocalDateTime.of(2026, 5, 12, 8, 55),
        reminderMinutes = 30,
        imageUrl = "ph1|event_images/uidA__uidB/evt-current-1/c83d2e5f.jpg|image/jpeg|48213|" +
            "9f86d081884c7d659a2feaa0c55ad015a3bf4f1b2b0b822cd15d6c15b0f00a08",
        acceptance = EventAcceptance.ACCEPTED,
        acceptedBy = BOB,
        acceptedAt = LocalDateTime.of(2026, 5, 3, 7, 0),
        isImportant = true,
        forMembers = listOf(
            FamilyMemberRef.Child("c1"),
            FamilyMemberRef.Pet("p1"),
            FamilyMemberRef.Unknown("future:xyz")
        ),
        friendParticipates = "friend-1",
        familyId = FAMILY
    )

    private fun minimalEvent() = Event(
        id = "evt-current-2",
        title = "Swim",
        startDateTime = LocalDateTime.of(2026, 5, 14, 16, 0),
        eventType = "activity",
        parentOwner = "dad",
        createdAt = LocalDateTime.of(2026, 5, 1, 8, 0),
        updatedAt = LocalDateTime.of(2026, 5, 1, 8, 0)
    )

    private fun queuedRow() = EventEntity(
        id = "evt-current-3",
        title = "Handover",
        description = null,
        startDateTime = LocalDateTime.of(2026, 5, 15, 17, 0),
        endDateTime = LocalDateTime.of(2026, 5, 15, 17, 30),
        eventType = "handover",
        parentOwner = "mom",
        createdAt = LocalDateTime.of(2026, 5, 1, 8, 0),
        updatedAt = LocalDateTime.of(2026, 5, 4, 12, 0),
        updatedAtMillis = EventTimestamp.fromWire("2026-05-04T10:00:00"),
        createdByFirebaseUid = ALICE,
        sharedWithJson = "[\"$ALICE\",\"$BOB\"]",
        lastModifiedBy = ALICE,
        reminderMinutes = 15,
        forMembersJson = "[\"child:c1\",\"future:xyz\"]",
        familyId = FAMILY
    )

    private const val ALICE = "uidA"
    private const val BOB = "uidB"
    private const val FAMILY = "uidA__uidB"
    private const val DELETED_AT = 1_778_000_000_000L
}
