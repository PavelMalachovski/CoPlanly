package com.coparently.app.data.sync

import com.coparently.app.data.local.dao.EventDao
import com.coparently.app.data.local.entity.EventEntity
import com.coparently.app.data.remote.google.CalendarEvents
import com.coparently.app.data.remote.google.CredentialProvider
import com.coparently.app.data.remote.google.GoogleAccessException
import com.coparently.app.data.remote.google.GoogleAccessFailure
import com.coparently.app.data.remote.google.GoogleCalendarApi
import com.coparently.app.domain.model.User
import com.coparently.app.domain.repository.UserRepository
import com.google.api.client.auth.oauth2.Credential
import com.google.api.client.util.DateTime
import com.google.api.services.calendar.model.EventDateTime
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import com.google.api.services.calendar.model.Event as GoogleEvent

/**
 * [CalendarSyncRepository] used to read the owner slot from `ParentsSource`, in
 * `presentation/`, the tree's only data -> presentation edge. It now reads the same two calls -
 * [UserRepository.getCurrentUserId] and [UserRepository.getUserById] - directly, since
 * `UserRepository` is a domain interface this data-layer class may depend on.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class CalendarSyncRepositoryTest {

    private val eventDao: EventDao = mockk(relaxed = true)
    private val googleCalendarApi: GoogleCalendarApi = mockk()
    private var credentialProvider: CredentialProvider = GrantingCredentials(mockk())
    private val userRepository: UserRepository = mockk()

    init {
        // Nothing imported before, unless a test says so: a relaxed DAO would answer with a mock row.
        coEvery { eventDao.getEventById(any()) } returns null
    }

    private fun repository() = CalendarSyncRepository(
        eventDao,
        googleCalendarApi,
        credentialProvider,
        userRepository,
        mockk(relaxed = true)
    )

    @Test
    fun `an imported event is stamped with the signed-in user's slot, read through UserRepository`() =
        runTest {
            val credential = mockk<Credential>()
            credentialProvider = GrantingCredentials(credential)
            coEvery { userRepository.getCurrentUserId() } returns "u1"
            coEvery { userRepository.getUserById("u1") } returns User(
                id = "u1",
                email = "pavel@example.com",
                name = "Pavel",
                role = "dad",
                colorCode = "#2196F3"
            )
            val googleEvent = GoogleEvent().apply {
                id = "e1"
                summary = "Pickup"
            }
            every {
                googleCalendarApi.listEvents(
                    credential = any(),
                    calendarId = any(),
                    timeMin = any(),
                    timeMax = any(),
                    limit = any()
                )
            } returns imported(googleEvent)
            val inserted = slot<List<EventEntity>>()
            coEvery { eventDao.insertEvents(capture(inserted)) } returns Unit

            val results = repository().syncFromGoogle().toList()

            assertTrue(results.last() is SyncResult.Success)
            assertEquals("dad", inserted.captured.single().parentOwner)
            // And private: a Google Calendar is a personal calendar, so an import stays on the
            // device that pulled it rather than being published to a co-parent. It is also
            // what makes "which family does this import belong to" a question with no need of
            // an answer once a person has several. Owner decision, Aug 2026.
            assertTrue(
                inserted.captured.single().isPrivate,
                "a Google Calendar import must never reach the co-parent"
            )
        }

    @Test
    fun `syncFromGoogle throws rather than stamping an event when nobody is signed in`() =
        runTest {
            val credential = mockk<Credential>()
            credentialProvider = GrantingCredentials(credential)
            coEvery { userRepository.getCurrentUserId() } returns null

            val results = repository().syncFromGoogle().toList()

            val error = results.last()
            assertTrue(error is SyncResult.Error)
            assertEquals(SyncFailure.NOT_SIGNED_IN_APP, (error as SyncResult.Error).reason)
            verify(exactly = 0) {
                googleCalendarApi.listEvents(
                    credential = any(),
                    calendarId = any(),
                    timeMin = any(),
                    timeMax = any(),
                    limit = any()
                )
            }
            coVerify(exactly = 0) { eventDao.insertEvents(any()) }
        }

    @Test
    fun `a truncated import says so, instead of reading like a finished one`() = runTest {
        // The whole point of CQ-7: the old import stopped at the 50th event and reported the same
        // "Synced N events" a complete one did, so a user with a full calendar was told it had
        // finished. The result carries the fact; `SyncStateTextTest` pins that the two facts are
        // worded differently.
        val credential = mockk<Credential>()
        credentialProvider = GrantingCredentials(credential)
        coEvery { userRepository.getCurrentUserId() } returns "u1"
        coEvery { userRepository.getUserById("u1") } returns User(
            id = "u1", email = "p@example.com", name = "Pavel", role = "dad", colorCode = "#2196F3"
        )
        coEvery { eventDao.insertEvents(any()) } returns Unit
        val event = GoogleEvent().apply {
            id = "e1"
            summary = "Pickup"
        }

        every {
            googleCalendarApi.listEvents(any(), any(), any(), any(), any())
        } returns imported(event, truncated = true)
        val cutShort = repository().syncFromGoogle().toList().last()

        every {
            googleCalendarApi.listEvents(any(), any(), any(), any(), any())
        } returns imported(event, truncated = false)
        val complete = repository().syncFromGoogle().toList().last()

        assertTrue(cutShort is SyncResult.Success)
        assertTrue(complete is SyncResult.Success)
        assertTrue((cutShort as SyncResult.Success).truncated, "a cut-short import must say so")
        assertFalse((complete as SyncResult.Success).truncated, "a complete import must not")
    }

    @Test
    fun `the window the import actually read is reported, not left to be guessed`() = runTest {
        val credential = mockk<Credential>()
        credentialProvider = GrantingCredentials(credential)
        coEvery { userRepository.getCurrentUserId() } returns "u1"
        coEvery { userRepository.getUserById("u1") } returns User(
            id = "u1", email = "p@example.com", name = "Pavel", role = "dad", colorCode = "#2196F3"
        )
        coEvery { eventDao.insertEvents(any()) } returns Unit
        every {
            googleCalendarApi.listEvents(any(), any(), any(), any(), any())
        } returns CalendarEvents(
            events = listOf(
                GoogleEvent().apply {
                    id = "e1"
                    summary = "Pickup"
                }
            ),
            truncated = false,
            from = LocalDateTime.of(2026, 8, 24, 0, 0),
            until = LocalDateTime.of(2027, 8, 24, 0, 0)
        )

        val success = repository().syncFromGoogle().toList().last() as SyncResult.Success

        assertEquals(LocalDate.of(2026, 8, 24), success.from, "start of the window")
        assertEquals(LocalDate.of(2027, 8, 24), success.until, "end of the window")
    }

    @Test
    fun `an all-day import ends on its last day, not on Google's exclusive end date`() = runTest {
        // Google closes a one-day event on the 14th with `end.date` = the 15th. Stored as the 15th
        // at midnight it overlapped the 15th in every day query and was drawn a day too long.
        val credential = mockk<Credential>()
        credentialProvider = GrantingCredentials(credential)
        coEvery { userRepository.getCurrentUserId() } returns "u1"
        coEvery { userRepository.getUserById("u1") } returns User(
            id = "u1", email = "p@example.com", name = "Pavel", role = "dad", colorCode = "#2196F3"
        )
        every {
            googleCalendarApi.listEvents(any(), any(), any(), any(), any())
        } returns imported(
            allDay("one-day", from = "2026-09-14", toExclusive = "2026-09-15"),
            allDay("two-days", from = "2026-09-14", toExclusive = "2026-09-16")
        )
        val inserted = slot<List<EventEntity>>()
        coEvery { eventDao.insertEvents(capture(inserted)) } returns Unit

        repository().syncFromGoogle().toList()

        val byId = inserted.captured.associateBy { it.id }
        assertEquals(LocalDateTime.of(2026, 9, 14, 0, 0), byId.getValue("one-day").startDateTime)
        assertEquals(LocalDateTime.of(2026, 9, 14, 23, 59), byId.getValue("one-day").endDateTime)
        assertEquals(LocalDateTime.of(2026, 9, 15, 23, 59), byId.getValue("two-days").endDateTime)
    }

    @Test
    fun `a connection Google ended asks for a reconnect and imports nothing`() = runTest {
        // It used to say "Not connected to Google" — about a connection the row still showed.
        credentialProvider = RefusingCredentials(GoogleAccessFailure.NEEDS_RECONNECT)
        signedIn()

        val result = repository().syncFromGoogle().toList().last()

        assertEquals(SyncResult.Error(SyncFailure.RECONNECT), result)
        coVerify(exactly = 0) { eventDao.insertEvents(any()) }
    }

    @Test
    fun `a renewal that could not reach Google is told apart from no connection at all`() = runTest {
        signedIn()

        credentialProvider = RefusingCredentials(GoogleAccessFailure.UNAVAILABLE)
        val unreachable = repository().syncFromGoogle().toList().last()
        credentialProvider = RefusingCredentials(GoogleAccessFailure.NOT_CONNECTED)
        val notConnected = repository().syncFromGoogle().toList().last()

        assertEquals(SyncResult.Error(SyncFailure.GOOGLE_UNREACHABLE), unreachable)
        assertEquals(SyncResult.Error(SyncFailure.NOT_SIGNED_IN_GOOGLE), notConnected)
    }

    // ---- families (isolation audit, October 2026) -------------------------------------------

    @Test
    fun `a new import takes the parent's only family, and none while they have two`() = runTest {
        every { googleCalendarApi.listEvents(any(), any(), any(), any(), any()) } returns
            imported(googleEvent("e1", "Dentist"))
        val inserted = slot<List<EventEntity>>()
        coEvery { eventDao.insertEvents(capture(inserted)) } returns Unit

        signedIn(partnerId = "u2", partnerIds = listOf("u2"))
        repository().syncFromGoogle().toList()
        assertEquals("u1__u2", inserted.captured.single().familyId)

        // Two families: nothing says which one a personal calendar's event is about, and the
        // family on screen is not an answer.
        signedIn(partnerId = "u3", partnerIds = listOf("u2", "u3"))
        repository().syncFromGoogle().toList()
        assertNull(inserted.captured.single().familyId)
    }

    @Test
    fun `a re-import keeps what the app set, and takes only what Google changed`() = runTest {
        // The parent shared the import in the family with u2, it synced, and then the family with
        // u3 came on screen. A re-import used to make it private, unsynced and u3's.
        signedIn(partnerId = "u3", partnerIds = listOf("u2", "u3"))
        val shared = EventEntity(
            id = "e1",
            title = "Dentist",
            startDateTime = LocalDateTime.of(2026, 9, 14, 9, 0),
            endDateTime = LocalDateTime.of(2026, 9, 14, 10, 0),
            eventType = "medical",
            parentOwner = "dad",
            createdAt = LocalDateTime.of(2026, 9, 1, 9, 0),
            updatedAt = LocalDateTime.of(2026, 9, 1, 9, 0),
            updatedAtMillis = 1L,
            syncedToFirestore = true,
            createdByFirebaseUid = "u1",
            isPrivate = false,
            familyId = "u1__u2"
        )
        coEvery { eventDao.getEventById("e1") } returns shared
        every { googleCalendarApi.listEvents(any(), any(), any(), any(), any()) } returns
            imported(googleEvent("e1", "Dentist, moved"))
        val inserted = slot<List<EventEntity>>()
        coEvery { eventDao.insertEvents(capture(inserted)) } returns Unit

        repository().syncFromGoogle().toList()

        val written = inserted.captured.single()
        assertEquals("Dentist, moved", written.title)
        assertEquals("u1__u2", written.familyId)
        assertFalse(written.isPrivate)
        assertTrue(written.syncedToFirestore)
        assertEquals("medical", written.eventType)
    }

    @Test
    fun `a re-import Google changed nothing in writes nothing`() = runTest {
        signedIn()
        every { googleCalendarApi.listEvents(any(), any(), any(), any(), any()) } returns
            imported(googleEvent("e1", "Pickup"))
        val first = slot<List<EventEntity>>()
        coEvery { eventDao.insertEvents(capture(first)) } returns Unit
        repository().syncFromGoogle().toList()
        // The same event, as the first import wrote it, a moment later.
        coEvery { eventDao.getEventById("e1") } returns first.captured.single().copy(familyId = "u1__u2")

        val result = repository().syncFromGoogle().toList().last()

        assertTrue(result is SyncResult.Success)
        coVerify(exactly = 1) { eventDao.insertEvents(any()) }
    }

    /** A timed Google event, fixed in time so two imports of it read the same. */
    private fun googleEvent(id: String, title: String) = GoogleEvent().apply {
        this.id = id
        summary = title
        start = EventDateTime().setDateTime(DateTime(FIXED_START_MILLIS))
    }

    private fun signedIn(partnerId: String?, partnerIds: List<String>) {
        coEvery { userRepository.getCurrentUserId() } returns "u1"
        coEvery { userRepository.getUserById("u1") } returns User(
            id = "u1",
            email = "p@example.com",
            name = "Pavel",
            role = "dad",
            colorCode = "#2196F3",
            partnerId = partnerId,
            partnerIds = partnerIds
        )
    }

    private fun signedIn() {
        coEvery { userRepository.getCurrentUserId() } returns "u1"
        coEvery { userRepository.getUserById("u1") } returns User(
            id = "u1", email = "p@example.com", name = "Pavel", role = "dad", colorCode = "#2196F3"
        )
    }

    private fun allDay(id: String, from: String, toExclusive: String) = GoogleEvent().apply {
        this.id = id
        summary = "Holiday"
        start = EventDateTime().setDate(DateTime(from))
        end = EventDateTime().setDate(DateTime(toExclusive))
    }

    private fun imported(vararg events: GoogleEvent, truncated: Boolean = false) = CalendarEvents(
        events = events.toList(),
        truncated = truncated,
        from = LocalDateTime.of(2026, 8, 24, 0, 0),
        until = LocalDateTime.of(2027, 8, 24, 0, 0)
    )

    private companion object {
        /** A fixed instant; which day it falls on does not matter here. */
        const val FIXED_START_MILLIS = 1_790_000_000_000L
    }
}

/** A connected device whose Google accepts the one [credential] it holds. */
private class GrantingCredentials(private val credential: Credential) : CredentialProvider {
    override suspend fun getCredential(forceRefresh: Boolean): Credential = credential

    override suspend fun <T> withCredential(call: suspend (Credential) -> T): T = call(credential)
}

/** A device with no usable Google connection, for the reason [failure] gives. */
private class RefusingCredentials(private val failure: GoogleAccessFailure) : CredentialProvider {
    override suspend fun getCredential(forceRefresh: Boolean): Credential = throw GoogleAccessException(failure)

    override suspend fun <T> withCredential(call: suspend (Credential) -> T): T =
        throw GoogleAccessException(failure)
}
