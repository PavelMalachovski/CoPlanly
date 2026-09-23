package com.coparently.app.data.repository

import com.coparently.app.domain.custody.ContactWindow
import com.coparently.app.domain.custody.ContactWindowCodec
import com.google.gson.Gson

/**
 * Room's form of a pattern's contact windows (MON-6b): a JSON array of the
 * [ContactWindowCodec] strings, the same strings the Firestore document carries.
 *
 * Gson only ever sees an array of strings here, never the [ContactWindow] data class, so R8
 * has no field names to rename (the reason `FamilyMemberRef` crosses the wire the same way).
 * Mirrors [DayOverrideJson]'s two rules: none is stored as null, and an unreadable column
 * degrades to no windows rather than throwing on the path that paints the calendar.
 */
object ContactWindowJson {

    private val gson = Gson()

    /** [windows] as JSON, or null when there are none. */
    fun encode(windows: List<ContactWindow>): String? =
        windows.takeIf { it.isNotEmpty() }?.let { gson.toJson(ContactWindowCodec.encodeAll(it)) }

    /** [json] as windows, or none when it is null, blank or unreadable. */
    fun decode(json: String?): List<ContactWindow> {
        if (json.isNullOrBlank()) return emptyList()
        val values = runCatching { gson.fromJson(json, Array<String>::class.java) }.getOrNull()
        return ContactWindowCodec.decodeAll(values?.toList())
    }
}
