package com.coparently.app.domain.serialization

import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.StructureKind
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

/**
 * Puts a class's properties in the order Gson wrote them on an Android phone (MON-24).
 *
 * Gson writes a class's fields in the order `Class.getDeclaredFields()` returns them
 * (`ReflectiveTypeAdapterFactory.getBoundFields`, which also puts a subclass's fields before its
 * superclass's). On ART that is not declaration order: `mirror::Class::GetDeclaredFields` walks the
 * class's `ArtField` array, which `ClassLinker::LoadClass` fills in the order of the dex file's
 * `class_data_item`, and the dex format requires that order to be ascending `field_idx` — field ids
 * sorted by class, then **name**, then type. ART relies on it: `FindFieldByNameAndType` binary
 * searches the array by name. Field layout sorts a separate index array and leaves this one alone.
 * So on every phone Gson wrote a class's keys sorted by field name, comparing UTF-16 code units
 * (dex string order), while the JVM's `getDeclaredFields` — and kotlinx — use declaration order.
 *
 * This sorts the members of every object that encodes a class, by key, and leaves a map's entries
 * in their iteration order, as Gson left them. The key is the property's JSON name, which is its
 * field name as long as no `@SerialName` renames it (none does; a rename would have to sort by the
 * field's name). It sorts the whole object rather than per class in a hierarchy: no stored model
 * extends another. `GsonDeviceParityTest` holds this to Gson on the emulators' ART.
 */
internal object GsonFieldOrder {

    /** [element], encoded from [descriptor]'s type, with every class's members sorted by name. */
    fun apply(element: JsonElement, descriptor: SerialDescriptor): JsonElement = when {
        descriptor.kind == StructureKind.CLASS && element is JsonObject -> JsonObject(
            element.entries
                .sortedBy { it.key }
                .associateTo(LinkedHashMap()) { (name, value) -> name to member(value, descriptor, name) }
        )
        descriptor.kind == StructureKind.LIST && element is JsonArray ->
            JsonArray(element.map { apply(it, descriptor.getElementDescriptor(0)) })
        descriptor.kind == StructureKind.MAP && element is JsonObject -> JsonObject(
            element.mapValuesTo(LinkedHashMap()) { (_, value) -> apply(value, descriptor.getElementDescriptor(1)) }
        )
        else -> element
    }

    private fun member(value: JsonElement, owner: SerialDescriptor, name: String): JsonElement {
        val index = owner.getElementIndex(name)
        return if (index < 0) value else apply(value, owner.getElementDescriptor(index))
    }
}
