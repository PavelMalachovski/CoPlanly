package com.coparently.app.data.school.bakalari

import com.coparently.app.data.http.RecordingServer
import com.coparently.app.data.http.answer
import io.ktor.http.HttpHeaders
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Every request the Bakaláři client makes, as the bytes OkHttp sent before the client moved to
 * Ktor (MON-24): method, address, the headers a server reads, and the sign-in form.
 *
 * The expected texts are not this client's own output copied back. They were produced by running
 * the OkHttp 4.12 code the app shipped (`FormBody.Builder`, `HttpUrl.Builder.addPathSegments`,
 * `addQueryParameter`) over the same arguments, and pasted here.
 */
class BakalariRequestShapeTest {

    private val tokens = """{"access_token":"a","refresh_token":"r","expires_in":3599}"""

    @Test
    fun `the password grant is a form POST to api login with the ANDR client id`() = runTest {
        val server = RecordingServer { answer(tokens) }

        BakalariClient(server.client).signIn(BASE, "novak", "tajneHeslo1")

        val request = server.requests.single()
        assertEquals("POST", request.method.value)
        assertEquals("https://skola.example.cz/api/login", request.url.toString())
        assertEquals("application/x-www-form-urlencoded", request.body.contentType.toString())
        assertEquals("client_id=ANDR&grant_type=password&username=novak&password=tajneHeslo1", server.bodyOf(0))
        assertNull(request.headers[HttpHeaders.Authorization])
    }

    @Test
    fun `every request names OkHttp 4_12 as its agent - as the client it replaced did`() = runTest {
        val server = RecordingServer { answer(tokens) }
        val client = BakalariClient(server.client)

        client.signIn(BASE, "novak", "tajneHeslo1")
        runCatching { client.timetableJson(BASE, "token-1", LocalDate(2026, 9, 14)) }
        runCatching { client.checkServer(BASE) }

        assertEquals(3, server.requests.size)
        server.requests.forEach { request ->
            assertEquals("okhttp/4.12.0", request.headers[HttpHeaders.UserAgent], request.url.toString())
        }
    }

    @Test
    fun `the refresh grant is a form POST with the refresh token alone`() = runTest {
        val server = RecordingServer { answer(tokens) }

        BakalariClient(server.client).refresh(BASE, "old-refresh")

        val request = server.requests.single()
        assertEquals("POST", request.method.value)
        assertEquals("https://skola.example.cz/api/login", request.url.toString())
        assertEquals("application/x-www-form-urlencoded", request.body.contentType.toString())
        assertEquals("client_id=ANDR&grant_type=refresh_token&refresh_token=old-refresh", server.bodyOf(0))
    }

    @Test
    fun `a name and a password are encoded as OkHttp's FormBody encoded them`() = runTest {
        val server = RecordingServer { answer(tokens) }

        BakalariClient(server.client).signIn(BASE, "Novák Jan", "p@ss w+rd&x=y%20!~*'()$,;/?:#[]{}|\\^`\"<>é€\u0001")

        assertEquals(
            "client_id=ANDR&grant_type=password&username=Nov%C3%A1k%20Jan&password=" +
                "p%40ss%20w%2Brd%26x%3Dy%2520%21%7E*%27%28%29%24%2C%3B%2F%3F%3A%23%5B%5D%7B%7D%7C%5C%5E%60%22%3C%3E" +
                "%C3%A9%E2%82%AC%01",
            server.bodyOf(0)
        )
    }

    @Test
    fun `the timetable is a GET by date with the bearer token and JSON asked for`() = runTest {
        val server = RecordingServer { answer("{}") }

        BakalariClient(server.client).timetableJson(BASE, "token-1", LocalDate(2020, 3, 4))

        val request = server.requests.single()
        assertEquals("GET", request.method.value)
        assertEquals("https://skola.example.cz/api/3/timetable/actual?date=2020-03-04", request.url.toString())
        assertEquals("Bearer token-1", request.headers[HttpHeaders.Authorization])
        assertEquals("application/json", request.headers[HttpHeaders.Accept])
    }

    @Test
    fun `the events are a GET from a date with the bearer token and JSON asked for`() = runTest {
        val server = RecordingServer { answer("{}") }

        BakalariClient(server.client).eventsJson(BASE, "token-1", LocalDate(2026, 10, 5))

        val request = server.requests.single()
        assertEquals("GET", request.method.value)
        assertEquals("https://skola.example.cz/api/3/events?from=2026-10-05", request.url.toString())
        assertEquals("Bearer token-1", request.headers[HttpHeaders.Authorization])
        assertEquals("application/json", request.headers[HttpHeaders.Accept])
    }

    @Test
    fun `the account is a GET of api 3 user with the bearer token`() = runTest {
        val server = RecordingServer { answer("{}") }

        BakalariClient(server.client).userJson(BASE, "token-1")

        val request = server.requests.single()
        assertEquals("GET", request.method.value)
        assertEquals("https://skola.example.cz/api/3/user", request.url.toString())
        assertEquals("Bearer token-1", request.headers[HttpHeaders.Authorization])
        assertEquals("application/json", request.headers[HttpHeaders.Accept])
    }

    @Test
    fun `the server check is an unauthenticated GET of api`() = runTest {
        val server = RecordingServer { answer("""[{"ApiVersion":"3.12.0"}]""") }

        BakalariClient(server.client).checkServer(BASE)

        val request = server.requests.single()
        assertEquals("GET", request.method.value)
        assertEquals("https://skola.example.cz/api", request.url.toString())
        assertNull(request.headers[HttpHeaders.Authorization])
    }

    @Test
    fun `a school on a path and a port keeps both - with or without a trailing slash`() = runTest {
        val server = RecordingServer { answer(tokens) }
        val client = BakalariClient(server.client)

        client.signIn("https://skola.cz:444/bakaweb", "novak", "heslo")
        client.signIn("https://skola.cz:444/bakaweb/", "novak", "heslo")

        assertEquals("https://skola.cz:444/bakaweb/api/login", server.requests[0].url.toString())
        assertEquals("https://skola.cz:444/bakaweb/api/login", server.requests[1].url.toString())
    }

    private companion object {
        const val BASE = "https://skola.example.cz"
    }
}
