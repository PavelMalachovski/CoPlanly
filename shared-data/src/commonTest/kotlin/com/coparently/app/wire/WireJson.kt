package com.coparently.app.wire

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.longOrNull

/**
 * A Firestore server timestamp as a fixture holds it: `{"$timestamp": <epoch millis>}`.
 *
 * The app never reads a timestamp inside a mapper — the data sources turn one into millis before
 * `EventVersionDocument.Parsed.from` and `RequestDeadlineDocument.parse` see the document — so a
 * plain marker is enough.
 *
 * @property millis The instant, as epoch millis.
 */
internal data class WireTimestamp(val millis: Long)

/**
 * The JSON form of a Firestore document in the wire fixtures, both ways.
 *
 * Firestore has one integer type and one floating type, and the app narrows through `Number`
 * when it reads, but a mapper may still cast (`as Long`, `as Double`), so the type must survive
 * the file: a JSON number written without a fraction or exponent reads back as a [Long], any
 * other as a [Double], and [write] prints a [Double] with its `.0`. Maps are printed with sorted
 * keys and two-space indentation, so a regenerated fixture's diff is only what changed.
 *
 * The printed text is the layout the fixtures were first written in (Gson's pretty printer, with
 * nulls and without HTML escaping, until MON-24 phase 2 made this common code), so regenerating
 * an unchanged fixture changes no byte of it.
 */
internal object WireJson {

    private const val TIMESTAMP = "\$timestamp"
    private const val INDENT = "  "

    /** [text] parsed into the maps, lists and scalars a Firestore snapshot hands a mapper. */
    fun parse(text: String): Any? = fromJson(Json.parseToJsonElement(text))

    /** [value] as canonical fixture JSON, ending with a newline. */
    fun write(value: Any?): String = StringBuilder().apply { print(toJson(value), "") }.append('\n').toString()

    private fun fromJson(element: JsonElement): Any? = when (element) {
        JsonNull -> null
        is JsonArray -> element.map(::fromJson)
        is JsonObject -> objectFromJson(element)
        is JsonPrimitive -> primitiveFromJson(element)
    }

    private fun objectFromJson(json: JsonObject): Any {
        val timestamp = json[TIMESTAMP]
        if (json.size == 1 && timestamp is JsonPrimitive && !timestamp.isString) {
            return WireTimestamp(checkNotNull(timestamp.longOrNull) { "$TIMESTAMP is not a whole number" })
        }
        return json.entries.associateTo(LinkedHashMap()) { (key, value) -> key to fromJson(value) }
    }

    private fun primitiveFromJson(primitive: JsonPrimitive): Any = when {
        primitive.isString -> primitive.content
        primitive.content == "true" -> true
        primitive.content == "false" -> false
        else -> {
            val lexical = primitive.content
            if (lexical.any { it == '.' || it == 'e' || it == 'E' }) lexical.toDouble() else lexical.toLong()
        }
    }

    private fun toJson(value: Any?): JsonElement = when (value) {
        null -> JsonNull
        is WireTimestamp -> JsonObject(mapOf(TIMESTAMP to JsonPrimitive(value.millis)))
        is Map<*, *> -> JsonObject(
            value.entries.sortedBy { it.key.toString() }
                .associateTo(LinkedHashMap()) { (key, entry) -> key.toString() to toJson(entry) }
        )
        is Iterable<*> -> JsonArray(value.map(::toJson))
        is Array<*> -> toJson(value.toList())
        else -> scalarToJson(value)
    }

    private fun scalarToJson(value: Any): JsonElement = when (value) {
        is Boolean -> JsonPrimitive(value)
        is Double -> JsonPrimitive(value)
        is Float -> JsonPrimitive(value.toDouble())
        is Number -> JsonPrimitive(value.toLong())
        is Enum<*> -> JsonPrimitive(value.name)
        else -> JsonPrimitive(value.toString())
    }

    /** Gson's pretty layout: two-space indents, `"key": value`, `[]` and `{}` when empty. */
    private fun StringBuilder.print(element: JsonElement, indent: String) {
        when (element) {
            JsonNull -> append("null")
            is JsonPrimitive -> if (element.isString) string(element.content) else append(element.content)
            is JsonArray -> if (element.isEmpty()) {
                append("[]")
            } else {
                append("[\n")
                element.forEachIndexed { index, item ->
                    append(indent).append(INDENT)
                    print(item, indent + INDENT)
                    append(if (index < element.size - 1) ",\n" else "\n")
                }
                append(indent).append(']')
            }
            is JsonObject -> if (element.isEmpty()) {
                append("{}")
            } else {
                append("{\n")
                element.entries.forEachIndexed { index, (key, item) ->
                    append(indent).append(INDENT)
                    string(key)
                    append(": ")
                    print(item, indent + INDENT)
                    append(if (index < element.size - 1) ",\n" else "\n")
                }
                append(indent).append('}')
            }
        }
    }

    /** A JSON string as Gson writes one with HTML escaping off. */
    private fun StringBuilder.string(text: String) {
        append('"')
        text.forEach { c ->
            when {
                c == '"' -> append("\\\"")
                c == '\\' -> append("\\\\")
                c == '\t' -> append("\\t")
                c == '\b' -> append("\\b")
                c == '\n' -> append("\\n")
                c == '\r' -> append("\\r")
                c == '\u000C' -> append("\\f")
                c < ' ' || c == ' ' || c == ' ' -> append("\\u").append(c.code.toString(HEX).padStart(4, '0'))
                else -> append(c)
            }
        }
        append('"')
    }

    private const val HEX = 16
}
