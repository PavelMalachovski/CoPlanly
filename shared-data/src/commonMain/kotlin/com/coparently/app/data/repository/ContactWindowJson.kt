package com.coparently.app.data.repository

import com.coparently.app.domain.custody.ContactWindow
import com.coparently.app.domain.custody.ContactWindowCodec
import com.coparently.app.domain.serialization.StoredJson

/**
 * Room's form of a pattern's contact windows (MON-6b): a JSON array of the
 * [ContactWindowCodec] strings, the same strings the Firestore document carries.
 *
 * The JSON only ever holds an array of strings here, never the [ContactWindow] data class, so the
 * stored text depends on no class's field names (the reason `FamilyMemberRef` crosses the wire the
 * same way).
 * Mirrors [DayOverrideJson]'s two rules: none is stored as null, and an unreadable column
 * degrades to no windows rather than throwing on the path that paints the calendar.
 */
object ContactWindowJson {

    /** [windows] as JSON, or null when there are none. */
    fun encode(windows: List<ContactWindow>): String? =
        windows.takeIf { it.isNotEmpty() }
            ?.let { StoredJson.encode(StoredJson.strings, ContactWindowCodec.encodeAll(it)) }

    /** [json] as windows, or none when it is null, blank or unreadable. */
    fun decode(json: String?): List<ContactWindow> {
        if (json.isNullOrBlank()) return emptyList()
        val values = runCatching { StoredJson.decodeOrNull(StoredJson.strings, json) }.getOrNull()
        return ContactWindowCodec.decodeAll(values)
    }
}
