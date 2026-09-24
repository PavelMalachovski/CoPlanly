package com.coparently.app.data.repository

import com.coparently.app.domain.custody.ChildOverrideCodec
import com.coparently.app.domain.custody.ChildScheduleOverride
import com.coparently.app.domain.custody.DecodedChildOverrides
import com.coparently.app.domain.custody.SharedCustody
import com.google.gson.Gson

/**
 * Room's form of each child's own schedule (FAM-4): a JSON array of the [ChildOverrideCodec]
 * strings, the same strings the Firestore document carries — unreadable entries included,
 * verbatim, so a row never loses an override a newer build wrote.
 *
 * Gson only ever sees an array of strings, never the [ChildScheduleOverride] data class, so R8 has
 * no field names to rename. [SeasonalLayerJson]'s two rules hold: none is stored as null, and an
 * unreadable column degrades to no overrides rather than throwing on the path that paints the grid.
 */
object ChildOverrideJson {

    private val gson = Gson()

    /** The overrides as JSON, or null when there are none. */
    fun encode(overrides: List<ChildScheduleOverride>, unreadable: List<String>): String? =
        ChildOverrideCodec.encodeAll(overrides, unreadable)
            .takeIf { it.isNotEmpty() }
            ?.let { gson.toJson(it) }

    /**
     * What the mirror stores for [remote]'s overrides: the document's list, or — when the document
     * has no `childOverrides` key at all — [existing], unchanged. A missing key is a write by a
     * build that predates FAM-4, never "no overrides"; a removal this build makes is an explicit
     * empty list (item 24's rule).
     */
    fun mirrored(remote: SharedCustody, existing: String?): String? =
        if (remote.childOverridesWire == null) {
            existing
        } else {
            encode(remote.model.childOverrides, remote.model.unreadableChildOverrides)
        }

    /** [json] as overrides, or none when it is null, blank or unreadable. */
    fun decode(json: String?): DecodedChildOverrides {
        if (json.isNullOrBlank()) return DecodedChildOverrides()
        val values = runCatching { gson.fromJson(json, Array<String>::class.java) }.getOrNull()
        return ChildOverrideCodec.decodeAll(values?.toList())
    }
}
