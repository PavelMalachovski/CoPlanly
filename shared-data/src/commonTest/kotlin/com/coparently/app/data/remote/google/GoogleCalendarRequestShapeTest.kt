package com.coparently.app.data.remote.google

import com.coparently.app.data.http.RecordingServer
import com.coparently.app.data.http.answer
import io.ktor.http.HttpHeaders
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Clock
import kotlin.time.Instant

/**
 * The one request the Calendar import makes, Calendar API v3 `events.list`, as google-api-client
 * 2.2.0 made it before MON-24: the path, every query parameter in the order and encoding it wrote
 * them (paging, `orderBy`, `singleEvents`, the window as RFC 3339 with the offset of the zone the
 * phone is in) and the bearer token.
 *
 * The expected addresses were produced by running `Calendar.events().list("primary")` with the
 * setters the app called (`setTimeMin`/`setTimeMax` with `DateTime(epochMillis)`,
 * `setMaxResults(250)`, `setOrderBy("startTime")`, `setSingleEvents(true)`, `setPageToken`) over
 * google-http-client's `MockHttpTransport` in each zone, and pasted here.
 */
class GoogleCalendarRequestShapeTest {

    private val credential = GoogleCredential("ya29.token")
    private val emptyPage = """{"kind":"calendar#events","items":[]}"""

    private fun api(server: RecordingServer, zone: String, now: String = "2026-11-01T08:30:15.123Z") =
        GoogleCalendarApi(server.client, { TimeZone.of(zone) }, FixedClock(Instant.parse(now)))

    @Test
    fun `events list is a GET with the bearer token and the window in the phone's offset`() = runTest {
        val server = RecordingServer { answer(emptyPage) }

        api(server, "Europe/Prague").listEvents(
            credential,
            timeMin = LocalDateTime(2026, 11, 1, 9, 30, 15, 123_000_000),
            timeMax = LocalDateTime(2027, 11, 1, 9, 30, 15, 123_000_000)
        )

        val request = server.requests.single()
        assertEquals("GET", request.method.value)
        assertEquals(
            "https://www.googleapis.com/calendar/v3/calendars/primary/events?maxResults=250&orderBy=startTime" +
                "&singleEvents=true&timeMax=2027-11-01T09:30:15.123%2B01:00&timeMin=2026-11-01T09:30:15.123%2B01:00",
            request.url.toString()
        )
        assertEquals("Bearer ya29.token", request.headers[HttpHeaders.Authorization])
    }

    @Test
    fun `in UTC the window ends in Z`() = runTest {
        val server = RecordingServer { answer(emptyPage) }

        api(server, "UTC").listEvents(credential, timeMin = LocalDateTime(2026, 11, 1, 9, 30, 15, 123_000_000))

        assertEquals(
            "https://www.googleapis.com/calendar/v3/calendars/primary/events?maxResults=250&orderBy=startTime" +
                "&singleEvents=true&timeMax=2027-11-01T09:30:15.123Z&timeMin=2026-11-01T09:30:15.123Z",
            server.requests.single().url.toString()
        )
    }

    @Test
    fun `with no window the import reads a year from now - each end in its own offset`() = runTest {
        // 09:30:15.123 in New York on 1 November 2026 is after that day's change to winter time;
        // a year later, summer time has not ended yet.
        val server = RecordingServer { answer(emptyPage) }

        val read = api(server, "America/New_York", now = "2026-11-01T14:30:15.123Z").listEvents(credential)

        assertEquals(
            "https://www.googleapis.com/calendar/v3/calendars/primary/events?maxResults=250&orderBy=startTime" +
                "&singleEvents=true&timeMax=2027-11-01T09:30:15.123-04:00&timeMin=2026-11-01T09:30:15.123-05:00",
            server.requests.single().url.toString()
        )
        assertEquals(LocalDateTime(2026, 11, 1, 9, 30, 15, 123_000_000), read.from)
        assertEquals(LocalDateTime(2027, 11, 1, 9, 30, 15, 123_000_000), read.until)
    }

    @Test
    fun `the next page is asked for with its token - encoded as google-http-client encoded it`() = runTest {
        var page = 0
        val server = RecordingServer {
            page++
            answer(if (page == 1) """{"items":[],"nextPageToken":"tok/+= x"}""" else emptyPage)
        }

        api(server, "Europe/Prague").listEvents(
            credential,
            timeMin = LocalDateTime(2026, 11, 1, 9, 30, 15, 123_000_000),
            timeMax = LocalDateTime(2027, 11, 1, 9, 30, 15, 123_000_000)
        )

        assertEquals(2, server.requests.size)
        assertEquals(
            "https://www.googleapis.com/calendar/v3/calendars/primary/events?maxResults=250&orderBy=startTime" +
                "&pageToken=tok/%2B%3D%20x&singleEvents=true" +
                "&timeMax=2027-11-01T09:30:15.123%2B01:00&timeMin=2026-11-01T09:30:15.123%2B01:00",
            server.requests[1].url.toString()
        )
        assertEquals("Bearer ya29.token", server.requests[1].headers[HttpHeaders.Authorization])
    }

    @Test
    fun `a calendar id is one path segment`() = runTest {
        val server = RecordingServer { answer(emptyPage) }

        api(server, "UTC").listEvents(
            credential,
            calendarId = "a@b.c/d +!~*()",
            timeMin = LocalDateTime(2026, 11, 1, 9, 30, 15, 123_000_000)
        )

        assertEquals(
            "https://www.googleapis.com/calendar/v3/calendars/a@b.c%2Fd%20+!~*()/events",
            server.requests.single().url.toString().substringBefore('?')
        )
    }

    /** A clock that always says [now]. */
    private class FixedClock(private val now: Instant) : Clock {
        override fun now(): Instant = now
    }
}
