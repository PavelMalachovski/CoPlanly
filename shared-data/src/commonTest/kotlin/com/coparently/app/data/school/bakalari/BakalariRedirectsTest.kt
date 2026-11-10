package com.coparently.app.data.school.bakalari

import com.coparently.app.data.http.RecordingServer
import com.coparently.app.data.http.answer
import io.ktor.client.request.HttpRequestData
import io.ktor.http.HttpHeaders
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

/**
 * The Bakaláři client follows redirects as OkHttp 4.12 followed them (MON-24 fix round). Each
 * expectation was observed by running OkHttp 4.12 against MockWebServer 4.12 and printed: a POST
 * answered 307 or 308 is sent again with its method and body, a POST answered 300 to 303 becomes a
 * GET without a body, a GET follows all six, `Authorization` is dropped when the host or port
 * changes, a redirect without a `Location` or to another scheme is the answer, and the 21st
 * follow-up fails the call as an `IOException`.
 */
class BakalariRedirectsTest {

    private val tokens = """{"access_token":"a","refresh_token":"r","expires_in":3599}"""

    /** A school that redirects its first request with [status] to [location], then answers [then]. */
    private fun redirecting(status: Int, location: String = "/next?x=1", then: String = tokens) =
        RecordingServer { request ->
            if (request.isFirst()) answer("", status, headersOf(HttpHeaders.Location, location)) else answer(then)
        }

    private fun HttpRequestData.isFirst(): Boolean = url.encodedPath == "/api/login" || url.encodedPath == "/api/3/user"

    @Test
    fun `a sign-in answered 307 or 308 is posted again - form and all`() = runTest {
        for (status in listOf(307, 308)) {
            val server = redirecting(status)

            assertEquals("a", BakalariClient(server.client).signIn(BASE, "novak", "heslo").accessToken)

            val again = server.requests[1]
            assertEquals("POST", again.method.value, "after $status")
            assertEquals("https://skola.example.cz/next?x=1", again.url.toString())
            assertEquals("application/x-www-form-urlencoded", again.body.contentType.toString())
            assertEquals("client_id=ANDR&grant_type=password&username=novak&password=heslo", server.bodyOf(1))
        }
    }

    @Test
    fun `a sign-in answered 300 to 303 is asked for again as a GET without the form`() = runTest {
        for (status in listOf(300, 301, 302, 303)) {
            val server = redirecting(status)

            BakalariClient(server.client).signIn(BASE, "novak", "heslo")

            val again = server.requests[1]
            assertEquals("GET", again.method.value, "after $status")
            assertEquals("https://skola.example.cz/next?x=1", again.url.toString())
            assertEquals("", server.bodyOf(1))
            assertNull(again.body.contentType)
        }
    }

    @Test
    fun `a GET follows every redirect with its headers - and drops the token for another host`() = runTest {
        for (status in listOf(300, 301, 302, 303, 307, 308)) {
            val sameHost = redirecting(status, then = "{}")
            BakalariClient(sameHost.client).userJson(BASE, "token-1")
            assertEquals("GET", sameHost.requests[1].method.value, "after $status")
            assertEquals("Bearer token-1", sameHost.requests[1].headers[HttpHeaders.Authorization])
            assertEquals("application/json", sameHost.requests[1].headers[HttpHeaders.Accept])
            assertEquals("okhttp/4.12.0", sameHost.requests[1].headers[HttpHeaders.UserAgent])

            val otherPort = redirecting(status, location = "https://skola.example.cz:444/elsewhere", then = "{}")
            BakalariClient(otherPort.client).userJson(BASE, "token-1")
            assertEquals("https://skola.example.cz:444/elsewhere", otherPort.requests[1].url.toString())
            assertNull(otherPort.requests[1].headers[HttpHeaders.Authorization], "after $status")
            assertEquals("application/json", otherPort.requests[1].headers[HttpHeaders.Accept])
        }
    }

    @Test
    fun `a relative Location is resolved against the address it answered`() = runTest {
        val server = RecordingServer { request ->
            if (request.url.encodedPath == "/bakaweb/api/login") {
                answer("", status = 302, headers = headersOf(HttpHeaders.Location, "rel?y=2"))
            } else {
                answer(tokens)
            }
        }

        BakalariClient(server.client).signIn("https://skola.example.cz/bakaweb", "novak", "heslo")

        assertEquals("https://skola.example.cz/bakaweb/api/rel?y=2", server.requests[1].url.toString())
    }

    @Test
    fun `a redirect without a Location or to another scheme is the answer`() = runTest {
        val noLocation = RecordingServer { answer("", status = 302) }
        assertEquals(302, statusOf(noLocation))
        assertEquals(1, noLocation.requests.size)

        val ftp = redirecting(302, location = "ftp://skola.example.cz/x")
        assertEquals(302, statusOf(ftp))
        assertEquals(1, ftp.requests.size)
    }

    @Test
    fun `the twenty-first follow-up is a network failure`() = runTest {
        val loop = RecordingServer { answer("", status = 302, headers = headersOf(HttpHeaders.Location, "/loop")) }

        assertFailsWith<BakalariException.Network> { BakalariClient(loop.client).checkServer(BASE) }
        assertEquals(21, loop.requests.size)
    }

    private suspend fun statusOf(server: RecordingServer): Int =
        assertFailsWith<BakalariException.Http> { BakalariClient(server.client).userJson(BASE, "t") }.status

    private companion object {
        const val BASE = "https://skola.example.cz"
    }
}
