package com.coparently.app.serialization

import com.google.gson.Gson
import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser

/**
 * Gson's text as Gson wrote it **on a phone**, from what it writes on this JVM (MON-24).
 *
 * Gson writes a class's fields in `Class.getDeclaredFields()` order. HotSpot returns declaration
 * order; ART returns dex order, which sorts a class's fields by name (see `GsonFieldOrder` in `:shared`). The
 * bytes on phones are ART's, so the JVM's output is not the truth: this re-sorts every object that
 * Gson wrote for a class — found by walking [value] beside the text, by reflection, independently
 * of `GsonFieldOrder`'s descriptors — and leaves a map's entries in their order. The instrumented
 * `GsonDeviceParityTest` compares with Gson on ART itself. The shared module's tests hold the same
 * helper; test sources are not shared between modules.
 */
internal object ArtFieldOrder {

    private val printer = Gson()

    /** [gsonText], which Gson wrote for [value] on the JVM, as Gson wrote it on ART. */
    fun of(gsonText: String, value: Any?): String = printer.toJson(reorder(JsonParser.parseString(gsonText), value))

    private fun reorder(element: JsonElement, value: Any?): JsonElement = when {
        value is Map<*, *> && element is JsonObject -> JsonObject().also { out ->
            element.entrySet().forEach { (key, entry) -> out.add(key, reorder(entry, value[key])) }
        }
        value is Collection<*> && element is JsonArray -> JsonArray().also { out ->
            element.zip(value).forEach { (entry, item) -> out.add(reorder(entry, item)) }
        }
        value != null && element is JsonObject -> JsonObject().also { out ->
            element.entrySet().sortedBy { it.key }.forEach { (key, entry) ->
                out.add(key, reorder(entry, field(value, key)))
            }
        }
        else -> element
    }

    private fun field(owner: Any, name: String): Any? =
        owner.javaClass.getDeclaredField(name).apply { isAccessible = true }.get(owner)
}
