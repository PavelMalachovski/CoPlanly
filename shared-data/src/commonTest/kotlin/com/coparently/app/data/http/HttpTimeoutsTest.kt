package com.coparently.app.data.http

import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.headersOf
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import kotlinx.io.IOException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

/**
 * Each client waits as the library it replaced waited (MON-24 fix round): the school import's
 * OkHttp client was built with 20-second connect, read and write timeouts and a 40-second
 * `callTimeout` (`SchoolModule`, `d935524c`); google-http-client 1.42.3's `HttpRequest` has a
 * 20-second connect and read timeout, no write timeout and no limit on the call, and the Calendar
 * import never changed them.
 *
 * The school client's 40 seconds cover the whole call, every redirect included, as OkHttp's
 * `callTimeout` did (fix round 2): each leg below takes 15 seconds of virtual time, below the
 * 20-second read timeout, so only a limit on the chain can stop it.
 */
@OptIn(ExperimentalCoroutinesApi::class) // the test scheduler's virtual clock
class HttpTimeoutsTest {

    @Test
    fun `the school client waits as its OkHttp client did`() {
        assertEquals(20_000L, HttpTimeouts.SCHOOL.connectMillis)
        assertEquals(20_000L, HttpTimeouts.SCHOOL.socketMillis)
        assertEquals(40_000L, HttpTimeouts.SCHOOL.callMillis)
    }

    @Test
    fun `the Calendar client waits as google-http-client did - with no limit on the call`() {
        assertEquals(20_000L, HttpTimeouts.GOOGLE_CALENDAR.connectMillis)
        assertEquals(20_000L, HttpTimeouts.GOOGLE_CALENDAR.socketMillis)
        assertNull(HttpTimeouts.GOOGLE_CALENDAR.callMillis)
    }

    @Test
    fun `the school client's call limit covers the whole redirect chain`() = runTest {
        assertEquals(LEG_MILLIS * 2, timeOfChain(HttpTimeouts.SCHOOL, legs = 2))

        val startedAt = testScheduler.currentTime
        assertFailsWith<IOException> { timeOfChain(HttpTimeouts.SCHOOL, legs = 3) }
        assertEquals(40_000L, testScheduler.currentTime - startedAt)
    }

    @Test
    fun `the Calendar client's chain has no limit - as google-http-client's had none`() = runTest {
        assertEquals(LEG_MILLIS * 5, timeOfChain(HttpTimeouts.GOOGLE_CALENDAR, legs = 5))
    }

    /**
     * The virtual time a call takes whose first [legs] - 1 answers each redirect to the next leg,
     * every answer after [LEG_MILLIS].
     */
    private suspend fun TestScope.timeOfChain(timeouts: HttpTimeouts, legs: Int): Long {
        val server = RecordingServer(timeouts, StandardTestDispatcher(testScheduler)) { request ->
            delay(LEG_MILLIS)
            val leg = request.url.parameters["leg"]!!.toInt()
            if (leg < legs) answer("", 302, headersOf(HttpHeaders.Location, "/next?leg=${leg + 1}")) else answer("done")
        }
        val startedAt = testScheduler.currentTime
        val start = Outgoing(HttpMethod.Get, "https://skola.example.cz/start?leg=1")
        val answer = server.client.send(start, Redirects.OKHTTP_4)
        assertEquals("done", answer.body)
        assertEquals(legs, server.requests.size)
        return testScheduler.currentTime - startedAt
    }

    private companion object {
        const val LEG_MILLIS = 15_000L
    }
}
