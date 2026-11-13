package com.coparently.app.data.school.edupage

import com.coparently.app.domain.serialization.StoredJson
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * Lenient readers over a JSON tree for the EduPage parsers: a missing, null or mistyped field is
 * null or empty, never an exception, so an odd field costs that field and not the response.
 */
internal object EdupageJson {

    /** [text] as a tree, or null when it is not JSON. */
    fun parseOrNull(text: String): JsonElement? = try {
        StoredJson.parse(text)
    } catch (_: SerializationException) {
        null
    }

    /** The field [name] as text (a string as it is, a number or boolean as written), or null. */
    fun JsonObject.text(name: String): String? = (get(name) as? JsonPrimitive)?.takeIf { it !== JsonNull }?.content

    /** The field [name] as an object, or null. */
    fun JsonObject.obj(name: String): JsonObject? = get(name) as? JsonObject

    /** The objects held by field [name], an array's objects or an object's object values. */
    fun JsonObject.items(name: String): List<JsonObject> = when (val value = get(name)) {
        is JsonArray -> value.mapNotNull { it as? JsonObject }
        is JsonObject -> value.values.mapNotNull { it as? JsonObject }
        else -> emptyList()
    }

    /** Field [name] of the object-or-array table [name] whose entry has id [id]. */
    fun JsonObject.entry(name: String, id: String): JsonObject? = when (val value = get(name)) {
        is JsonObject -> value[id] as? JsonObject
        is JsonArray -> value.firstNotNullOfOrNull { (it as? JsonObject)?.takeIf { row -> row.text("id") == id } }
        else -> null
    }

    /** `yyyy-MM-dd` as a date, or null. */
    fun date(text: String?): LocalDate? = text?.trim()?.let { runCatching { LocalDate.parse(it) }.getOrNull() }

    /** `HH:mm` or `HH:mm:ss` as a time, with `24:00` read as 23:59; null when unreadable. */
    fun time(text: String?): LocalTime? {
        val value = text?.trim()
        return when {
            value == null -> null
            value in END_OF_DAY -> LocalTime(LAST_HOUR, LAST_MINUTE)
            else -> clock(value)
        }
    }

    private fun clock(value: String): LocalTime? {
        val parts = value.split(':')
        val hour = parts.getOrNull(0)?.toIntOrNull()
        val minute = parts.getOrNull(1)?.toIntOrNull()
        val second = parts.getOrNull(2)?.toIntOrNull() ?: 0
        return when {
            parts.size !in CLOCK_PARTS || hour == null || minute == null -> null
            hour in 0..LAST_HOUR && minute in 0..LAST_MINUTE && second in 0..LAST_MINUTE ->
                LocalTime(hour, minute, second)
            else -> null
        }
    }

    private val END_OF_DAY = setOf("24:00", "24:00:00")
    private val CLOCK_PARTS = 2..3
    private const val LAST_HOUR = 23
    private const val LAST_MINUTE = 59
}
