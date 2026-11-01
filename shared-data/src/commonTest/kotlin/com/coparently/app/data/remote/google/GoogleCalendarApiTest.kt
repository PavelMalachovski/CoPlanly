package com.coparently.app.data.remote.google

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
    fun `a page with no items is an empty page`() = runTest {
        val server = RecordingServer { answer("""{"kind":"calendar#events"}""") }

        assertTrue(api(server).listEvents(credential, timeMin = from).events.isEmpty())
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
