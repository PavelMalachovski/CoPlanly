package com.coparently.app.domain.serialization

import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * The untyped half of [StoredJson] (MON-24): a value as a Firestore document holds one — maps,
 * lists, texts, numbers, booleans — to a tree and back, the way Gson handled `Map`, `List` and
 * `Any` by their runtime type.
 */
internal object StoredJsonValues {

    /** [value] as a tree, by its runtime type, as Gson's `ObjectTypeAdapter` wrote it. */
    fun elementOf(value: Any?): JsonElement = when (value) {
        null -> JsonNull
        is JsonElement -> value
        is String -> JsonPrimitive(value)
        is Boolean -> JsonPrimitive(value)
        is Char -> JsonPrimitive(value.toString())
        is Number -> JsonPrimitive(finite(value))
        is Enum<*> -> JsonPrimitive(value.name)
        is Map<*, *> -> JsonObject(
            LinkedHashMap<String, JsonElement>().apply {
                value.forEach { (key, entry) -> put(key.toString(), elementOf(entry)) }
            }
        )
        is Collection<*> -> JsonArray(value.map(::elementOf))
        is Array<*> -> JsonArray(value.map(::elementOf))
        // A SerializationException is an IllegalArgumentException.
        else -> throw SerializationException(
            "${value::class.simpleName} is not a JSON value; give it a serializer and call encode"
        )
    }

    /** [value], unless it is a NaN or an infinity, which Gson refused to write too. */
    private fun finite(value: Number): Number {
        val isFinite = when (value) {
            is Double -> value.isFinite()
            is Float -> value.isFinite()
            else -> true
        }
        require(isFinite) { "$value is not a valid double value as per JSON specification" }
        return value
    }

    /**
     * [element] as maps (in their order), lists, texts, booleans and numbers per [numbers], as
     * Gson's `ObjectTypeAdapter` read it.
     */
    fun valueOf(element: JsonElement, numbers: StoredJson.Numbers): Any? = when (element) {
        JsonNull -> null
        is JsonObject -> LinkedHashMap<String, Any?>().apply {
            element.forEach { (key, entry) -> put(key, valueOf(entry, numbers)) }
        }
        is JsonArray -> element.mapTo(ArrayList()) { valueOf(it, numbers) }
        is JsonPrimitive -> primitiveOf(element, numbers)
    }

    private fun primitiveOf(primitive: JsonPrimitive, numbers: StoredJson.Numbers): Any {
        val content = primitive.content
        return when {
            primitive.isString -> content
            content == "true" -> true
            content == "false" -> false
            // A lenient reader hands an unquoted word over as a literal; Gson read it as a string.
            else -> numberOf(content, numbers) ?: content
        }
    }

    private fun numberOf(content: String, numbers: StoredJson.Numbers): Number? = when (numbers) {
        StoredJson.Numbers.DOUBLE -> content.toDoubleOrNull()
        StoredJson.Numbers.LONG_OR_DOUBLE ->
            if ('.' in content) content.toDoubleOrNull() else content.toLongOrNull() ?: content.toDoubleOrNull()
    }
}
