package com.coparently.app.data.remote.google

import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlinx.io.IOException
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlin.time.Instant

/**
 * Reads a page of `events.list` by hand over the JSON tree, as the Bakaláři import reads its
 * school (`BakalariJson`): no reflected model, so R8 has nothing to rename. google-api-client read
 * the same page into its `@Key` models through Gson, which needed keep rules; they went with it.
 *
 * **A page it could not read failed the import, and still does** (MON-24 fix round): a body that
 * is not JSON (a captive portal's page) is an `IOException`, as Gson's `MalformedJsonException`
 * was, so the import says the network failed; an empty body, a document cut short, a page that is
 * not an object and a field of the wrong type are an [IllegalArgumentException], as google's parser
 * threw (an empty or cut-short body ended as its `IllegalArgumentException` or a
 * `NullPointerException`), so the import fails as unknown. Only `null` and `{}` are an empty page.
 * The fields read are checked as the `Event` model's types checked them: a text is a JSON string,
 * `start` and `end` are objects, `recurrence` a list of strings, and both times parse
 * (`GoogleDateTime`). An explicit `null` reads as google's parser read it (an empty text, an empty
 * list, a time at the epoch, an item with no field set); the Calendar API leaves an unset field
 * out instead. Fields the import does not read are not checked, where the model checked
 * every field it declared. Content after the page's closing brace is ignored, as Gson read one
 * value.
 */
internal object GoogleCalendarJson {

    /** Strict JSON, as Gson's reader was by default: quoted names, no trailing commas. */
    private val strict = Json

    /** An item with no field set: what google's parser made of a `null` item. */
    private val UNSET = JsonObject(emptyMap())

    /** The events and the next page's token in [body]. */
    fun page(body: String): PageOf<GoogleCalendarEvent> {
        val root = document(body) ?: return PageOf(emptyList(), null)
        val items = when (val value = root["items"]) {
            null, JsonNull -> emptyList()
            // google's parser read a `null` item as an event with every field unset.
            is JsonArray -> value.map { item ->
                event(if (item === JsonNull) UNSET else item as? JsonObject ?: wrongType("items"))
            }
            else -> wrongType("items")
        }
        return PageOf(items, root.text("nextPageToken"))
    }

    /** [body]'s top-level object, null for `null`; see the object's KDoc for what fails how. */
    private fun document(body: String): JsonObject? {
        val start = body.indexOfFirst { !it.isWhitespace() }
        require(start >= 0) { "no JSON input found" }
        return when (body[start]) {
            '{' -> parseObject(body, start)
            '[', '"', '-', in '0'..'9' -> throw IllegalArgumentException("A page of events is a JSON object")
            else -> literal(body.substring(start))
        }
    }

    /** The object starting at [start], up to its closing brace; content after it is ignored. */
    private fun parseObject(body: String, start: Int): JsonObject {
        val end = objectEnd(body, start)
        val text = body.substring(start, end)
        return try {
            strict.parseToJsonElement(text) as JsonObject
        } catch (e: SerializationException) {
            throw IOException("Google Calendar answered with malformed JSON", e)
        }
    }

    private fun event(item: JsonObject): GoogleCalendarEvent = GoogleCalendarEvent(
        id = item.text("id"),
        summary = item.text("summary"),
        description = item.text("description"),
        start = item.timeOrNull("start"),
        end = item.timeOrNull("end"),
        recurrence = when (val value = item["recurrence"]) {
            null -> null
            JsonNull -> emptyList()
            // google's parser read a `null` line as its empty "null string".
            is JsonArray -> value.map { element -> element.textOrNull("recurrence").orEmpty() }
            else -> wrongType("recurrence")
        }
    )

    private fun JsonObject.timeOrNull(name: String): GoogleEventTime? = when (val value = get(name)) {
        null, JsonNull -> null
        is JsonObject -> GoogleEventTime(
            dateTime = value.instant("dateTime"),
            date = value.instant("date")?.toLocalDateTime(TimeZone.UTC)?.date
        )
        else -> wrongType(name)
    }

    /** The time in the field [name]: null when absent, the epoch when `null` (google's null `DateTime`). */
    private fun JsonObject.instant(name: String): Instant? = text(name)?.let { text ->
        Instant.fromEpochMilliseconds(if (get(name) === JsonNull) 0 else GoogleDateTime.epochMillis(text))
    }

    /** The field [name] as text: null when absent, the wrong type when not a string or `null`. */
    private fun JsonObject.text(name: String): String? = get(name)?.textOrNull(name)

    /** A string as it is, and `null` as google's parser read it: its empty "null string". */
    private fun JsonElement.textOrNull(name: String): String? = when {
        this === JsonNull -> ""
        this is JsonPrimitive && isString -> content
        else -> wrongType(name)
    }

    private fun wrongType(name: String): Nothing = throw IllegalArgumentException("key $name")
}

/** `null` is an empty page and `true`/`false` the wrong type; anything else is not JSON. */
private fun literal(text: String): JsonObject? = when {
    text.startsWith("null") -> null
    text.startsWith("true") || text.startsWith("false") ->
        throw IllegalArgumentException("A page of events is a JSON object")
    else -> throw IOException("Google Calendar answered with something that is not JSON")
}

/**
 * The index after the brace that closes the object at [start]. A body that ends inside a string
 * is malformed ([IOException]); one that ends between tokens was cut short
 * ([IllegalArgumentException]).
 */
private fun objectEnd(body: String, start: Int): Int {
    var depth = 0
    var inString = false
    var i = start
    while (i < body.length) {
        val c = body[i]
        when {
            inString && c == '\\' -> i++
            inString && c == '"' -> inString = false
            inString -> Unit
            c == '"' -> inString = true
            c == '{' || c == '[' -> depth++
            c == '}' || c == ']' -> if (--depth == 0) return i + 1
        }
        i++
    }
    if (inString) throw IOException("Google Calendar's page ends inside a string")
    throw IllegalArgumentException("Google Calendar's page was cut short")
}
