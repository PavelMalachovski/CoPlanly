package com.coparently.app.data.remote.google

import com.coparently.app.data.http.HttpTimeouts
import com.coparently.app.data.http.RecordingServer
import com.coparently.app.data.http.answer
import io.ktor.http.HttpHeaders
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.io.IOException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Clock

/**
 * What the Calendar import makes of Google's answers (MON-24): the events of each page as
 * google-api-client's models read them, every page followed, and a refusal carrying what decides
 * the connection's fate.
 */
class GoogleCalendarApiTest {

    private val credential = GoogleCredential("ya29.token")
    private val from = LocalDateTime(2026, 11, 1, 0, 0)

    private fun api(server: RecordingServer) = GoogleCalendarApi(server.client, { TimeZone.UTC }, Clock.System)

    private fun serving(body: String) = RecordingServer(HttpTimeouts.GOOGLE_CALENDAR) { answer(body) }

    private suspend fun eventsIn(body: String) = api(serving(body)).listEvents(credential, timeMin = from).events

    @Test
    fun `a page's events are read as the Calendar models read them`() = runTest {
        val server = RecordingServer {
            answer(
                """{"kind":"calendar#events","items":[""" +
                    """{"id":"e1","summary":"Swimming","description":"Bring a towel",""" +
                    """"start":{"dateTime":"2026-05-04T16:00:00+02:00"},"end":{"date":"2026-05-05"},""" +
                    """"recurrence":["RRULE:FREQ=WEEKLY"]},""" +
                    """{"id":"e2","start":{"dateTime":"2026-05-04T14:00:00.000Z"}}]}"""
            )
        }

        val read = api(server).listEvents(credential, timeMin = from)

        val (swimming, bare) = read.events
        assertEquals("e1", swimming.id)
        assertEquals("Swimming", swimming.summary)
        assertEquals("Bring a towel", swimming.description)
        // google-http-client's `DateTime.getValue()` for the same text.
        assertEquals(1_777_903_200_000L, swimming.start?.dateTime?.toEpochMilliseconds())
        assertNull(swimming.start?.date)
        assertEquals(LocalDate(2026, 5, 5), swimming.end?.date)
        assertNull(swimming.end?.dateTime)
        assertEquals(listOf("RRULE:FREQ=WEEKLY"), swimming.recurrence)
        assertEquals(1_777_903_200_000L, bare.start?.dateTime?.toEpochMilliseconds())
        assertNull(bare.summary)
        assertNull(bare.end)
        assertNull(bare.recurrence)
        assertFalse(read.truncated)
    }

    @Test
    fun `every page is followed - and the cap is reported`() = runTest {
        var page = 0
        val server = RecordingServer {
            page++
            answer("""{"items":[{"id":"p$page"}],"nextPageToken":"t$page"}""")
        }

        val read = api(server).listEvents(credential, timeMin = from, limit = 3)

        assertEquals(listOf("p1", "p2", "p3"), read.events.map { it.id })
        assertTrue(read.truncated)
    }

    @Test
    fun `a page with no items is an empty page - and so is null`() = runTest {
        assertTrue(eventsIn("""{"kind":"calendar#events"}""").isEmpty())
        assertTrue(eventsIn("null").isEmpty())
    }

    // What google-api-client threw for a page it could not read, so the import fails as it failed
    // (`CalendarSyncRepository`: an IOException is "network", anything else "unknown"). Each was
    // printed by running google-api-client 2.2.0 over the same body.

    @Test
    fun `a page that is not JSON fails as the network - as Gson's MalformedJsonException did`() = runTest {
        for (body in listOf("<html><body>Sign in to the Wi-Fi</body></html>", "{'items':[]}", "{\"items\":[],}")) {
            assertFailsWith<IOException>("for [$body]") { eventsIn(body) }
        }
    }

    @Test
    fun `an empty or cut-short page or one of the wrong shape fails the import`() = runTest {
        val bodies = listOf(
            "",
            "[]",
            """{"items":[{"id":"e1"""",
            """{"items":{"id":"e1"}}""",
            """{"items":[{"id":5}]}""",
            """{"items":[{"id":"e1","start":"x"}]}""",
            """{"items":[{"id":"e1","recurrence":"RRULE"}]}""",
            """{"items":[],"nextPageToken":5}"""
        )
        for (body in bodies) {
            assertFailsWith<IllegalArgumentException>("for [$body]") { eventsIn(body) }
        }
    }

    @Test
    fun `a time that does not parse fails the whole page - not just its event`() = runTest {
        val times = listOf(
            """{"dateTime":"garbage"}""",
            """{"dateTime":"2026-05-04T16:00:00+0200"}""",
            """{"date":"2026-5-4"}"""
        )
        for (time in times) {
            val body = """{"items":[{"id":"fine","summary":"Fine"},{"id":"e1","start":$time}]}"""
            assertFailsWith<IllegalArgumentException>("for $time") { eventsIn(body) }
        }
    }

    @Test
    fun `explicit nulls read as google-api-client read them`() = runTest {
        val body = """{"items":[null,""" +
            """{"id":null,"summary":null,"start":null,"end":{"dateTime":null},"recurrence":null}]}"""

        val (unset, nulls) = eventsIn(body)

        assertEquals(GoogleCalendarEvent(null, null, null, null, null, null), unset)
        assertEquals("", nulls.id)
        assertEquals("", nulls.summary)
        assertNull(nulls.start?.dateTime)
        assertEquals(0L, nulls.end?.dateTime?.toEpochMilliseconds())
        assertEquals(emptyList(), nulls.recurrence)
    }

    @Test
    fun `a date read from a date-time is its UTC date - as google-api-client gave it`() = runTest {
        val body = """{"items":[{"id":"e1","start":{"date":"2026-05-04T23:30:00-05:00"},""" +
            """"end":{"dateTime":"2026-05-04"}}]}"""

        val event = eventsIn(body).single()

        assertEquals(LocalDate(2026, 5, 5), event.start?.date)
        assertEquals(1_777_852_800_000L, event.end?.dateTime?.toEpochMilliseconds())
    }

    // Redirects as google-http-client 1.42.3's `handleRedirect` followed them (run with
    // MockWebServer and NetHttpTransport): 301 to 308, the bearer token sent on, 10 at most.

    @Test
    fun `a redirect is followed with the bearer token - even to another host`() = runTest {
        for (status in listOf(301, 302, 303, 307, 308)) {
            val server = RecordingServer(HttpTimeouts.GOOGLE_CALENDAR) { request ->
                if (request.url.host == "www.googleapis.com") {
                    answer("", status, headersOf(HttpHeaders.Location, "https://other.example/next?q=1"))
                } else {
                    answer("""{"items":[{"id":"moved"}]}""")
                }
            }

            val read = api(server).listEvents(credential, timeMin = from)

            assertEquals("moved", read.events.single().id, "after $status")
            assertEquals("https://other.example/next?q=1", server.requests[1].url.toString())
            assertEquals("GET", server.requests[1].method.value)
            assertEquals("Bearer ya29.token", server.requests[1].headers[HttpHeaders.Authorization])
        }
    }

    @Test
    fun `a 300 is not followed - and the eleventh redirect is the answer`() = runTest {
        val multiple = RecordingServer(HttpTimeouts.GOOGLE_CALENDAR) {
            answer("", status = 300, headers = headersOf(HttpHeaders.Location, "/elsewhere"))
        }
        assertEquals(300, refusalOf(multiple).statusCode)
        assertEquals(1, multiple.requests.size)

        val loop = RecordingServer(HttpTimeouts.GOOGLE_CALENDAR) {
            answer("", status = 302, headers = headersOf(HttpHeaders.Location, "/calendar/v3/loop"))
        }
        assertEquals(302, refusalOf(loop).statusCode)
        assertEquals(11, loop.requests.size)
    }

    @Test
    fun `a refusal carries its status - Google's reasons and the WWW-Authenticate header`() = runTest {
        val server = RecordingServer {
            answer(
                """{"error":{"code":403,"message":"Request had insufficient authentication scopes.",""" +
                    """"errors":[{"message":"Insufficient Permission","domain":"global",""" +
                    """"reason":"insufficientPermissions"}],""" +
                    """"status":"PERMISSION_DENIED"}}""",
                status = 403,
                headers = headersOf(HttpHeaders.WWWAuthenticate, INSUFFICIENT_SCOPE)
            )
        }

        val refusal = refusalOf(server)

        assertEquals(403, refusal.statusCode)
        assertEquals(listOf("insufficientPermissions"), refusal.reasons)
        assertEquals(INSUFFICIENT_SCOPE, refusal.authenticate)
    }

    @Test
    fun `a refusal with no JSON body has no reasons`() = runTest {
        val server = RecordingServer { answer("Unauthorized", status = 401) }

        val refusal = refusalOf(server)

        assertEquals(401, refusal.statusCode)
        assertTrue(refusal.reasons.isEmpty())
        assertNull(refusal.authenticate)
    }

    @Test
    fun `Google out of reach is an IOException - not a refusal`() = runTest {
        val server = RecordingServer { throw IOException("offline") }

        assertFailsWith<IOException> { api(server).listEvents(credential, timeMin = from) }
    }

    @Test
    fun `a credential never prints its token`() {
        assertFalse("ya29" in credential.toString())
    }

    private suspend fun refusalOf(server: RecordingServer): GoogleCalendarHttpException =
        assertFailsWith<GoogleCalendarHttpException> { api(server).listEvents(credential, timeMin = from) }

    private companion object {
        const val INSUFFICIENT_SCOPE = "Bearer realm=\"https://accounts.google.com/\", error=\"insufficient_scope\""
    }
}
