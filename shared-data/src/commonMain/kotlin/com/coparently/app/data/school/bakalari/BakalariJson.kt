package com.coparently.app.data.school.bakalari

import com.coparently.app.domain.serialization.StoredJson
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.format.DateTimeParseException

/**
 * Lenient readers over a JSON tree (kotlinx.serialization's, read as leniently as Gson's
 * `JsonParser` read it before MON-24), for the Bakaláři parsers (MON-8).
 *
 * The parsers read fields by hand rather than through reflected DTO classes: a reflected model
 * needs R8 keep rules and a runtime probe case (`tools/check-invariants.js`), and the server's
 * shapes vary between schools anyway — `HourId` is a number on one and a string on another.
 * Every reader here returns null or empty for a missing, null or mistyped field and never throws,
 * so an unknown or odd field costs that field, not the response.
 */
internal object BakalariJson {

    /** [text] as a JSON object; throws [BakalariException.Malformed] when it is not one. */
    fun objectOf(text: String): JsonObject =
        parse(text) as? JsonObject ?: throw BakalariException.Malformed("Expected a JSON object")

    /** [text] as a JSON array; throws [BakalariException.Malformed] when it is not one. */
    fun arrayOf(text: String): JsonArray =
        parse(text) as? JsonArray ?: throw BakalariException.Malformed("Expected a JSON array")

    /**
     * The field [name] as text: a string as it is, a number or a boolean in its plain form. This
     * is the lenient read `HourId` and `Hours[].Id` need. Never trimmed — Bakaláři ids can carry
     * leading spaces (`" 6"`) and are compared verbatim.
     */
    fun JsonObject.text(name: String): String? = get(name)?.primitiveText()

    /** The field [name] as a boolean, or null. */
    fun JsonObject.bool(name: String): Boolean? {
        val primitive = get(name)?.primitive() ?: return null
        return when {
            primitive.isString -> primitive.content.toBooleanStrictOrNull()
            primitive.content == "true" -> true
            primitive.content == "false" -> false
            else -> null
        }
    }

    /** The field [name] as an object, or null. */
    fun JsonObject.obj(name: String): JsonObject? = get(name) as? JsonObject

    /** The objects in the array field [name]; anything else in it is skipped. */
    fun JsonObject.objects(name: String): List<JsonObject> =
        (get(name) as? JsonArray).orEmpty().mapNotNull { element -> element as? JsonObject }

    /** The strings in the array field [name]; anything else in it is skipped. */
    fun JsonObject.strings(name: String): List<String> =
        (get(name) as? JsonArray).orEmpty().mapNotNull { element -> element.primitiveText() }

    /**
     * An ISO date-time with an offset ("2020-03-02T00:00:00+01:00") as the school's own wall
     * clock: the offset is the school's zone, and the calendar keeps naive local times (CLAUDE.md
     * item 13). Falls back to a date-time without an offset; null when neither parses.
     */
    fun wallClock(text: String?): LocalDateTime? {
        val value = text?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        return parseOrNull { OffsetDateTime.parse(value).toLocalDateTime() }
            ?: parseOrNull { LocalDateTime.parse(value) }
    }

    private fun <T> parseOrNull(parse: () -> T): T? = try {
        parse()
    } catch (ignored: DateTimeParseException) {
        null
    }
}

/** [text] as a tree; throws [BakalariException.Malformed] when it is not JSON at all. */
private fun parse(text: String): JsonElement = try {
    StoredJson.parse(text)
} catch (e: SerializationException) {
    throw BakalariException.Malformed("Unreadable JSON", e)
}

/** A string, number or boolean — never `null`, which Gson's tree did not count as a primitive. */
private fun JsonElement.primitive(): JsonPrimitive? = (this as? JsonPrimitive)?.takeIf { it !== JsonNull }

/** A primitive as text: a string as it is, a number or a boolean as it was written. */
private fun JsonElement.primitiveText(): String? = primitive()?.content

private fun JsonArray?.orEmpty(): List<JsonElement> = this ?: emptyList()
