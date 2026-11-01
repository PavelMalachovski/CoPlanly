package com.coparently.app.data.remote.google

import com.coparently.app.domain.serialization.StoredJson
import kotlinx.datetime.LocalDate
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlin.time.Instant

/**
 * One event of a Google calendar, as much of Calendar API v3's `Event` as the import reads.
 *
 * @property id Google's id for the event (for an instance of a recurring event, the instance's).
 * @property summary Its title, or null.
 * @property description Its notes, or null.
 * @property start When it starts.
 * @property end When it ends; for an all-day event Google's date is the day **after** the last.
 * @property recurrence The `RRULE`/`EXDATE` lines of a recurring event, or null when the key is
 *   absent — which, with `singleEvents=true`, is every instance.
 */
data class GoogleCalendarEvent(
    val id: String?,
    val summary: String?,
    val description: String?,
    val start: GoogleEventTime?,
    val end: GoogleEventTime?,
    val recurrence: List<String>?
)

/**
 * Calendar API v3's `EventDateTime`: an instant for a timed event, a date for an all-day one.
 *
 * @property dateTime The instant, from RFC 3339 text with its offset; null for an all-day event.
 * @property date The calendar date of an all-day event; null for a timed one.
 */
data class GoogleEventTime(
    val dateTime: Instant?,
    val date: LocalDate?
)

/**
 * Google refused a Calendar call: the status and what Google said about it, which decides whether
 * the connection is renewed, ended or left alone (`CredentialProviderImpl`).
 *
 * It replaced google-api-client's `GoogleJsonResponseException`. It is not an `IOException`,
 * so a caller that also catches network failures cannot catch a refusal as one.
 *
 * @property statusCode The HTTP status.
 * @property reasons The `error.errors[].reason` values of Google's error body (such as
 *   `insufficientPermissions` or `rateLimitExceeded`), in order; empty when it had none.
 * @property authenticate The `WWW-Authenticate` header (RFC 6750 §3.1), or null.
 */
class GoogleCalendarHttpException(
    val statusCode: Int,
    val reasons: List<String>,
    val authenticate: String?
) : Exception("Google Calendar answered HTTP $statusCode") {

    internal companion object {
        /** The refusal Google's [body] describes, read leniently: no reasons when it is not its JSON. */
        fun of(statusCode: Int, body: String, authenticate: String?): GoogleCalendarHttpException {
            val errors = (GoogleCalendarJson.objectOrNull(body)?.get("error") as? JsonObject)?.get("errors")
            val reasons = (errors as? JsonArray).orEmpty().mapNotNull { (it as? JsonObject)?.text("reason") }
            return GoogleCalendarHttpException(statusCode, reasons, authenticate)
        }
    }
}

/**
 * Reads a page of `events.list` by hand over the JSON tree, as the Bakaláři import reads its
 * school (`BakalariJson`): no reflected model, so R8 has nothing to rename, and a missing,
 * null or odd field costs that field. google-api-client read the same page into its `@Key`
 * models through Gson, which needed keep rules; they went with it.
 */
internal object GoogleCalendarJson {

    /** The events and the next page's token in [body]; a body that is not an object reads as empty. */
    fun page(body: String): PageOf<GoogleCalendarEvent> {
        val root = objectOrNull(body) ?: return PageOf(emptyList(), null)
        val items = (root["items"] as? JsonArray).orEmpty().mapNotNull { (it as? JsonObject)?.let(::event) }
        return PageOf(items, root.text("nextPageToken"))
    }

    fun objectOrNull(body: String): JsonObject? = try {
        StoredJson.parse(body) as? JsonObject
    } catch (e: SerializationException) {
        null
    }

    private fun event(item: JsonObject): GoogleCalendarEvent = GoogleCalendarEvent(
        id = item.text("id"),
        summary = item.text("summary"),
        description = item.text("description"),
        start = (item["start"] as? JsonObject)?.let(::time),
        end = (item["end"] as? JsonObject)?.let(::time),
        recurrence = (item["recurrence"] as? JsonArray)?.mapNotNull { it.textOrNull() }
    )

    private fun time(value: JsonObject): GoogleEventTime = GoogleEventTime(
        dateTime = value.text("dateTime")?.let { runCatching { Instant.parse(it) }.getOrNull() },
        date = value.text("date")?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
    )
}

private fun JsonObject.text(name: String): String? = get(name)?.textOrNull()

private fun JsonElement.textOrNull(): String? = (this as? JsonPrimitive)?.takeIf { it !== JsonNull }?.content

private fun JsonArray?.orEmpty(): List<JsonElement> = this ?: emptyList()
