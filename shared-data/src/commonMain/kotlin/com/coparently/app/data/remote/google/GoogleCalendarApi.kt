package com.coparently.app.data.remote.google

import com.coparently.app.data.http.Answer
import com.coparently.app.data.http.Outgoing
import com.coparently.app.data.http.PercentEncoding
import com.coparently.app.data.http.Redirects
import com.coparently.app.data.http.send
import io.ktor.client.HttpClient
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.offsetAt
import kotlinx.datetime.plus
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime
import kotlin.math.absoluteValue
import kotlin.time.Clock

/**
 * The access token a Calendar call is made with: sent as `Authorization: Bearer`, and nothing
 * else. Its `toString` never prints the token.
 *
 * @property accessToken Google's OAuth access token.
 */
class GoogleCredential(val accessToken: String) {
    override fun toString(): String = "GoogleCredential(…)"
}

/**
 * Google Calendar, read and only read: one call, Calendar API v3 `events.list`, made on Ktor since
 * MON-24. It replaced google-api-client's `Calendar.events().list(…)`, which it calls exactly as
 * that did (`GoogleCalendarRequestShapeTest`), with the same timeouts (`HttpTimeouts.GOOGLE_CALENDAR`)
 * and the same redirects (`Redirects.GOOGLE_HTTP_CLIENT`).
 *
 * [listEvents] is the one call the app makes to Google Calendar, under the read-only scope the
 * app's `GoogleCalendarAccess.SCOPE` names. There is no create, update or delete here on purpose:
 * the app writes nothing to Google, and `GoogleCalendarAccessTest` fails if a public method is
 * added, so the scope, the privacy policy and the consent screen are looked at again first.
 */
class GoogleCalendarApi internal constructor(
    private val http: HttpClient,
    private val zone: () -> TimeZone,
    private val clock: Clock
) {

    /** The production API: the device's time zone and the wall clock. */
    constructor(http: HttpClient) : this(http, { TimeZone.currentSystemDefault() }, Clock.System)

    /**
     * Lists events from Google Calendar, following every page.
     *
     * The previous version asked for one page of 50 and returned it, while its only caller passed
     * no bounds at all — so an import of a real calendar stopped at the 50th event and reported
     * "Found 50 events", which is what a complete import also looks like. Nothing said otherwise.
     *
     * Two bounds now exist and both are reported rather than assumed: the window
     * ([CalendarEvents.from]/[CalendarEvents.until], defaulting to the next [HORIZON_MONTHS]
     * months) and the event cap ([IMPORT_LIMIT], surfaced as [CalendarEvents.truncated]). The
     * window's ends are wall-clock times in the device's zone, as they were.
     *
     * @param limit Most events to return. Reaching it sets [CalendarEvents.truncated].
     * @throws GoogleCalendarHttpException when Google answers with an error status.
     * @throws kotlinx.io.IOException when Google cannot be reached, or answers with something that is
     *   not JSON (`GoogleCalendarJson`).
     * @throws IllegalArgumentException when a page cannot be read as a page of events, as
     *   google-api-client's parser threw.
     */
    suspend fun listEvents(
        credential: GoogleCredential,
        calendarId: String = "primary",
        timeMin: LocalDateTime? = null,
        timeMax: LocalDateTime? = null,
        limit: Int = IMPORT_LIMIT
    ): CalendarEvents {
        val zone = zone()
        val from = timeMin ?: clock.now().toLocalDateTime(zone)
        val until = timeMax ?: LocalDateTime(from.date.plus(HORIZON_MONTHS, DateTimeUnit.MONTH), from.time)
        val window = "&timeMax=" + PercentEncoding.googleQuery(rfc3339(until, zone)) +
            "&timeMin=" + PercentEncoding.googleQuery(rfc3339(from, zone))
        val pagePath = "$EVENTS_LIST/calendars/" + PercentEncoding.googlePath(calendarId) + "/events"

        val paged = collectPages(limit, MAX_PAGES) { pageToken ->
            // The parameters in the order google-api-client wrote them: alphabetical.
            val query = "maxResults=$PAGE_SIZE&orderBy=startTime" +
                pageToken?.let { "&pageToken=" + PercentEncoding.googleQuery(it) }.orEmpty() +
                "&singleEvents=true" + window
            val authorization = HttpHeaders.Authorization to "Bearer ${credential.accessToken}"
            val outgoing = Outgoing(HttpMethod.Get, "$pagePath?$query", listOf(authorization))
            page(http.send(outgoing, Redirects.GOOGLE_HTTP_CLIENT))
        }

        return CalendarEvents(
            events = paged.items,
            truncated = paged.truncated,
            from = from,
            until = until
        )
    }

    private fun page(answer: Answer): PageOf<GoogleCalendarEvent> {
        if (answer.status !in HTTP_SUCCESS) {
            throw GoogleCalendarHttpException.of(
                statusCode = answer.status,
                body = answer.body,
                authenticate = answer.headers[HttpHeaders.WWWAuthenticate]
            )
        }
        return GoogleCalendarJson.page(answer.body)
    }

    /**
     * [local] in [zone] as google-http-client's `DateTime(epochMillis).toStringRfc3339()` wrote it:
     * the instant's wall clock in [zone] to the millisecond, then `Z` or the offset in minutes.
     * A wall-clock time in a daylight-saving gap is moved later by the gap, as `atZone` moved it.
     */
    private fun rfc3339(local: LocalDateTime, zone: TimeZone): String {
        val instant = local.toInstant(zone)
        val shown = instant.toLocalDateTime(zone)
        val offsetMinutes = zone.offsetAt(instant).totalSeconds / SECONDS_PER_MINUTE
        val offset = if (offsetMinutes == 0) {
            "Z"
        } else {
            (if (offsetMinutes > 0) "+" else "-") +
                pad(offsetMinutes.absoluteValue / MINUTES_PER_HOUR, 2) + ":" +
                pad(offsetMinutes.absoluteValue % MINUTES_PER_HOUR, 2)
        }
        return pad(shown.year, YEAR_DIGITS) + "-" + pad(shown.month.ordinal + 1, 2) + "-" + pad(shown.day, 2) +
            "T" + pad(shown.hour, 2) + ":" + pad(shown.minute, 2) + ":" + pad(shown.second, 2) +
            "." + pad(shown.nanosecond / NANOS_PER_MILLI, MILLI_DIGITS) + offset
    }

    private fun pad(value: Int, digits: Int): String = value.toString().padStart(digits, '0')

    companion object {
        /** Calendar API v3, as google-api-services-calendar's root URL and service path name it. */
        private const val EVENTS_LIST = "https://www.googleapis.com/calendar/v3"

        /** Google's own per-page ceiling is 2500; 250 keeps any single request small. */
        private const val PAGE_SIZE = 250

        /**
         * How many events one import may take. Reached only by a very full calendar, and when it
         * is reached the caller is told — the point of [CalendarEvents.truncated].
         */
        const val IMPORT_LIMIT = 2_000

        /**
         * How far ahead an import reaches when the caller names no end.
         *
         * A horizon is not a nicety here. `singleEvents=true` expands recurrences into
         * instances, so "everything from now on" is not a finite request at all — a single
         * open-ended weekly event generates instances forever, and without an end date the
         * event cap alone would silently decide where the calendar stopped. One year is a
         * co-parenting horizon: school years, holidays and custody rotations all fit inside it.
         */
        private const val HORIZON_MONTHS = 12

        /** Refuses to page forever if a server ever returns a token that does not advance. */
        private const val MAX_PAGES = 64

        private val HTTP_SUCCESS = 200..299
        private const val SECONDS_PER_MINUTE = 60
        private const val MINUTES_PER_HOUR = 60
        private const val NANOS_PER_MILLI = 1_000_000
        private const val YEAR_DIGITS = 4
        private const val MILLI_DIGITS = 3
    }
}
