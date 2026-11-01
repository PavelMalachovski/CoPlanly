package com.coparently.app.data.school

import com.coparently.app.data.school.bakalari.BakalariAuthorizer
import com.coparently.app.data.school.bakalari.BakalariClient
import com.coparently.app.domain.model.ChildInfo
import com.coparently.app.domain.model.User
import com.coparently.app.domain.repository.ChildInfoRepository
import com.coparently.app.domain.repository.UserRepository
import com.coparently.app.domain.school.SchoolConnectionStatus
import com.coparently.app.domain.school.SchoolDayType
import com.coparently.app.domain.school.SchoolImportKind
import com.coparently.app.domain.school.SchoolImportPlan
import com.coparently.app.domain.school.SchoolImportWording
import com.coparently.app.domain.school.SchoolSyncFailure
import com.coparently.app.domain.school.SchoolSyncResult
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpStatusCode
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDateTime
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
 * One school update end to end on the JVM (MON-8): a real client and authorizer against an
 * in-memory server (Ktor's mock engine since MON-24, OkHttp's MockWebServer before), the real
 * store, and the calendar side mocked — what it fetches, what it records, and how it marks the
 * connection.
 */
class SchoolImporterTest {

    /** What the school answers a path with: a status and a body. */
    private var school: (String) -> Pair<Int, String> = { 404 to "" }
    private lateinit var dir: File
    private lateinit var store: SchoolConnectionStore
    private val clock = Clock.fixed(Instant.parse("2020-03-02T06:00:00Z"), ZoneOffset.UTC)
    private val users = mockk<UserRepository>()
    private val writer = mockk<SchoolEventWriter>()
    private val wording = mockk<SchoolImportWordingSource>()
    private val children = mockk<ChildInfoRepository>()
    private val requested = mutableListOf<String>()

    @Before
    fun setUp() {
        dir = Files.createTempDirectory("school-importer").toFile()
        store = SchoolConnectionStore(SchoolTestSupport.preferences(dir))
        store.put(SchoolTestSupport.stored(baseUrl = BASE))
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
        dir.deleteRecursively()
    }

    @Test
    fun `an update fetches four weeks and the events, records what it made and marks the connection`() = runTest {
        school = serving(eventsStatus = 403)
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

    // ---- a connection that names no family (isolation audit, October 2026) --------------------

    @Test
    fun `a connection of no family takes the parent's only family, and keeps it`() = runTest {
        connectedWithoutFamily(coParents = listOf("bob"), childFamily = null)
        school = serving(eventsStatus = 403)
        val plan = slot<SchoolImportPlan>()
        coEvery { writer.apply(capture(plan)) } answers { SchoolImportCounts(plan.captured.creates.size, 0, 0) }

        importer().sync("conn1")

        assertTrue(plan.captured.creates.isNotEmpty())
        assertTrue(plan.captured.creates.all { it.familyId == "alice__bob" })
        assertEquals("alice__bob", store.get("alice", "conn1")?.connection?.familyId)
    }

    @Test
    fun `with two families a connection of no family takes its child's family, never the one on screen`() =
        runTest {
            // Carol's family is on screen; the child is Bob's family's.
            connectedWithoutFamily(coParents = listOf("bob", "carol"), onScreen = "carol", childFamily = "alice__bob")
            school = serving(eventsStatus = 403)
            val plan = slot<SchoolImportPlan>()
            coEvery { writer.apply(capture(plan)) } answers { SchoolImportCounts(plan.captured.creates.size, 0, 0) }

            importer().sync("conn1")

            assertTrue(plan.captured.creates.isNotEmpty())
            assertTrue(plan.captured.creates.all { it.familyId == "alice__bob" })
            assertEquals("alice__bob", store.get("alice", "conn1")?.connection?.familyId)
        }

    @Test
    fun `with two families and nothing naming one, nothing is fetched or written`() = runTest {
        connectedWithoutFamily(coParents = listOf("bob", "carol"), onScreen = "carol", childFamily = null)

        val result = importer().sync("conn1")

        assertEquals(SchoolSyncResult.NeedsFamily, result)
        assertEquals(0, requested.size)
        coVerify(exactly = 0) { writer.apply(any()) }
        assertEquals(null, store.get("alice", "conn1")?.connection?.familyId)
    }

    @Test
    fun `without a co-parent a connection of no family imports into no family`() = runTest {
        connectedWithoutFamily(coParents = emptyList(), childFamily = null)
        school = serving(eventsStatus = 403)
        val plan = slot<SchoolImportPlan>()
        coEvery { writer.apply(capture(plan)) } answers { SchoolImportCounts(plan.captured.creates.size, 0, 0) }

        importer().sync("conn1")

        assertTrue(plan.captured.creates.all { it.familyId == null })
        assertEquals(null, store.get("alice", "conn1")?.connection?.familyId)
    }

    /**
     * The connection made while Alice had no co-parent, now with [coParents] and [onScreen]'s family
     * on screen, for a child whose record names [childFamily].
     */
    private fun connectedWithoutFamily(
        coParents: List<String>,
        onScreen: String? = coParents.firstOrNull(),
        childFamily: String?
    ) {
        store.put(SchoolTestSupport.stored(baseUrl = BASE, familyId = null))
        coEvery { users.getUserById("alice") } returns User(
            id = "alice",
            email = "alice@example.com",
            name = "Alice",
            role = "mom",
            colorCode = "",
            partnerId = onScreen,
            partnerIds = coParents
        )
        val at = LocalDateTime(2020, 1, 1, 0, 0)
        coEvery { children.getChildInfoById("child-1") } returns ChildInfo(
            id = "child-1",
            childName = "Anna",
            dateOfBirth = null,
            createdAt = at,
            updatedAt = at,
            familyId = childFamily
        )
    }

    @Test
    fun `a server error marks the connection and changes nothing in the calendar`() = runTest {
        school = serving(timetableStatus = 500)

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
        assertEquals(0, requested.size)
    }

    private fun importer(): SchoolImporter {
        val http = HttpClient(
            MockEngine { request ->
                val path = request.url.encodedPathAndQuery
                synchronized(requested) { requested += path }
                val (status, body) = school(path)
                respond(body, HttpStatusCode.fromValue(status))
            }
        )
        val client = BakalariClient(http)
        return SchoolImporter(
            authorizer = BakalariAuthorizer(client, store, clock::millis),
            client = client,
            store = store,
            userRepository = users,
            collaborators = SchoolImporter.Collaborators(writer, wording, children),
            clock = clock
        )
    }

    private fun serving(timetableStatus: Int = 200, eventsStatus: Int = 200): (String) -> Pair<Int, String> = { path ->
        when {
            path.startsWith("/api/3/timetable/actual") -> timetableStatus to SchoolTestSupport.fixture("bezny.json")
            path.startsWith("/api/3/events") -> eventsStatus to SchoolTestSupport.fixture("events.json")
            else -> 404 to ""
        }
    }

    private companion object {
        const val BASE = "https://skola.example.cz"
    }
}
