package com.coparently.app.data.school.bakalari

import com.coparently.app.data.http.RecordingServer
import com.coparently.app.data.http.answer
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlinx.io.IOException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/**
 * What the Bakaláři client makes of a school's answers (MON-8), on Ktor since MON-24: the token
 * pair and its lifetime, `invalid_grant` matched on the error and never on the Czech description,
 * the status of a resource call, the server check, the https rule and a network failure. The
 * rotation of the refresh token and the retry on a `401` are the authorizer's, in the app.
 */
class BakalariClientTest {

    private val clock = 1_000_000L

    private fun client(server: RecordingServer, requireHttps: Boolean = true) =
        BakalariClient(server.client, requireHttps) { clock }

    @Test
    fun `sign-in reads the pair and its lifetime`() = runTest {
        val server = RecordingServer { answer(tokenResponse("access-1", "refresh-1", expiresIn = 3599)) }

        val tokens = client(server).signIn(BASE, "novak", "tajneHeslo1")

        assertEquals(BakalariTokens("access-1", "refresh-1", clock + 3_599_000L), tokens)
    }

    @Test
    fun `a refresh an older server answers without a refresh token keeps the one just redeemed`() = runTest {
        val server = RecordingServer { answer("""{"access_token":"access-2","expires_in":599}""") }

        val tokens = client(server).refresh(BASE, "refresh-1")

        assertEquals(BakalariTokens("access-2", "refresh-1", clock + 599_000L), tokens)
    }

    @Test
    fun `a wrong password is invalid_grant - matched on the error and not on the Czech description`() = runTest {
        val server = RecordingServer {
            answer("""{"error":"invalid_grant","error_description":"Špatný login nebo heslo"}""", status = 400)
        }

        assertFailsWith<BakalariException.InvalidGrant> { client(server).signIn(BASE, "novak", "wrong") }
    }

    @Test
    fun `another refusal of the sign-in is its status`() = runTest {
        val server = RecordingServer { answer("""{"error":"invalid_client"}""", status = 400) }

        val failure = assertFailsWith<BakalariException.Http> { client(server).signIn(BASE, "novak", "heslo") }

        assertEquals(400, failure.status)
    }

    @Test
    fun `a resource call returns the body - and names a 401 a 403 and any other status`() = runTest {
        var status = 200
        val server = RecordingServer { answer("""{"Events":[]}""", status = status) }
        val client = client(server)

        assertEquals("""{"Events":[]}""", client.eventsJson(BASE, "t", LocalDate(2026, 10, 5)))
        status = 401
        assertFailsWith<BakalariException.Unauthorized> { client.userJson(BASE, "t") }
        status = 403
        assertFailsWith<BakalariException.Forbidden> { client.eventsJson(BASE, "t", LocalDate(2026, 10, 5)) }
        status = 500
        assertEquals(500, assertFailsWith<BakalariException.Http> { client.userJson(BASE, "t") }.status)
    }

    @Test
    fun `a byte order mark before the JSON is dropped`() = runTest {
        val server = RecordingServer { answer("﻿" + tokenResponse("access-1", "refresh-1", expiresIn = 599)) }

        assertEquals("access-1", client(server).signIn(BASE, "novak", "heslo").accessToken)
    }

    @Test
    fun `a Bakalari server answers GET api with its version - anything else is refused`() = runTest {
        var reply = """[{"ApiVersion":"3.12.0","ApplicationVersion":"1.32.625.2","BaseUrl":"api/3"}]"""
        var status = 200
        val server = RecordingServer { answer(reply, status = status) }
        val client = client(server)

        client.checkServer(BASE)
        reply = "<html>Not found</html>"
        status = 404
        assertFailsWith<BakalariException.NotBakalari> { client.checkServer(BASE) }
        reply = """[{"Something":"else"}]"""
        status = 200
        assertFailsWith<BakalariException.NotBakalari> { client.checkServer(BASE) }
    }

    @Test
    fun `the production client sends nothing to an address that is not https`() = runTest {
        val server = RecordingServer()

        assertFailsWith<BakalariException.InsecureUrl> {
            BakalariClient(server.client).signIn("http://skola.cz", "novak", "heslo")
        }
        assertEquals(0, server.requests.size)
    }

    @Test
    fun `an address that is not a web address is not a Bakalari server`() = runTest {
        val server = RecordingServer()

        assertFailsWith<BakalariException.NotBakalari> { client(server).checkServer("ftp://skola.cz") }
        assertFailsWith<BakalariException.NotBakalari> { client(server).checkServer("skola.cz") }
        assertEquals(0, server.requests.size)
    }

    @Test
    fun `no network is a network failure`() = runTest {
        val server = RecordingServer { throw IOException("offline") }

        assertFailsWith<BakalariException.Network> { client(server).signIn(BASE, "novak", "heslo") }
    }

    private fun tokenResponse(access: String, refresh: String, expiresIn: Int) =
        """{"bak:ApiVersion":"3.13.0","access_token":"$access","refresh_token":"$refresh",""" +
            """"token_type":"Bearer","expires_in":$expiresIn,"scope":"openid profile offline_access bakalari_api"}"""

    private companion object {
        const val BASE = "https://skola.example.cz"
    }
}
