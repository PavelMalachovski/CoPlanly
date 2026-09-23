package com.coparently.app.data.versions

import com.google.gson.GsonBuilder
import com.google.gson.ToNumberPolicy
import com.google.gson.reflect.TypeToken

/**
 * What a saved revision of an event looks like on the wire, defined once (MON-4).
 *
 * `event_versions/{versionId}` holds one immutable document per create, update and delete of a
 * non-private event — the design is `docs/DESIGN-court-record.md` §4 and the invariants are
 * CLAUDE.md item 25. This file is the vocabulary both halves share: the recorder writes through
 * [document] and the export reads through [Parsed], so the field names cannot drift between them
 * the way the events schema once drifted between `EventRepositoryImpl` and `SyncService`.
 *
 * The `event` field is **the event document itself**, exactly as
 * `EventRepositoryImpl.toFirestoreMap()` built it for the save being recorded — not a second
 * mapping of the event. A revision in any other shape would be a claim about the event that the
 * event's own schema does not make.
 */
object EventVersionDocument {

    /** The collection. Top-level, so the 90-day event sweep can never reach it (design §4). */
    const val COLLECTION = "event_versions"

    const val EVENT_ID = "eventId"
    const val KIND = "kind"
    const val EDITOR_UID = "editorUid"
    const val DEVICE_TIME_MILLIS = "deviceTimeMillis"

    /** Server-stamped on arrival; the rule requires it to equal `request.time`. */
    const val RECORDED_AT = "recordedAt"
    const val SHARED_WITH = "sharedWith"
    const val FAMILY_ID = "familyId"
    const val EVENT = "event"
    const val FORMAT_VERSION = "formatVersion"

    /** The shape of this document. Bump it, and teach [Parsed] the old one, if it ever changes. */
    const val CURRENT_FORMAT = 1

    /**
     * Numbers come back as `Long` when they are whole, so a snapshot that went through Room and
     * back uploads `reminderMinutes: 15`, not `15.0` — the same value the event document holds.
     */
    private val gson = GsonBuilder()
        .serializeNulls()
        .setObjectToNumberStrategy(ToNumberPolicy.LONG_OR_DOUBLE)
        .create()
    private val mapType = object : TypeToken<Map<String, Any?>>() {}.type
    private val listType = object : TypeToken<List<String>>() {}.type

    /** The event document as JSON, for the Room outbox. */
    fun encodeSnapshot(snapshot: Map<String, Any?>): String = gson.toJson(snapshot)

    /** The event document back out of the Room outbox; empty when the JSON is unreadable. */
    fun decodeSnapshot(json: String): Map<String, Any?> =
        runCatching { gson.fromJson<Map<String, Any?>>(json, mapType) }.getOrNull().orEmpty()

    /** An audience as JSON, for the Room outbox. */
    fun encodeAudience(audience: List<String>): String = gson.toJson(audience)

    /** An audience back out of the Room outbox; empty when the JSON is unreadable. */
    fun decodeAudience(json: String): List<String> =
        runCatching { gson.fromJson<List<String>>(json, listType) }.getOrNull().orEmpty()

    /**
     * The document to upload, minus [RECORDED_AT], which only the caller can stamp — it has to be
     * `FieldValue.serverTimestamp()`, and this file stays free of Firebase so it can be tested on
     * the JVM.
     */
    // Seven values that are the document's own fields; wrapping them in a type would only rename
    // the list this function exists to spell out.
    @Suppress("LongParameterList")
    fun document(
        eventId: String,
        kind: EventVersionKind,
        editorUid: String,
        deviceTimeMillis: Long,
        audience: List<String>,
        familyId: String?,
        snapshot: Map<String, Any?>
    ): Map<String, Any?> = mapOf(
        EVENT_ID to eventId,
        KIND to kind.wire,
        EDITOR_UID to editorUid,
        DEVICE_TIME_MILLIS to deviceTimeMillis,
        SHARED_WITH to audience,
        // Blank rather than absent: the rule requires the key, and blank is what "belongs to
        // nobody else" is everywhere else in this schema.
        FAMILY_ID to familyId.orEmpty(),
        EVENT to snapshot,
        FORMAT_VERSION to CURRENT_FORMAT
    )

    /**
     * One revision as read back, with the server time already converted to epoch millis.
     *
     * @property versionId The document id.
     * @property recordedAtMillis When the server received it, or null for a revision that has not
     *   reached the server — one still in this device's outbox.
     * @property snapshot The event document as it was saved.
     */
    data class Parsed(
        val versionId: String,
        val eventId: String,
        val kind: EventVersionKind,
        val editorUid: String,
        val deviceTimeMillis: Long,
        val recordedAtMillis: Long?,
        val familyId: String,
        val snapshot: Map<String, Any?>
    ) {
        companion object {

            /**
             * Reads a downloaded revision, or null when it is not one this build understands.
             *
             * Null rather than a guess: an export that invented a kind or a time for a revision
             * would be the one thing this record must never do.
             *
             * @param versionId The document id.
             * @param data The raw document.
             * @param recordedAtMillis The document's server time, already converted by the caller
             *   (a Firestore `Timestamp` is not available on the JVM this is tested on).
             */
            fun from(versionId: String, data: Map<String, Any?>, recordedAtMillis: Long?): Parsed? {
                val eventId = data[EVENT_ID] as? String
                val kind = EventVersionKind.fromWire(data[KIND] as? String)
                val editor = data[EDITOR_UID] as? String
                val deviceTime = (data[DEVICE_TIME_MILLIS] as? Number)?.toLong()

                @Suppress("UNCHECKED_CAST")
                val snapshot = (data[EVENT] as? Map<String, Any?>)
                if (eventId == null || kind == null || editor == null) return null
                if (deviceTime == null || snapshot == null) return null
                return Parsed(
                    versionId = versionId,
                    eventId = eventId,
                    kind = kind,
                    editorUid = editor,
                    deviceTimeMillis = deviceTime,
                    recordedAtMillis = recordedAtMillis,
                    familyId = (data[FAMILY_ID] as? String).orEmpty(),
                    snapshot = snapshot
                )
            }
        }
    }
}

/**
 * What a revision records: the event's creation, an edit, or its deletion.
 *
 * @property wire The value stored in the document; `firestore.rules` accepts exactly these three.
 */
enum class EventVersionKind(val wire: String) {
    CREATED("created"),
    UPDATED("updated"),
    DELETED("deleted");

    companion object {

        /** The kind stored as [wire], or null for a value this build does not know. */
        fun fromWire(wire: String?): EventVersionKind? = entries.firstOrNull { it.wire == wire }
    }
}
