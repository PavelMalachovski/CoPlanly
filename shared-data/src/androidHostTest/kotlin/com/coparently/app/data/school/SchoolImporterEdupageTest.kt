@file:OptIn(InternalSharedApi::class)

package com.coparently.app.data.school

import com.coparently.app.data.platform.MemoryTextStore
import com.coparently.app.data.platform.SealedSecurePreferences
import com.coparently.app.data.school.bakalari.BakalariAuthorizer
import com.coparently.app.data.school.bakalari.BakalariClient
import com.coparently.app.data.school.bakalari.BakalariTokens
import com.coparently.app.domain.InternalSharedApi
import com.coparently.app.domain.model.User
import com.coparently.app.domain.repository.ChildInfoRepository
import com.coparently.app.domain.repository.UserRepository
import com.coparently.app.domain.school.ExistingImport
import com.coparently.app.domain.school.SchoolConnection
import com.coparently.app.domain.school.SchoolConnectionStatus
import com.coparently.app.domain.school.SchoolDay
import com.coparently.app.domain.school.SchoolDayType
import com.coparently.app.domain.school.SchoolEvent
import com.coparently.app.domain.school.SchoolEventTime
import com.coparently.app.domain.school.SchoolImportPlan
import com.coparently.app.domain.school.SchoolImportWording
import com.coparently.app.domain.school.SchoolStudent
import com.coparently.app.domain.school.SchoolSyncResult
import com.coparently.app.domain.school.SchoolSystem
import com.coparently.app.domain.school.SchoolTask
import com.coparently.app.domain.school.SchoolWeek
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpStatusCode
import io.mockk.coEvery
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import org.junit.Before
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Instant

/** [SchoolImporter] reading EduPage and Bakaláři through their [SchoolSource]s. */
class SchoolImporterEdupageTest {

    private val store = SchoolConnectionStore(SealedSecurePreferences(MemoryTextStore()))
    private val users = mockk<UserRepository>()
    private val writer = mockk<SchoolEventWriter>()
    private val wording = mockk<SchoolImportWordings>()
    private val children = mockk<ChildInfoRepository>()
    private val clock = object : Clock {
        override fun now(): Instant = Instant.parse("2026-11-12T06:00:00Z")
    }
    private val today = LocalDate(2026, 11, 12)
    private val friday = LocalDate(2026, 11, 13)

    private val fake = object : SchoolSource {
        var calls = 0

        override suspend fun weeks(uid: String, connectionId: String, monday: LocalDate): List<SchoolWeek> {
            calls++
            return listOf(
                SchoolWeek(
                    monday,
                    listOf(
                        SchoolDay(today, SchoolDayType.WORK_DAY, "", false, LocalTime(8, 0), LocalTime(13, 0)),
                        SchoolDay(friday, SchoolDayType.HOLIDAY, "Rector's day", false, null, null)
                    )
                )
            )
        }

        override suspend fun events(uid: String, connectionId: String, today: LocalDate): List<SchoolEvent> {
            calls++
            val time = SchoolEventTime(false, LocalDateTime(friday, LocalTime(9, 0)), null, "key")
            return listOf(SchoolEvent("e1", "Trip", null, listOf(time), emptyList(), emptyList()))
        }

        override suspend fun tasks(uid: String, connectionId: String, today: LocalDate): List<SchoolTask> {
            calls++
            return listOf(SchoolTask("t1", friday, "Math: worksheet", null))
        }
    }

    @Before
    fun setUp() {
        coEvery { users.getCurrentUserId() } returns "alice"
        coEvery { users.getUserById("alice") } returns
            User(id = "alice", email = "alice@example.com", name = "Alice", role = "mom", colorCode = "")
        coEvery { writer.existing(any()) } returns emptyMap()
        coEvery { wording.forChild(any(), any()) } returns object : SchoolImportWording {
            override val schoolHours = "Anna at school"
            override fun dayOff(description: String, type: SchoolDayType) = "Anna: no school"
        }
    }

    private fun edupage(id: String, username: String) = StoredSchoolConnection(
        connection = SchoolConnection(
            id = id,
            baseUrl = "https://zsx.edupage.org",
            username = username,
            schoolName = "School",
            studentName = "Anna",
            childId = "child-1",
            familyId = "alice__bob",
            status = SchoolConnectionStatus.OK,
            lastSuccessAtMillis = null,
            system = SchoolSystem.EDUPAGE
        ),
        ownerUid = "alice",
        student = SchoolStudent("", "Anna", ""),
        tokens = BakalariTokens("", "", 0L),
        edupage = EdupageSecrets("pw", emptyMap(), "-1234")
    )

    private fun importer(requested: MutableList<String> = mutableListOf()): SchoolImporter {
        val client = BakalariClient(
            HttpClient(
                MockEngine { request ->
                    requested += request.url.encodedPathAndQuery
                    respond("", HttpStatusCode.Forbidden)
                }
            )
        )
        return SchoolImporter.on(
            authorizer = BakalariAuthorizer.on(client, store) { clock.now().toEpochMilliseconds() },
            client = client,
            store = store,
            userRepository = users,
            collaborators = SchoolImporter.Collaborators(writer, wording, children),
            clock = clock,
            zone = TimeZone.UTC,
            edupage = fake
        )
    }

    @Test
    fun `an edupage connection imports hours events tasks and days off`() = runTest {
        store.put(edupage("ep1", "novak"))
        val plan = slot<SchoolImportPlan>()
        coEvery { writer.apply(capture(plan)) } answers { SchoolImportCounts(plan.captured.creates.size, 0, 0) }

        val result = importer().sync("ep1")

        assertEquals(SchoolSyncResult.Success(4, 0, 0), result)
        val titles = plan.captured.creates.map { it.title }.toSet()
        assertEquals(setOf("Anna at school", "Anna: no school", "Trip", "Math: worksheet"), titles)
        assertEquals(3, fake.calls)
    }

    @Test
    fun `a second parent's edupage connection for the same child creates nothing new`() = runTest {
        store.put(edupage("ep1", "novak"))
        val first = slot<SchoolImportPlan>()
        coEvery { writer.apply(capture(first)) } answers { SchoolImportCounts(first.captured.creates.size, 0, 0) }
        importer().sync("ep1")
        val created = first.captured.creates.associateBy { it.id }

        // The other parent signs in with their own username; Room already holds the events.
        store.put(edupage("ep2", "novakova"))
        coEvery { writer.existing(any()) } answers {
            firstArg<Set<String>>().filter { it in created }.associateWith { ExistingImport.Live(created.getValue(it)) }
        }
        val second = slot<SchoolImportPlan>()
        coEvery { writer.apply(capture(second)) } answers { SchoolImportCounts(0, 0, 0) }

        importer().sync("ep2")

        assertTrue(second.captured.creates.isEmpty())
        assertTrue(second.captured.updates.isEmpty())
    }

    @Test
    fun `a source that needs the password is reported as NeedsPassword`() = runTest {
        store.put(edupage("ep1", "novak"))
        val refusing = object : SchoolSource {
            override suspend fun weeks(uid: String, connectionId: String, monday: LocalDate): List<SchoolWeek> =
                throw SchoolSourceFailure.NeedsPassword()

            override suspend fun events(uid: String, connectionId: String, today: LocalDate): List<SchoolEvent> =
                emptyList()

            override suspend fun tasks(uid: String, connectionId: String, today: LocalDate): List<SchoolTask> =
                emptyList()
        }
        val client = BakalariClient(HttpClient(MockEngine { respond("", HttpStatusCode.Forbidden) }))
        val importer = SchoolImporter.on(
            authorizer = BakalariAuthorizer.on(client, store) { clock.now().toEpochMilliseconds() },
            client = client,
            store = store,
            userRepository = users,
            collaborators = SchoolImporter.Collaborators(writer, wording, children),
            clock = clock,
            zone = TimeZone.UTC,
            edupage = refusing
        )

        assertEquals(SchoolSyncResult.NeedsPassword, importer.sync("ep1"))
    }

    @Test
    fun `a bakalari connection still goes through the bakalari source`() = runTest {
        store.put(
            edupage("bk1", "novak").let {
                it.copy(
                    connection = it.connection.copy(
                        baseUrl = "https://skola.bakalari.cz",
                        system = SchoolSystem.BAKALARI
                    ),
                    tokens = BakalariTokens("access-1", "refresh-1", Long.MAX_VALUE),
                    edupage = null
                )
            }
        )
        coEvery { writer.apply(any()) } returns SchoolImportCounts(0, 0, 0)
        val requested = mutableListOf<String>()

        val result = importer(requested).sync("bk1")

        assertEquals(SchoolSyncResult.Success(0, 0, 0), result)
        assertTrue(requested.any { it.startsWith("/api/3/timetable/actual") })
        assertEquals(0, fake.calls)
    }
}
