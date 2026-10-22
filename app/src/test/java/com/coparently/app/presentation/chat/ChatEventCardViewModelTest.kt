package com.coparently.app.presentation.chat

import com.coparently.app.data.local.dao.EventDao
import com.coparently.app.data.local.entity.EventEntity
import com.coparently.app.data.remote.firebase.FirestoreEventDataSource
import com.coparently.app.data.repository.EventRepositoryImpl
import com.coparently.app.data.sync.EventDocument
import com.coparently.app.data.sync.Tombstone
import com.coparently.app.domain.events.EventAcceptance
import com.coparently.app.domain.events.EventTimestamp
import com.coparently.app.domain.model.Event
import com.coparently.app.domain.repository.EventRepository
import com.coparently.app.domain.repository.UserRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test
import java.time.LocalDateTime
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull

/**
 * [ChatEventCardViewModel]: what a tap on a chat card about an event opens.
 *
 * Four states — the event's preview, the inbox while it waits on this parent, and "no longer
 * exists" for one that is gone or tombstoned — plus the server read for an event the thread
 * announced before the sync brought it to this phone. Tombstones are tested through the real
 * [EventRepositoryImpl] over a mocked DAO and Firestore source, because that is where a deletion
 * is recognised: a mocked repository answering null would only restate "missing".
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ChatEventCardViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private lateinit var eventRepository: EventRepository
    private lateinit var userRepository: UserRepository

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        eventRepository = mockk {
            coEvery { getEventById(any()) } returns null
            coEvery { fetchRemoteEvent(any()) } returns null
        }
        userRepository = mockk {
            coEvery { getCurrentUserId() } returns ME
        }
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    // ---- the rule -------------------------------------------------------------

    @Test
    fun `nothing found is missing`() {
        assertEquals(EventCardOutcome.Missing, EventCardOutcome.of(null, ME))
    }

    @Test
    fun `the co-parent's event waiting on my answer opens the inbox, even with its creator unknown`() {
        assertEquals(
            EventCardOutcome.AwaitingMe,
            EventCardOutcome.of(event(createdBy = CO_PARENT, acceptance = EventAcceptance.PENDING), ME)
        )
        // The inbox lists a pending event whatever its creator, unless it is the reader's own.
        assertEquals(
            EventCardOutcome.AwaitingMe,
            EventCardOutcome.of(event(createdBy = null, acceptance = EventAcceptance.PENDING), ME)
        )
    }

    @Test
    fun `every other event that exists is previewed`() {
        val previewed = listOf(
            event(createdBy = CO_PARENT),
            event(createdBy = ME, acceptance = EventAcceptance.PENDING),
            event(createdBy = CO_PARENT, acceptance = EventAcceptance.ACCEPTED),
            event(createdBy = CO_PARENT, acceptance = EventAcceptance.DECLINED),
            event(createdBy = ME, acceptance = EventAcceptance.DECLINED)
        )

        previewed.forEach { event ->
            assertEquals(EventCardOutcome.Preview(event), EventCardOutcome.of(event, ME), "${event.acceptance}")
        }
    }

    @Test
    fun `before the reader is known nothing is sent to the inbox`() {
        // The inbox lists nothing for an unknown uid either, so sending the parent there would
        // open an empty screen.
        val pending = event(createdBy = CO_PARENT, acceptance = EventAcceptance.PENDING)

        assertEquals(EventCardOutcome.Preview(pending), EventCardOutcome.of(pending, null))
    }

    // ---- the lookup -----------------------------------------------------------

    @Test
    fun `an event on this phone opens its preview, without asking the server`() = runTest {
        val event = event(createdBy = CO_PARENT)
        coEvery { eventRepository.getEventById(ID) } returns event
        val viewModel = viewModel()

        viewModel.open(ID)
        advanceUntilIdle()

        assertEquals(EventCardOutcome.Preview(event), viewModel.outcome.value)
        coVerify(exactly = 0) { eventRepository.fetchRemoteEvent(any()) }
    }

    @Test
    fun `an event that waits on my answer opens the inbox`() = runTest {
        coEvery { eventRepository.getEventById(ID) } returns
            event(createdBy = CO_PARENT, acceptance = EventAcceptance.PENDING)
        val viewModel = viewModel()

        viewModel.open(ID)
        advanceUntilIdle()

        assertEquals(EventCardOutcome.AwaitingMe, viewModel.outcome.value)
    }

    @Test
    fun `an event the thread announced before the sync brought it here is read from the server`() = runTest {
        // The chat is mirrored in realtime and events are not: "New event" routinely arrives first.
        val event = event(createdBy = CO_PARENT)
        coEvery { eventRepository.fetchRemoteEvent(ID) } returns event
        val viewModel = viewModel()

        viewModel.open(ID)
        advanceUntilIdle()

        assertEquals(EventCardOutcome.Preview(event), viewModel.outcome.value)
    }

    @Test
    fun `an event neither this phone nor the server has is missing`() = runTest {
        val viewModel = viewModel()

        viewModel.open(ID)
        advanceUntilIdle()

        assertEquals(EventCardOutcome.Missing, viewModel.outcome.value)
    }

    @Test
    fun `a lookup that fails says there is nothing to open rather than ignoring the tap`() = runTest {
        coEvery { eventRepository.getEventById(ID) } throws IllegalStateException("database closed")
        val viewModel = viewModel()

        viewModel.open(ID)
        advanceUntilIdle()

        assertEquals(EventCardOutcome.Missing, viewModel.outcome.value)
    }

    @Test
    fun `dismissing closes what was opened`() = runTest {
        coEvery { eventRepository.getEventById(ID) } returns event(createdBy = CO_PARENT)
        val viewModel = viewModel()
        viewModel.open(ID)
        advanceUntilIdle()

        viewModel.dismiss()

        assertNull(viewModel.outcome.value)
    }

    @Test
    fun `a second tap replaces a lookup still in flight`() = runTest {
        val event = event(createdBy = CO_PARENT)
        coEvery { eventRepository.fetchRemoteEvent("slow") } coAnswers {
            delay(SLOW_LOOKUP_MS)
            null
        }
        coEvery { eventRepository.getEventById(ID) } returns event
        val viewModel = viewModel()

        viewModel.open("slow")
        runCurrent()
        viewModel.open(ID)
        advanceUntilIdle()

        // Had the first lookup kept running, its "missing" would have landed on top of this.
        assertEquals(EventCardOutcome.Preview(event), viewModel.outcome.value)
    }

    // ---- tombstones, through the real repository ------------------------------

    @Test
    fun `an event deleted on this phone is missing, though the server still has it`() = runTest {
        // The delete is still in the outbox: Room keeps the row as a pending tombstone and the
        // server's copy is alive until it lands. Neither may bring the event back.
        val eventDao = mockk<EventDao>(relaxed = true) {
            coEvery { getEventById(ID) } returns entity().copy(deletedAtMillis = DELETED_AT)
        }
        val firestore = mockk<FirestoreEventDataSource> {
            coEvery { getEventById(ID) } returns EventDocument.uploadDocument(entity(), AUDIENCE)
        }
        val viewModel = viewModelOver(eventDao, firestore)

        viewModel.open(ID)
        advanceUntilIdle()

        assertEquals(EventCardOutcome.Missing, viewModel.outcome.value)
        coVerify(exactly = 0) { eventDao.insertEvent(any()) }
    }

    @Test
    fun `an event the co-parent deleted is missing, and is not put back on this phone`() = runTest {
        val eventDao = mockk<EventDao>(relaxed = true) {
            coEvery { getEventById(ID) } returns null
        }
        val firestore = mockk<FirestoreEventDataSource> {
            coEvery { getEventById(ID) } returns
                EventDocument.uploadDocument(entity(), AUDIENCE) + Tombstone.fields(DELETED_AT, CO_PARENT)
        }
        val viewModel = viewModelOver(eventDao, firestore)

        viewModel.open(ID)
        advanceUntilIdle()

        assertEquals(EventCardOutcome.Missing, viewModel.outcome.value)
        coVerify(exactly = 0) { eventDao.insertEvent(any()) }
    }

    @Test
    fun `a live event the sync has not brought yet is stored and previewed`() = runTest {
        // The control for the two above: the same document without a tombstone opens.
        val eventDao = mockk<EventDao>(relaxed = true) {
            coEvery { getEventById(ID) } returns null
        }
        val firestore = mockk<FirestoreEventDataSource> {
            coEvery { getEventById(ID) } returns EventDocument.uploadDocument(entity(), AUDIENCE)
        }
        val viewModel = viewModelOver(eventDao, firestore)

        viewModel.open(ID)
        advanceUntilIdle()

        val preview = assertIs<EventCardOutcome.Preview>(viewModel.outcome.value)
        assertEquals(ID, preview.event.id)
        assertEquals("Dentist", preview.event.title)
        coVerify(exactly = 1) { eventDao.insertEvent(any()) }
    }

    private fun viewModel() = ChatEventCardViewModel(eventRepository, userRepository)

    /** The view model over the production repository, so its tombstone rules are the ones tested. */
    private fun viewModelOver(eventDao: EventDao, firestore: FirestoreEventDataSource) =
        ChatEventCardViewModel(
            eventRepository = EventRepositoryImpl(
                eventDao = eventDao,
                userDao = mockk(relaxed = true),
                firebaseAuthService = mockk(relaxed = true),
                firestoreEventDataSource = firestore,
                activityAnnouncer = mockk(relaxed = true),
                eventVersionRecorder = mockk(relaxed = true),
                fcmService = mockk(relaxed = true)
            ),
            userRepository = userRepository
        )

    private fun event(createdBy: String?, acceptance: EventAcceptance = EventAcceptance.NOT_REQUIRED) = Event(
        id = ID,
        title = "Dentist",
        description = "Bring the insurance card.",
        startDateTime = AT,
        endDateTime = AT.plusHours(1),
        eventType = "doctor",
        parentOwner = "mom",
        createdAt = AT,
        updatedAt = AT,
        createdByFirebaseUid = createdBy,
        acceptance = acceptance
    )

    private fun entity() = EventEntity(
        id = ID,
        title = "Dentist",
        startDateTime = AT,
        endDateTime = AT.plusHours(1),
        eventType = "doctor",
        parentOwner = "mom",
        createdAt = AT,
        updatedAt = AT,
        updatedAtMillis = EventTimestamp.ofWallClock(AT),
        createdByFirebaseUid = CO_PARENT,
        sharedWithJson = """["$ME","$CO_PARENT"]"""
    )

    private companion object {
        const val ID = "e1"
        const val ME = "uid-alice"
        const val CO_PARENT = "uid-bob"
        const val DELETED_AT = 1_790_000_000_000L
        const val SLOW_LOOKUP_MS = 10_000L
        val AUDIENCE = listOf(ME, CO_PARENT)
        val AT: LocalDateTime = LocalDateTime.of(2026, 10, 20, 15, 30)
    }
}
