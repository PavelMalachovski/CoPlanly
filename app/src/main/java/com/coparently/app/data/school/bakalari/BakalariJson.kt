package com.coparently.app.data.school.bakalari

import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParseException
import com.google.gson.JsonParser
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.format.DateTimeParseException

/**
 * Lenient readers over Gson's tree model, for the Bakaláři parsers (MON-8).
 *
 * The parsers read fields by hand rather than through reflected DTO classes: a reflected model
 * needs R8 keep rules and a runtime probe case (`tools/check-invariants.js`), and the server's
 * shapes vary between schools anyway — `HourId` is a number on one and a string on another.
 * Every reader here returns null or empty for a missing, null or mistyped field and never throws,
 * so an unknown or odd field costs that field, not the response.
 */
internal object BakalariJson {

    /** [text] as a JSON object; throws [BakalariException.Malformed] when it is not one. */
    fun objectOf(text: String): JsonObject = try {
        JsonParser.parseString(text).takeIf { it.isJsonObject }?.asJsonObject
            ?: throw BakalariException.Malformed("Expected a JSON object")
    } catch (e: JsonParseException) {
        throw BakalariException.Malformed("Unreadable JSON", e)
    }

    /** [text] as a JSON array; throws [BakalariException.Malformed] when it is not one. */
    fun arrayOf(text: String): JsonArray = try {
        JsonParser.parseString(text).takeIf { it.isJsonArray }?.asJsonArray
            ?: throw BakalariException.Malformed("Expected a JSON array")
    } catch (e: JsonParseException) {
        throw BakalariException.Malformed("Unreadable JSON", e)
    }

    /**
     * The field [name] as text: a string as it is, a number or a boolean in its plain form. This
     * is the lenient read `HourId` and `Hours[].Id` need. Never trimmed — Bakaláři ids can carry
     * leading spaces (`" 6"`) and are compared verbatim.
     */
    fun JsonObject.text(name: String): String? {
        val element = get(name) ?: return null
        return if (element.isJsonPrimitive) element.asString else null
    }

    /** The field [name] as a boolean, or null. */
    fun JsonObject.bool(name: String): Boolean? {
        val element = get(name)?.takeIf { it.isJsonPrimitive } ?: return null
        val primitive = element.asJsonPrimitive
        return when {
            primitive.isBoolean -> primitive.asBoolean
            primitive.isString -> primitive.asString.toBooleanStrictOrNull()
            else -> null
        }
    }

    /** The field [name] as an object, or null. */
    fun JsonObject.obj(name: String): JsonObject? = get(name)?.takeIf { it.isJsonObject }?.asJsonObject

    /** The objects in the array field [name]; anything else in it is skipped. */
    fun JsonObject.objects(name: String): List<JsonObject> =
        get(name)?.takeIf { it.isJsonArray }?.asJsonArray.orEmpty()
            .mapNotNull { element -> element.takeIf { it.isJsonObject }?.asJsonObject }

    /** The strings in the array field [name]; anything else in it is skipped. */
    fun JsonObject.strings(name: String): List<String> =
        get(name)?.takeIf { it.isJsonArray }?.asJsonArray.orEmpty()
            .mapNotNull { element -> element.takeIf { it.isJsonPrimitive }?.asString }

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

private fun JsonArray?.orEmpty(): List<JsonElement> = this?.toList().orEmpty()
