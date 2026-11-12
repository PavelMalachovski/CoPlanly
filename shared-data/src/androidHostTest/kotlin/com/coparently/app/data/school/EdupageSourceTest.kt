package com.coparently.app.data.school

import com.coparently.app.data.platform.MemoryTextStore
import com.coparently.app.data.platform.SealedSecurePreferences
import com.coparently.app.data.school.bakalari.BakalariTokens
import com.coparently.app.data.school.edupage.EdupageClient
import com.coparently.app.data.school.edupage.EdupageException
import com.coparently.app.data.school.edupage.EdupageSession
import com.coparently.app.data.school.edupage.EdupageSource
import com.coparently.app.domain.school.SchoolConnection
import com.coparently.app.domain.school.SchoolConnectionStatus
import com.coparently.app.domain.school.SchoolStudent
import com.coparently.app.domain.school.SchoolSystem
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.coVerifyOrder
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/** [EdupageSource]: one session per update, signed in again from the stored password when it lapses. */
class EdupageSourceTest {

    private val store = SchoolConnectionStore(SealedSecurePreferences(MemoryTextStore()))
    private val client = mockk<EdupageClient>()
    private val source = EdupageSource(client, store)
    private val monday = LocalDate(2026, 11, 9)

    private val timetable = """{"r":{"ttitems":[]}}"""
    private val timeline = """{"timelineItems":[]}"""

    private fun userhome(userId: String): JsonObject = buildJsonObject { put("userid", userId) }

    private fun session(cookies: Map<String, String>, userId: String = "Ucitel1") =
        EdupageSession(HOST, cookies, "hash", userhome(userId))

    private fun connect() {
        store.put(
            StoredSchoolConnection(
                connection = SchoolConnection(
                    id = "ep1",
                    baseUrl = "https://$HOST",
                    username = "novak",
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
                edupage = EdupageSecrets("hunter2", mapOf("PHPSESSID" to "old"), "-1234")
            )
        )
    }

    private fun cookies() = store.get("alice", "ep1")?.edupage?.cookies

    private fun serveTimetable(session: EdupageSession) {
        coEvery { client.timetableJson(session, any(), any(), any()) } returns timetable
        coEvery { client.timelineJson(session, any()) } returns timeline
    }

    @Test
    fun `an expired cookie signs in again with the stored password and keeps going`() = runTest {
        connect()
        val fresh = session(mapOf("PHPSESSID" to "new"))
        coEvery { client.resume(HOST, mapOf("PHPSESSID" to "old")) } throws EdupageException.SessionExpired()
        coEvery { client.signIn(HOST, "novak", "hunter2") } returns fresh
        serveTimetable(fresh)

        val weeks = source.weeks("alice", "ep1", monday)

        assertTrue(weeks.isNotEmpty())
        coVerify(exactly = 1) { client.signIn(HOST, "novak", "hunter2") }
        assertEquals(SchoolConnectionStatus.OK, store.get("alice", "ep1")?.connection?.status)
    }

    @Test
    fun `the new cookies are stored before the timetable is read`() = runTest {
        connect()
        val fresh = session(mapOf("PHPSESSID" to "new"))
        coEvery { client.resume(any(), any()) } throws EdupageException.SessionExpired()
        coEvery { client.signIn(any(), any(), any()) } returns fresh
        var storedWhenRead: Map<String, String>? = null
        coEvery { client.timetableJson(any(), any(), any(), any()) } answers {
            storedWhenRead = cookies()
            timetable
        }
        coEvery { client.timelineJson(any(), any()) } returns timeline

        source.weeks("alice", "ep1", monday)

        assertEquals(mapOf("PHPSESSID" to "new"), storedWhenRead)
    }

    @Test
    fun `a refused password marks the connection NEEDS_PASSWORD and keeps the password`() = runTest {
        connect()
        coEvery { client.resume(any(), any()) } throws EdupageException.SessionExpired()
        coEvery { client.signIn(any(), any(), any()) } throws EdupageException.BadCredentials()

        assertFailsWith<SchoolSourceFailure.NeedsPassword> { source.weeks("alice", "ep1", monday) }

        val stored = store.get("alice", "ep1")
        assertEquals(SchoolConnectionStatus.NEEDS_PASSWORD, stored?.connection?.status)
        assertEquals("hunter2", stored?.edupage?.password)
    }

    @Test
    fun `a second factor during a background sign-in marks NEEDS_PASSWORD`() = runTest {
        connect()
        coEvery { client.resume(any(), any()) } throws EdupageException.SessionExpired()
        coEvery { client.signIn(any(), any(), any()) } throws EdupageException.SecondFactor(emptyMap(), HOST)

        assertFailsWith<SchoolSourceFailure.NeedsPassword> { source.weeks("alice", "ep1", monday) }

        assertEquals(SchoolConnectionStatus.NEEDS_PASSWORD, store.get("alice", "ep1")?.connection?.status)
        assertEquals("hunter2", store.get("alice", "ep1")?.edupage?.password)
    }

    @Test
    fun `a parent account switches to the connection's child first`() = runTest {
        connect()
        val parent = session(mapOf("PHPSESSID" to "old"), userId = "Rodic77")
        val switched = parent.copy(cookies = mapOf("PHPSESSID" to "switched"))
        coEvery { client.resume(HOST, mapOf("PHPSESSID" to "old")) } returns parent
        coEvery { client.switchChild(parent, "-1234") } returns switched
        serveTimetable(switched)

        source.weeks("alice", "ep1", monday)

        coVerifyOrder {
            client.switchChild(parent, "-1234")
            client.timetableJson(switched, "-1234", monday, LocalDate(2026, 12, 6))
        }
        assertEquals(mapOf("PHPSESSID" to "switched"), cookies())
    }
}

private const val HOST = "zsx.edupage.org"
