package com.coparently.app.data.repository

import com.coparently.app.domain.custody.DayOverride
import com.coparently.app.domain.custody.DayOverrideStatus
import com.coparently.app.domain.serialization.StoredJson
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * The Room mirror's encoding of [DayOverride]s, keyed by ISO date.
 *
 * Room stores them as one nullable JSON column rather than a table of their own: they are read
 * and written as a whole map every time — the shared document carries them as one field, and a
 * write replaces all of them — so a second table would buy nothing but a join and a migration.
 * The same shape `UserEntity` uses for allergies and the medical profile.
 *
 * **Null and `{}` both mean "none", and encoding chooses null.** A row that has never carried a
 * swap must be byte-identical to one written before the column existed, because
 * `CustodyModelRepository.mirrorIntoRoom` compares whole entities to decide whether to re-insert
 * — and a re-insert ticks Room's invalidation tracker, re-emits to every observer, and does it
 * again on the next Firestore echo. That is also why the text is [StoredJson]'s, which writes
 * exactly what Gson wrote before it (MON-24).
 */
object DayOverrideJson {

    private val overridesByDate = MapSerializer(String.serializer(), DayOverride.serializer())
    private val entries = MapSerializer(String.serializer(), JsonElement.serializer())
    private val statuses = DayOverrideStatus.entries.map { it.name }.toSet()

    /** [overrides] as JSON, or null when there are none. */
    fun encode(overrides: Map<String, DayOverride>): String? =
        overrides.takeIf { it.isNotEmpty() }?.let { StoredJson.encode(overridesByDate, it) }

    /**
     * [json] as a map, or empty when it is null, blank or unreadable.
     *
     * Unreadable degrades to "no swaps" rather than throwing: this is on the path that paints
     * the calendar, and a malformed column must cost the swaps, not the grid. The document
     * remains the real record, so the next mirror pass restores them.
     *
     * An entry whose status this build cannot name — or that has none — is left out, as the
     * document's reader leaves one out (CLAUDE.md item 44). Gson read it with a null status, which
     * the first `when` over it would have thrown on; a default would have made it a pending offer.
     */
    fun decode(json: String?): Map<String, DayOverride> {
        if (json.isNullOrBlank()) return emptyMap()
        return runCatching {
            StoredJson.decodeOrNull(entries, json).orEmpty()
                .filterValues { entry -> entry.statusName() in statuses }
                .let { known -> StoredJson.decodeElement(overridesByDate, JsonObject(known)) }
        }
            .getOrNull()
            .orEmpty()
    }

    private fun JsonElement.statusName(): String? =
        ((this as? JsonObject)?.get("status") as? JsonPrimitive)?.takeIf { it !== JsonNull }?.content
}
