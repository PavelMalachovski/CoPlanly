package com.coparently.app.wire

import com.google.gson.GsonBuilder
import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonNull
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.google.gson.JsonPrimitive

/**
 * A Firestore server timestamp as a fixture holds it: `{"$timestamp": <epoch millis>}`.
 *
 * The app never reads a `com.google.firebase.Timestamp` inside a mapper — the one caller that
 * sees one (`FirestoreEventVersionDataSource`) converts it to millis before
 * `EventVersionDocument.Parsed.from` — so a plain marker is enough, and keeps these tests off a
 * Firebase class the JVM would have to construct.
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
 */
internal object WireJson {

    private const val TIMESTAMP = "\$timestamp"

    private val gson = GsonBuilder()
        .setPrettyPrinting()
        .serializeNulls()
        .disableHtmlEscaping()
        .create()

    /** [text] parsed into the maps, lists and scalars a Firestore snapshot hands a mapper. */
    fun parse(text: String): Any? = fromJson(JsonParser.parseString(text))

    /** [value] as canonical fixture JSON, ending with a newline. */
    fun write(value: Any?): String = gson.toJson(toJson(value)) + "\n"

    private fun fromJson(element: JsonElement): Any? = when {
        element.isJsonNull -> null
        element.isJsonArray -> element.asJsonArray.map(::fromJson)
        element.isJsonObject -> objectFromJson(element.asJsonObject)
        else -> primitiveFromJson(element.asJsonPrimitive)
    }

    private fun objectFromJson(json: JsonObject): Any {
        val timestamp = json.get(TIMESTAMP)
        if (json.size() == 1 && timestamp != null && timestamp.isJsonPrimitive) {
            return WireTimestamp(timestamp.asLong)
        }
        return json.entrySet().associateTo(LinkedHashMap()) { (key, value) -> key to fromJson(value) }
    }

    private fun primitiveFromJson(primitive: JsonPrimitive): Any = when {
        primitive.isBoolean -> primitive.asBoolean
        primitive.isString -> primitive.asString
        else -> {
            val lexical = primitive.asString
            if (lexical.any { it == '.' || it == 'e' || it == 'E' }) lexical.toDouble() else lexical.toLong()
        }
    }

    private fun toJson(value: Any?): JsonElement = when (value) {
        null -> JsonNull.INSTANCE
        is WireTimestamp -> JsonObject().apply { addProperty(TIMESTAMP, value.millis) }
        is Map<*, *> -> JsonObject().apply {
            value.entries.sortedBy { it.key.toString() }.forEach { (key, entry) -> add(key.toString(), toJson(entry)) }
        }
        is Iterable<*> -> JsonArray().apply { value.forEach { add(toJson(it)) } }
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
}
