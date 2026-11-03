@file:OptIn(InternalSharedApi::class)

package com.coparently.app.data.school.bakalari

import com.coparently.app.data.school.SchoolConnectionStore
import com.coparently.app.data.school.SchoolTestSupport
import com.coparently.app.domain.InternalSharedApi
import com.coparently.app.domain.school.SchoolConnectionStatus
import com.coparently.app.domain.school.SchoolImportKind
import com.coparently.app.domain.school.SchoolImportRecord
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.toByteArray
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.HttpResponseData
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import org.junit.After
import org.junit.Before
import org.junit.Test
import java.io.File
import java.nio.file.Files
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Keeping a school connection signed in (MON-8), through the real client against an in-memory
 * server: the rotating refresh token written down before it is used, `invalid_grant` marking the
 * connection without deleting anything, one refresh-and-retry on a `401`, and the school's answers
 * read into the school domain.
 *
 * The client moved to `:shared-data` on Ktor in MON-24, and its own requests and answers are
 * tested there (`BakalariRequestShapeTest`, `BakalariClientTest`); this was `BakalariClientTest`
 * on OkHttp's MockWebServer, and keeps its cases and assertions.
 */
class BakalariAuthorizerTest {

    private lateinit var dir: File
    private lateinit var store: SchoolConnectionStore
    private val clock = 1_000_000L
    private val requests = CopyOnWriteArrayList<HttpRequestData>()
    private val replies = ArrayDeque<Pair<String, Int>>()
    private var dispatcher: (suspend MockRequestHandleScope.(HttpRequestData) -> HttpResponseData)? = null
    private val client = BakalariClient(
        HttpClient(
            MockEngine { request ->
                requests += request
                dispatcher?.invoke(this, request)
                    ?: replies.removeFirst().let { (body, status) -> respond(body, HttpStatusCode.fromValue(status)) }
            }
        )
    )
    private val authorizer by lazy { BakalariAuthorizer.on(client, store) { clock } }

    @Before
    fun setUp() {
        dir = Files.createTempDirectory("bakalari").toFile()
        store = SchoolConnectionStore(SchoolTestSupport.preferences(dir))
    }

    @After
    fun tearDown() {
        dir.deleteRecursively()
    }

    @Test
    fun `an expired token is refreshed, and the rotated pair is stored before it is used`() = runTest {
        store.put(SchoolTestSupport.stored(baseUrl = BASE, tokens = BakalariTokens("old-access", "old-refresh", 0L)))
        val storedWhenUsed = CopyOnWriteArrayList<String>()
        dispatcher = { request ->
            when (request.url.encodedPath) {
                "/api/login" -> reply(tokenResponse("new-access", "new-refresh", expiresIn = 599))
                else -> {
                    storedWhenUsed += store.get("alice", "conn1")?.tokens?.refreshToken.orEmpty()
                    reply(SchoolTestSupport.fixture("user.json"))
                }
            }
        }

        val account = authorizer.withAccess("alice", "conn1") { base, token ->
            assertEquals("new-access", token)
            client.user(base, token)
        }

        assertEquals("XL", account.student.classId)
        assertEquals(listOf("new-refresh"), storedWhenUsed)
        assertTrue("grant_type=refresh_token" in requests[0].body.toByteArray().decodeToString())
        assertEquals("Bearer new-access", requests[1].headers["Authorization"])
    }

    @Test
    fun `a refused refresh marks the connection as needing the password and deletes nothing`() = runTest {
        val ledger = mapOf("school-x" to SchoolImportRecord(SchoolImportKind.EVENT, LocalDate(2026, 10, 7), "f"))
        store.put(
            SchoolTestSupport.stored(baseUrl = BASE, tokens = BakalariTokens("a", "dead-refresh", 0L))
                .copy(ledger = ledger)
        )
        enqueue(
            """{"error":"invalid_grant",""" +
                """"error_description":"The specified refresh token has already been redeemed."}""",
            status = 400
        )

        assertFailsWith<BakalariException.NeedsPassword> {
            authorizer.withAccess("alice", "conn1") { base, token -> client.user(base, token) }
        }

        val stored = assertNotNull(store.get("alice", "conn1"))
        assertEquals(SchoolConnectionStatus.NEEDS_PASSWORD, stored.connection.status)
        assertEquals(ledger, stored.ledger)
        assertEquals("child-1", stored.connection.childId)
        // And no further request is made with it until the password comes back.
        assertFailsWith<BakalariException.NeedsPassword> {
            authorizer.withAccess("alice", "conn1") { base, token -> client.user(base, token) }
        }
        assertEquals(1, requests.size)
    }

    @Test
    fun `a 401 refreshes once and retries once`() = runTest {
        store.put(SchoolTestSupport.stored(baseUrl = BASE, tokens = STALE))
        enqueue(DENIED, status = 401)
        enqueue(tokenResponse("fresh", "refresh-2", expiresIn = 3599))
        enqueue(SchoolTestSupport.fixture("events.json"))

        val events = authorizer.withAccess("alice", "conn1") { base, token ->
            client.events(base, token, LocalDate(2026, 10, 5))
        }

        assertEquals("TBVQN", events.single().id)
        assertEquals("Bearer stale", requests[0].headers["Authorization"])
        val retried = requests[2]
        assertEquals("Bearer fresh", retried.headers["Authorization"])
        assertEquals("https://skola.example.cz/api/3/events?from=2026-10-05", retried.url.toString())
        assertEquals("refresh-2", store.get("alice", "conn1")?.tokens?.refreshToken)
    }

    @Test
    fun `a second 401 after the retry is the answer`() = runTest {
        store.put(SchoolTestSupport.stored(baseUrl = BASE, tokens = STALE))
        enqueue("", status = 401)
        enqueue(tokenResponse("fresh", "refresh-2", expiresIn = 3599))
        enqueue("", status = 401)

        assertFailsWith<BakalariException.Unauthorized> {
            authorizer.withAccess("alice", "conn1") { base, token -> client.user(base, token) }
        }
        assertEquals(3, requests.size)
    }

    @Test
    fun `the timetable is asked for by date and read for the requested week`() = runTest {
        enqueue(SchoolTestSupport.fixture("bezny.json"))

        val week = client.timetable(BASE, "token", LocalDate(2020, 3, 4))

        assertEquals(
            "https://skola.example.cz/api/3/timetable/actual?date=2020-03-04",
            requests.single().url.toString()
        )
        assertEquals(5, week.days.size)
    }

    private fun enqueue(body: String, status: Int = 200) {
        replies += body to status
    }

    private fun MockRequestHandleScope.reply(body: String): HttpResponseData = respond(body)

    private companion object {
        const val BASE = "https://skola.example.cz"

        /** A pair whose access token the server no longer accepts, although it has not expired. */
        val STALE = BakalariTokens("stale", "refresh-1", Long.MAX_VALUE)

        /** What a resource endpoint answers a refused token with. */
        const val DENIED = """{"Message":"Authorization has been denied for this request."}"""
    }

    private fun tokenResponse(access: String, refresh: String, expiresIn: Int) =
        """
        {"bak:ApiVersion":"3.13.0","access_token":"$access","refresh_token":"$refresh",
         "token_type":"Bearer","expires_in":$expiresIn,"scope":"openid profile offline_access bakalari_api"}
        """.trimIndent()
}
