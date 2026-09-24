package com.coparently.app.data.sync

import com.coparently.app.data.local.entity.EventEntity
import com.coparently.app.domain.events.EventTimestamp
import com.coparently.app.domain.family.FamilyMemberRef
import com.coparently.app.domain.model.Event
import com.google.gson.Gson
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/**
 * The one reader of an `events` Firestore document, and its two full-document writers.
 *
 * [fromEvent] (what `EventRepositoryImpl.toFirestoreMap()` delegates to) is the single definition
 * of how an event is *written* (CLAUDE.md, "Things that are easy to get wrong", item 5), and
 * [uploadDocument] is `SyncService`'s upload of a queued row. [toEntity] is their counterpart,
 * extracted from `SyncService` when a second caller appeared: the change-request inbox now
 * fetches an event it does not have locally, and a second copy of this mapping would be one more
 * place for the schema to drift out of step.
 *
 * All three live here, pure, so the wire-format contract tests (`app/src/test/.../wire/`,
 * fixtures in `app/src/test/resources/wire/`) run the production mappers on the JVM.
 */
internal object EventDocument {

    private val formatter: DateTimeFormatter = DateTimeFormatter.ISO_LOCAL_DATE_TIME
    private val dateOnlyFormatter: DateTimeFormatter = DateTimeFormatter.ISO_LOCAL_DATE
    private val gson = Gson()

    /**
     * Reads one document into a Room row, already marked as coming from the server.
     *
     * Throws rather than guessing when a required field is missing or malformed — a caller
     * mirroring a snapshot catches per document, so one unparseable event does not take the
     * rest of the calendar with it.
     *
     * @param data The raw document.
     */
    fun toEntity(data: Map<String, Any?>): EventEntity = toEntity(
        data = data,
        // Read as UTC — what an upgraded build writes — and a legacy naive value the same way,
        // which is wrong by its writer's offset and cannot be otherwise (see `EventTimestamp`).
        updatedAtMillis = EventTimestamp.fromWire(data["updatedAt"] as String)
    )

    @Suppress("UNCHECKED_CAST")
    private fun toEntity(data: Map<String, Any?>, updatedAtMillis: Long): EventEntity = EventEntity(
        id = data["id"] as String,
        title = data["title"] as String,
        description = data["description"] as? String,
        startDateTime = LocalDateTime.parse(data["startDateTime"] as String, formatter),
        // Blank reads as "no end", like every other optional date here. `toFirestoreMap()` writes
        // `""` for an event without one, so parsing it unguarded threw, and the per-document catch
        // on the reading side then skipped the event: the co-parent never received it at all.
        endDateTime = (data["endDateTime"] as? String)?.ifBlank { null }
            ?.let { LocalDateTime.parse(it, formatter) },
        eventType = data["eventType"] as String,
        parentOwner = data["parentOwner"] as String,
        isRecurring = data["isRecurring"] as? Boolean ?: false,
        recurrencePattern = (data["recurrencePattern"] as? String)?.ifBlank { null },
        recurrenceEndDate = (data["recurrenceEndDate"] as? String)?.ifBlank { null }
            ?.let { LocalDate.parse(it) },
        pickupConfirmedBy = (data["pickupConfirmedBy"] as? String)?.ifBlank { null },
        pickupConfirmedAt = (data["pickupConfirmedAt"] as? String)?.ifBlank { null }
            ?.let { LocalDateTime.parse(it, formatter) },
        createdAt = LocalDateTime.parse(data["createdAt"] as String, formatter),
        // The wall clock shown on this phone is the instant in this phone's zone, not the
        // writer's: the wire value no longer names the writer's wall clock at all.
        updatedAt = EventTimestamp.toWallClock(updatedAtMillis),
        updatedAtMillis = updatedAtMillis,
        syncedToFirestore = true,
        createdByFirebaseUid = data["createdByFirebaseUid"] as? String,
        sharedWithJson = gson.toJson(data["sharedWith"] ?: emptyList<String>()),
        lastModifiedBy = data["lastModifiedBy"] as? String,
        permissions = data["permissions"] as? String ?: "read_write",
        imageUrl = (data["imageUrl"] as? String)?.ifBlank { null },
        // Absent reads as NOT_REQUIRED: every document written before this field existed was
        // created without an acceptance step, and defaulting the other way would hide it.
        acceptance = (data["acceptance"] as? String)?.ifBlank { null } ?: "NOT_REQUIRED",
        acceptedBy = (data["acceptedBy"] as? String)?.ifBlank { null },
        acceptedAt = (data["acceptedAt"] as? String)?.ifBlank { null }
            ?.let { LocalDateTime.parse(it, formatter) },
        // Absent reads as false: a document written before this field existed carries no such
        // expectation, and inventing one would put an exclamation mark on somebody else's
        // ordinary event.
        isImportant = data["isImportant"] as? Boolean ?: false,
        friendParticipates = (data["friendParticipates"] as? String)?.takeIf { it.isNotEmpty() },
        // Firestore returns numbers as Long; null when the document predates the field.
        reminderMinutes = (data["reminderMinutes"] as? Number)?.toInt(),
        // Absent reads as "the whole family", which is what every event written before the
        // reference type is. A reference this build does not understand is carried through
        // rather than dropped, so a co-parent on a newer build cannot have their tag erased by
        // an edit made here — see `FamilyMemberRef.Unknown`.
        forMembersJson = membersJson(data["forMembers"]),
        // Absent reads as null — "belongs to nobody but its creator" — which is what every
        // document written before the field existed is.
        familyId = (data["familyId"] as? String)?.takeIf { it.isNotEmpty() }
    )

    /**
     * The document a save through `EventRepositoryImpl` writes. Single source of truth for the
     * remote schema; moved here unchanged from the repository.
     *
     * @param event The event being saved.
     * @param creatorUid Stamped as `createdByFirebaseUid`.
     * @param audience The `sharedWith` uids this write publishes to.
     */
    fun fromEvent(event: Event, creatorUid: String, audience: List<String>): Map<String, Any?> = with(event) {
        mapOf(
            "id" to id,
            "title" to title,
            "description" to (description ?: ""),
            "startDateTime" to startDateTime.format(formatter),
            "endDateTime" to (endDateTime?.format(formatter) ?: ""),
            "eventType" to eventType,
            "parentOwner" to parentOwner,
            "isRecurring" to isRecurring,
            "recurrencePattern" to (recurrencePattern ?: ""),
            "recurrenceEndDate" to (recurrenceEndDate?.format(dateOnlyFormatter) ?: ""),
            "pickupConfirmedBy" to (pickupConfirmedBy ?: ""),
            "pickupConfirmedAt" to (pickupConfirmedAt?.format(formatter) ?: ""),
            "createdAt" to createdAt.format(formatter),
            // UTC, offset-free: the field keeps its name and type so an older build still parses
            // it, and only the zone it expresses changed (MON-4, see `EventTimestamp`).
            "updatedAt" to EventTimestamp.toWire(EventTimestamp.ofWallClock(updatedAt)),
            "createdByFirebaseUid" to creatorUid,
            "sharedWith" to audience,
            "lastModifiedBy" to (lastModifiedBy ?: creatorUid),
            "permissions" to permissions,
            "imageUrl" to (imageUrl ?: ""),
            "acceptance" to acceptance.name,
            "acceptedBy" to (acceptedBy ?: ""),
            "acceptedAt" to (acceptedAt?.format(formatter) ?: ""),
            "isImportant" to isImportant,
            "friendParticipates" to (friendParticipates ?: ""),
            // The relationship this event belongs to. It will replace `sharedWith` entirely:
            // the rules read the family's members, so the audience stops being a copy carried
            // on the document that can go stale (CLAUDE.md item 16). Both are written while the
            // read rules still consult the old one.
            "familyId" to (familyId ?: ""),
            // Who the event is about, as prefixed strings. An empty array is "the whole family",
            // never an absent key: the read side distinguishes neither, but a document whose
            // fields are iterated should not have a hole where a schema field belongs.
            "forMembers" to FamilyMemberRef.store(forMembers),
            // Round-tripped, not dropped: omitting it here meant the download half of a full
            // sync REPLACEd the creator's own row with a map that had no reminder, wiping the
            // value and (on the next update) cancelling the scheduled WorkManager reminder.
            "reminderMinutes" to reminderMinutes
        )
    }

    /**
     * The document `SyncService` uploads, with `set()`, for a queued Room row.
     *
     * Moved here from `SyncService.syncEvents`. It writes `null` where [fromEvent] writes `""`;
     * [toEntity] reads both as absent. It now carries `reminderMinutes` too: without it, an event
     * created offline and uploaded by the sync reached the server with no reminder, and the
     * download half of the same sync then replaced the creator's own row with that document —
     * the loss [fromEvent]'s comment describes, on the path it did not cover.
     *
     * @param entity The queued row.
     * @param audience The `sharedWith` uids, from `SyncService.shareTargets`.
     */
    fun uploadDocument(entity: EventEntity, audience: List<String>): Map<String, Any?> = mapOf(
        "id" to entity.id,
        "title" to entity.title,
        "description" to entity.description,
        "startDateTime" to entity.startDateTime.format(formatter),
        "endDateTime" to entity.endDateTime?.format(formatter),
        "eventType" to entity.eventType,
        "parentOwner" to entity.parentOwner,
        "isRecurring" to entity.isRecurring,
        "recurrencePattern" to entity.recurrencePattern,
        "recurrenceEndDate" to entity.recurrenceEndDate?.toString(),
        "pickupConfirmedBy" to entity.pickupConfirmedBy,
        "pickupConfirmedAt" to entity.pickupConfirmedAt?.format(formatter),
        "createdAt" to entity.createdAt.format(formatter),
        // The instant, as UTC text — the same wire form [fromEvent] writes (MON-4).
        "updatedAt" to EventTimestamp.toWire(entity.updatedAtMillis),
        "createdByFirebaseUid" to entity.createdByFirebaseUid,
        "sharedWith" to audience,
        "lastModifiedBy" to entity.lastModifiedBy,
        "permissions" to entity.permissions,
        "imageUrl" to entity.imageUrl,
        "acceptance" to entity.acceptance,
        "acceptedBy" to entity.acceptedBy,
        "acceptedAt" to entity.acceptedAt?.format(formatter),
        "isImportant" to entity.isImportant,
        "friendParticipates" to (entity.friendParticipates ?: ""),
        "forMembers" to storedMembers(entity.forMembersJson),
        "familyId" to (entity.familyId ?: ""),
        "reminderMinutes" to entity.reminderMinutes
    )

    /**
     * The JSON a Room row stores, from a document's `forMembers` array.
     *
     * Normalised on the way in — blanks dropped, duplicates collapsed — so a row's stored form
     * does not depend on which build wrote the document.
     */
    fun membersJson(raw: Any?): String = gson.toJson(FamilyMemberRef.store(FamilyMemberRef.parse(raw)))

    /**
     * The `forMembers` array a document carries, from a Room row's JSON column.
     *
     * Here rather than in `SyncService` because this file is the one place the events wire
     * format is defined; a second copy of the conversion is one more place for it to drift.
     */
    fun storedMembers(forMembersJson: String): List<String> {
        val stored = runCatching {
            gson.fromJson(forMembersJson, Array<String>::class.java)?.toList()
        }.getOrNull()
        return FamilyMemberRef.store(FamilyMemberRef.parse(stored))
    }
}
