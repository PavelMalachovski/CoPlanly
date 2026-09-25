package com.coparently.app.data.school

import com.coparently.app.data.school.bakalari.BakalariAuthorizer
import com.coparently.app.data.school.bakalari.BakalariClient
import com.coparently.app.domain.model.User
import com.coparently.app.domain.repository.UserRepository
import com.coparently.app.domain.school.SchoolConnectionStatus
import com.coparently.app.domain.school.SchoolDayType
import com.coparently.app.domain.school.SchoolImportKind
import com.coparently.app.domain.school.SchoolImportPlan
import com.coparently.app.domain.school.SchoolImportWording
import com.coparently.app.domain.school.SchoolSyncFailure
import com.coparently.app.domain.school.SchoolSyncResult
import io.mockk.coEvery
import io.mockk.mockk
import io.mockk.slot
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
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * One school update end to end on the JVM (MON-8): a real client and authorizer against a local
 * server, the real store, and the calendar side mocked — what it fetches, what it records, and
 * how it marks the connection.
 */
class SchoolImporterTest {

    private val server = MockWebServer()
    private lateinit var dir: File
    private lateinit var store: SchoolConnectionStore
    private val clock = Clock.fixed(Instant.parse("2020-03-02T06:00:00Z"), ZoneOffset.UTC)
    private val users = mockk<UserRepository>()
    private val writer = mockk<SchoolEventWriter>()
    private val wording = mockk<SchoolImportWordingSource>()
    private val requested = mutableListOf<String>()

    @Before
    fun setUp() {
        server.start()
        dir = Files.createTempDirectory("school-importer").toFile()
        store = SchoolConnectionStore(SchoolTestSupport.preferences(dir))
        store.put(SchoolTestSupport.stored(baseUrl = server.url("/").toString().trimEnd('/')))
        coEvery { users.getCurrentUserId() } returns "alice"
        coEvery { users.getUserById("alice") } returns
            User(id = "alice", email = "alice@example.com", name = "Alice", role = "mom", colorCode = "")
        coEvery { writer.existing(any()) } returns emptyMap()
        coEvery { wording.forChild(any(), any()) } returns object : SchoolImportWording {
            override val schoolHours = "Anna at school"
            override fun dayOff(description: String, type: SchoolDayType) = "Anna: no school"
        }
    }

    @After
    fun tearDown() {
        server.shutdown()
        dir.deleteRecursively()
    }

    @Test
    fun `an update fetches four weeks and the events, records what it made and marks the connection`() = runTest {
        server.dispatcher = serving(eventsStatus = 403)
        val plan = slot<SchoolImportPlan>()
        coEvery { writer.apply(capture(plan)) } answers {
            val failed = plan.captured.creates.first().id
            SchoolImportCounts(created = plan.captured.creates.size - 1, updated = 0, deleted = 0, setOf(failed))
        }

        val result = importer().sync("conn1")

        assertEquals(
            listOf("2020-03-02", "2020-03-09", "2020-03-16", "2020-03-23").map { "/api/3/timetable/actual?date=$it" } +
                "/api/3/events?from=2020-03-02",
            requested
        )
        // Five school days in the week of 2 March; the others answered with that week again and
        // were dropped, and the events right is missing, which imports nothing and fails nothing.
        assertEquals(5, plan.captured.creates.size)
        assertTrue(plan.captured.creates.all { it.eventType == "school" && it.familyId == "alice__bob" })
        assertEquals(SchoolSyncResult.Success(created = 4, updated = 0, deleted = 0), result)

        val stored = checkNotNull(store.get("alice", "conn1"))
        assertEquals(SchoolConnectionStatus.OK, stored.connection.status)
        assertEquals(clock.millis(), stored.connection.lastSuccessAtMillis)
        val failed = plan.captured.creates.first().id
        assertFalse(failed in stored.ledger, "a create that failed is tried again next time")
        assertEquals(4, stored.ledger.values.count { it.kind == SchoolImportKind.HOURS })
    }

    @Test
    fun `a server error marks the connection and changes nothing in the calendar`() = runTest {
        server.dispatcher = serving(timetableStatus = 500)

        val result = importer().sync("conn1")

        assertEquals(SchoolSyncResult.Failed(SchoolSyncFailure.SERVER), result)
        assertEquals(SchoolConnectionStatus.ERROR, store.get("alice", "conn1")?.connection?.status)
    }

    @Test
    fun `a connection waiting for its password makes no request`() = runTest {
        store.update("alice", "conn1") {
            it.copy(connection = it.connection.copy(status = SchoolConnectionStatus.NEEDS_PASSWORD))
        }

        assertEquals(SchoolSyncResult.NeedsPassword, importer().sync("conn1"))
        assertEquals(0, server.requestCount)
    }

    private fun importer(): SchoolImporter {
        val client = BakalariClient(OkHttpClient(), requireHttps = false, io = Dispatchers.IO, now = clock::millis)
        return SchoolImporter(
            authorizer = BakalariAuthorizer(client, store, clock::millis),
            client = client,
            store = store,
            userRepository = users,
            collaborators = SchoolImporter.Collaborators(writer, wording),
            clock = clock
        )
    }

    private fun serving(timetableStatus: Int = 200, eventsStatus: Int = 200) = object : Dispatcher() {
        override fun dispatch(request: RecordedRequest): MockResponse {
            val path = request.path.orEmpty()
            synchronized(requested) { requested += path }
            return when {
                path.startsWith("/api/3/timetable/actual") ->
                    MockResponse().setResponseCode(timetableStatus).setBody(SchoolTestSupport.fixture("bezny.json"))
                path.startsWith("/api/3/events") ->
                    MockResponse().setResponseCode(eventsStatus).setBody(SchoolTestSupport.fixture("events.json"))
                else -> MockResponse().setResponseCode(404)
            }
        }
    }
}
