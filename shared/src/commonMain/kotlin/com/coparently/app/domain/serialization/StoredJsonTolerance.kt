package com.coparently.app.domain.serialization

import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.StructureKind
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * Makes a stored tree readable by kotlinx wherever Gson read it (MON-24).
 *
 * Gson builds a model without running its constructor when the class has no no-argument one, so a
 * field missing from the text — or `null` there — is left at the JVM's default: 0, false, or null
 * even in a property Kotlin declares non-null. kotlinx refuses both. A stored row that an older
 * build or a damaged write left that way must still read, so before decoding, a required
 * non-nullable property that is missing or null is given the JVM's default where it has one (0,
 * false) and, where the JVM's was a null nobody could use, the empty value of its type (an empty
 * text, list or map, or an object filled the same way). A null element in a list or map whose type
 * is not nullable is dropped for the same reason. A property with a Kotlin default is left to
 * kotlinx, which applies the default (`coerceInputValues` for an explicit null too).
 *
 * Nothing else changes: a value that is present is left as it is, and a tree of the wrong shape
 * still fails to decode, as it did under Gson.
 */
internal object StoredJsonTolerance {

    /** [element], with what [descriptor] requires filled in. */
    fun fill(element: JsonElement, descriptor: SerialDescriptor): JsonElement = when {
        element === JsonNull -> element
        descriptor.kind == StructureKind.CLASS && element is JsonObject -> fillObject(element, descriptor)
        descriptor.kind == StructureKind.LIST && element is JsonArray -> {
            val item = descriptor.getElementDescriptor(0)
            JsonArray(element.filter { it !== JsonNull || item.isNullable }.map { fill(it, item) })
        }
        descriptor.kind == StructureKind.MAP && element is JsonObject -> {
            val item = descriptor.getElementDescriptor(1)
            JsonObject(
                element.filterValues { it !== JsonNull || item.isNullable }
                    .mapValuesTo(LinkedHashMap()) { (_, value) -> fill(value, item) }
            )
        }
        else -> element
    }

    private fun fillObject(json: JsonObject, descriptor: SerialDescriptor): JsonObject {
        val out = LinkedHashMap(json)
        for (index in 0 until descriptor.elementsCount) {
            val name = descriptor.getElementName(index)
            val property = descriptor.getElementDescriptor(index)
            val value = json[name]
            if (value != null && value !== JsonNull) {
                out[name] = fill(value, property)
            } else if (!descriptor.isElementOptional(index) && !property.isNullable) {
                emptyOf(property)?.let { out[name] = it }
            }
        }
        return JsonObject(out)
    }

    /** The value a missing required property reads as; null where there is none (an enum). */
    private fun emptyOf(descriptor: SerialDescriptor): JsonElement? = when (descriptor.kind) {
        PrimitiveKind.STRING -> JsonPrimitive("")
        PrimitiveKind.BOOLEAN -> JsonPrimitive(false)
        PrimitiveKind.BYTE, PrimitiveKind.SHORT, PrimitiveKind.INT, PrimitiveKind.LONG -> JsonPrimitive(0)
        PrimitiveKind.FLOAT, PrimitiveKind.DOUBLE -> JsonPrimitive(0.0)
        StructureKind.LIST -> JsonArray(emptyList())
        StructureKind.MAP -> JsonObject(emptyMap())
        StructureKind.CLASS -> fillObject(JsonObject(emptyMap()), descriptor)
        else -> null
    }
}
