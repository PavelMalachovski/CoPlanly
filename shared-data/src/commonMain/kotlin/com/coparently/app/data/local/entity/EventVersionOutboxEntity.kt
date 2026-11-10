package com.coparently.app.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * One saved revision of an event, waiting to reach `event_versions` (MON-4, schema 37).
 *
 * **An outbox, not a history.** The history lives in Firestore, where no client may change or
 * delete it; a row here exists only until the server has the revision, and is deleted then. It
 * is written in the same call that saves the event, because the event write paths are
 * best-effort — `EventRepositoryImpl.updateEvent` discards the result of its remote write — and a
 * revision written the same way would be lost exactly when the phone was offline.
 *
 * @property id The revision's Firestore document id, minted once on this device. Stable across
 *   retries, so a write whose acknowledgement was lost is recognised by reading the document
 *   back rather than written a second time.
 * @property eventId The event this is a revision of.
 * @property kind `created`, `updated` or `deleted` — see `EventVersionKind`.
 * @property editorUid Who saved it. The rule refuses any other author, so a row left behind by a
 *   previous account on this device is never uploaded under the next one.
 * @property deviceTimeMillis When the parent saved it, epoch millis, from this device's clock.
 * @property snapshotJson The event document as `EventRepositoryImpl.toFirestoreMap()` built it,
 *   as JSON — see `EventVersionDocument.encodeSnapshot`.
 * @property audienceJson The event's audience when it was saved, as a JSON array of uids. Narrowed
 *   again to live pairing state at upload, never widened.
 * @property familyId The event's `familyId`, or null for an event that belonged to nobody else.
 * @property attempts How many uploads the server has refused. A row that reaches
 *   `EventVersionRecorder.MAX_REFUSALS` stays, and stops being retried: it is still this device's
 *   record of what the parent saved, and the export prints it as such.
 */
@Entity(tableName = "event_version_outbox")
data class EventVersionOutboxEntity(
    @PrimaryKey
    val id: String,
    val eventId: String,
    val kind: String,
    val editorUid: String,
    val deviceTimeMillis: Long,
    val snapshotJson: String,
    val audienceJson: String,
    val familyId: String?,
    val attempts: Int
)
