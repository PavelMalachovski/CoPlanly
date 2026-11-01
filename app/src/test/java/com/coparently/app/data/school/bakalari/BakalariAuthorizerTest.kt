package com.coparently.app.data.school.bakalari

import com.coparently.app.data.school.SchoolConnectionStore
import com.coparently.app.data.school.SchoolTestSupport
import com.coparently.app.domain.school.SchoolConnectionStatus
import com.coparently.app.domain.school.SchoolImportKind
import com.coparently.app.domain.school.SchoolImportRecord
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.After
import org.junit.Before
import org.junit.Test
import java.io.File
import java.nio.file.Files
import java.time.LocalDate
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * The phone's line to a Bakaláři server, over real HTTP on a local server (MON-8): the sign-in,
 * the rotating refresh token written down before it is used, `invalid_grant` marking the
 * connection without deleting anything, and one refresh-and-retry on a `401`.
 */
class BakalariClientTest {

    private val server = MockWebServer()
    private lateinit var dir: File
    private lateinit var store: SchoolConnectionStore
    private var clock = 1_000_000L
    private val client by lazy {
        BakalariClient(OkHttpClient(), requireHttps = false, io = Dispatchers.IO, now = { clock })
    }
    private val authorizer by lazy { BakalariAuthorizer(client, store) { clock } }

    @Before
    fun setUp() {
        server.start()
        dir = Files.createTempDirectory("bakalari").toFile()
        store = SchoolConnectionStore(SchoolTestSupport.preferences(dir))
    }

    @After
    fun tearDown() {
        server.shutdown()
        dir.deleteRecursively()
    }

    private val baseUrl get() = server.url("/").toString().trimEnd('/')

    @Test
    fun `sign-in posts the password grant once and reads the pair and its lifetime`() = runTest {
        server.enqueue(tokenResponse("access-1", "refresh-1", expiresIn = 3599))

        val tokens = client.signIn(baseUrl, "novak", "tajneHeslo1")

        val request = server.takeRequest()
        assertEquals("/api/login", request.path)
        assertEquals("POST", request.method)
        val form = request.body.readUtf8()
        assertTrue("client_id=ANDR" in form)
        assertTrue("grant_type=password" in form)
        assertTrue("username=novak" in form)
        assertTrue("password=tajneHeslo1" in form)
        assertEquals(BakalariTokens("access-1", "refresh-1", clock + 3_599_000L), tokens)
    }

    @Test
    fun `a wrong password is invalid_grant, matched on the error and not on the Czech description`() = runTest {
        server.enqueue(
            MockResponse().setResponseCode(400)
                .setBody("""{"error":"invalid_grant","error_description":"Špatný login nebo heslo"}""")
        )

        assertFailsWith<BakalariException.InvalidGrant> { client.signIn(baseUrl, "novak", "wrong") }
    }

    @Test
    fun `an expired token is refreshed, and the rotated pair is stored before it is used`() = runTest {
        store.put(SchoolTestSupport.stored(baseUrl = baseUrl, tokens = BakalariTokens("old-access", "old-refresh", 0L)))
        val storedWhenUsed = CopyOnWriteArrayList<String>()
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse = when (request.path) {
                "/api/login" -> tokenResponse("new-access", "new-refresh", expiresIn = 599)
                else -> {
                    storedWhenUsed += store.get("alice", "conn1")?.tokens?.refreshToken.orEmpty()
                    MockResponse().setBody(SchoolTestSupport.fixture("user.json"))
                }
            }
        }

        val account = authorizer.withAccess("alice", "conn1") { base, token ->
            assertEquals("new-access", token)
            client.user(base, token)
        }

        assertEquals("XL", account.student.classId)
        assertEquals(listOf("new-refresh"), storedWhenUsed)
        val login = server.takeRequest()
        assertTrue("grant_type=refresh_token" in login.body.readUtf8())
        assertEquals("Bearer new-access", server.takeRequest().getHeader("Authorization"))
    }

    @Test
    fun `a refused refresh marks the connection as needing the password and deletes nothing`() = runTest {
        val ledger = mapOf("school-x" to SchoolImportRecord(SchoolImportKind.EVENT, LocalDate.of(2026, 10, 7), "f"))
        store.put(
            SchoolTestSupport.stored(baseUrl = baseUrl, tokens = BakalariTokens("a", "dead-refresh", 0L))
                .copy(ledger = ledger)
        )
        server.enqueue(
            MockResponse().setResponseCode(400)
                .setBody(
                    """{"error":"invalid_grant",""" +
                        """"error_description":"The specified refresh token has already been redeemed."}"""
                )
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
        assertEquals(1, server.requestCount)
    }

    @Test
    fun `a 401 refreshes once and retries once`() = runTest {
        store.put(SchoolTestSupport.stored(baseUrl = baseUrl, tokens = STALE))
        server.enqueue(MockResponse().setResponseCode(401).setBody(DENIED))
        server.enqueue(tokenResponse("fresh", "refresh-2", expiresIn = 3599))
        server.enqueue(MockResponse().setBody(SchoolTestSupport.fixture("events.json")))

        val events = authorizer.withAccess("alice", "conn1") { base, token ->
            client.events(base, token, LocalDate.of(2026, 10, 5))
        }

        assertEquals("TBVQN", events.single().id)
        assertEquals("Bearer stale", server.takeRequest().getHeader("Authorization"))
        server.takeRequest()
        val retried = server.takeRequest()
        assertEquals("Bearer fresh", retried.getHeader("Authorization"))
        assertEquals("/api/3/events?from=2026-10-05", retried.path)
        assertEquals("refresh-2", store.get("alice", "conn1")?.tokens?.refreshToken)
    }

    @Test
    fun `a second 401 after the retry is the answer`() = runTest {
        store.put(SchoolTestSupport.stored(baseUrl = baseUrl, tokens = STALE))
        server.enqueue(MockResponse().setResponseCode(401))
        server.enqueue(tokenResponse("fresh", "refresh-2", expiresIn = 3599))
        server.enqueue(MockResponse().setResponseCode(401))

        assertFailsWith<BakalariException.Unauthorized> {
            authorizer.withAccess("alice", "conn1") { base, token -> client.user(base, token) }
        }
        assertEquals(3, server.requestCount)
    }

    @Test
    fun `the timetable is asked for by date and read for the requested week`() = runTest {
        server.enqueue(MockResponse().setBody(SchoolTestSupport.fixture("bezny.json")))

        val week = client.timetable(baseUrl, "token", LocalDate.of(2020, 3, 4))

        assertEquals("/api/3/timetable/actual?date=2020-03-04", server.takeRequest().path)
        assertEquals(5, week.days.size)
    }

    @Test
    fun `a Bakalari server answers GET api with its version, anything else is refused`() = runTest {
        server.enqueue(
            MockResponse().setBody("""[{"ApiVersion":"3.12.0","ApplicationVersion":"1.32.625.2","BaseUrl":"api/3"}]""")
        )
        server.enqueue(MockResponse().setResponseCode(404).setBody("<html>Not found</html>"))

        client.checkServer(baseUrl)
        assertFailsWith<BakalariException.NotBakalari> { client.checkServer(baseUrl) }
        assertEquals("/api", server.takeRequest().path)
    }

    @Test
    fun `the production client sends nothing to an address that is not https`() = runTest {
        val strict = BakalariClient(OkHttpClient(), requireHttps = true, io = Dispatchers.IO, now = { clock })

        assertFailsWith<BakalariException.InsecureUrl> { strict.signIn(baseUrl, "novak", "heslo") }
        assertEquals(0, server.requestCount)
    }

    @Test
    fun `no network is a network failure`() = runTest {
        val unreachable = baseUrl
        server.shutdown()

        assertFailsWith<BakalariException.Network> { client.signIn(unreachable, "novak", "heslo") }
    }

    private companion object {
        /** A pair whose access token the server no longer accepts, although it has not expired. */
        val STALE = BakalariTokens("stale", "refresh-1", Long.MAX_VALUE)

        /** What a resource endpoint answers a refused token with. */
        const val DENIED = """{"Message":"Authorization has been denied for this request."}"""
    }

    private fun tokenResponse(access: String, refresh: String, expiresIn: Int) = MockResponse().setBody(
        """
        {"bak:ApiVersion":"3.13.0","access_token":"$access","refresh_token":"$refresh",
         "token_type":"Bearer","expires_in":$expiresIn,"scope":"openid profile offline_access bakalari_api"}
        """.trimIndent()
    )
}
