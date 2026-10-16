package com.coparently.app.data.repository

import com.coparently.app.data.remote.firebase.FirebaseAuthService
import com.coparently.app.data.remote.firebase.FirestoreRequestDeadlineDataSource
import com.coparently.app.data.requests.RequestDeadlineDocument
import com.coparently.app.domain.changerequests.RequestClock
import com.coparently.app.domain.changerequests.RequestDeadline
import com.coparently.app.domain.changerequests.RequestKind
import com.coparently.app.domain.changerequests.RequestStart
import com.coparently.app.domain.notification.RequestClockScheduler
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.FirebaseFirestoreException.Code
import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.LocalDate

/**
 * [RequestDeadlineRepositoryImpl]: what a request's clock does between the app and Firestore
 * (phase 3a, spec §5.1 and ruling 14).
 *
 * The data source is mocked and the scheduler is a recording one, so these tests state the
 * repository's own rules: the order of a start, the ten seconds a background write may wait, what a
 * refusal means, and how long a failing listener is retried before it reads as "no clocks".
 */
@OptIn(ExperimentalCoroutinesApi::class)
class RequestDeadlineRepositoryImplTest {

    private lateinit var dataSource: FirestoreRequestDeadlineDataSource
    private lateinit var authService: FirebaseAuthService
    private lateinit var scheduler: RecordingScheduler
    private lateinit var repository: RequestDeadlineRepositoryImpl

    /** What happened, in order, across the data source and the scheduler. */
    private val events = mutableListOf<String>()

    private val swapStart = RequestStart(
        kind = RequestKind.SWAP,
        ref = "g-1",
        familyId = FAMILY,
        responderUid = "uidB",
        answerByMillis = ANSWER_BY,
        capMillis = CAP,
        firstDate = LocalDate.of(2026, 5, 16),
        dayCount = 2
    )

    @Before
    fun setUp() {
        dataSource = mockk()
        authService = mockk()
        scheduler = RecordingScheduler(events)
        repository = RequestDeadlineRepositoryImpl(dataSource, authService, scheduler)
        every { authService.getCurrentUser() } returns mockk<FirebaseUser> { every { uid } returns ME }
    }

    // ---- starting a clock --------------------------------------------------------------------

    @Test
    fun `start writes the clock under its derived id, then schedules the deadline and the end`() = runTest {
        coEvery { dataSource.create(any(), any()) } coAnswers { events.add("create") }

        val clock = repository.start(swapStart)

        coVerify(exactly = 1) {
            dataSource.create(SWAP_ID, RequestDeadlineDocument.createFields(swapStart, ME))
        }
        assertEquals(
            "the clock as written: the server has not stamped it yet",
            RequestDeadline(
                id = SWAP_ID,
                kind = RequestKind.SWAP,
                ref = "g-1",
                familyId = FAMILY,
                proposerUid = ME,
                responderUid = "uidB",
                answerByMillis = ANSWER_BY,
                capMillis = CAP,
                createdAtMillis = null,
                firstDate = LocalDate.of(2026, 5, 16),
                dayCount = 2
            ),
            clock
        )
        assertEquals(
            "written first; then the deadline, and the end a day later (the last chance's button)",
            listOf("create", "deadline $SWAP_ID @ $ANSWER_BY", "end $SWAP_ID @ ${ANSWER_BY + DAY}"),
            events
        )
        assertEquals(
            "the end is the clock's own, not a second opinion",
            RequestClock.endMillis(clock!!),
            ANSWER_BY + DAY
        )
    }

    @Test
    fun `a refused or signed-out start writes nothing else and returns null`() = runTest {
        coEvery { dataSource.create(any(), any()) } throws denied()

        assertNull("the rules refused it", repository.start(swapStart))
        assertEquals("a refused clock schedules nothing", emptyList<String>(), events)
        coVerify(exactly = 1) { dataSource.create(any(), any()) }

        every { authService.getCurrentUser() } returns null

        assertNull("nobody is signed in", repository.start(swapStart))
        coVerify(exactly = 1) { dataSource.create(any(), any()) }
        assertEquals(emptyList<String>(), events)
    }

    @Test
    fun `a start the server has not answered after ten seconds still starts the clock`() = runTest {
        coEvery { dataSource.create(any(), any()) } coAnswers { awaitCancellation() }

        val clock = repository.start(swapStart)

        assertNotNull("queued by Firestore, which delivers it later (ruling 14)", clock)
        assertEquals(10_000L, testScheduler.currentTime)
        assertEquals(
            listOf("deadline $SWAP_ID @ $ANSWER_BY", "end $SWAP_ID @ ${ANSWER_BY + DAY}"),
            events
        )
    }

    // ---- the later writes --------------------------------------------------------------------

    @Test
    fun `a write still unanswered after ten seconds counts as written`() = runTest {
        // Offline, a Firestore write never completes: the SDK keeps it queued and sends it later.
        coEvery { dataSource.update(any(), any()) } coAnswers { awaitCancellation() }

        val written = repository.markSeen(openClock(), SEEN_AT)

        assertTrue("a worker must not hang offline until WorkManager stops it", written)
        assertEquals("and it waited ten seconds, no more", 10_000L, testScheduler.currentTime)
    }

    @Test
    fun `a refused write returns false`() = runTest {
        coEvery { dataSource.update(any(), any()) } throws denied()
        coEvery { dataSource.reportShown(any()) } throws denied()
        coEvery { dataSource.giveLastChance(any(), any()) } throws denied()
        coEvery { dataSource.close(any()) } throws denied()
        val clock = openClock()

        assertFalse(repository.markDelivered(clock, DELIVERED_AT, listOf(ANSWER_BY - DAY)))
        assertFalse(repository.markSeen(clock, SEEN_AT))
        assertFalse(repository.reportReminderShown(clock))
        assertFalse(repository.giveLastChance(clock, ANSWER_BY + DAY))
        assertFalse(repository.close(clock))
    }

    @Test
    fun `each write is a targeted update of its own fields, and schedules nothing`() = runTest {
        coEvery { dataSource.update(any(), any()) } just Runs
        coEvery { dataSource.reportShown(any()) } just Runs
        coEvery { dataSource.giveLastChance(any(), any()) } just Runs
        coEvery { dataSource.close(any()) } just Runs
        val clock = openClock()
        val reminders = listOf(ANSWER_BY - 2 * DAY, ANSWER_BY - DAY)

        assertTrue(repository.markDelivered(clock, DELIVERED_AT, reminders))
        assertTrue(repository.markSeen(clock, SEEN_AT))
        assertTrue(repository.reportReminderShown(clock))
        assertTrue(repository.giveLastChance(clock, ANSWER_BY + DAY))
        assertTrue(repository.close(clock))

        coVerify(exactly = 1) {
            dataSource.update(CHANGE_ID, RequestDeadlineDocument.deliveredFields(DELIVERED_AT, reminders))
        }
        coVerify(exactly = 1) { dataSource.update(CHANGE_ID, mapOf("seenAtMillis" to SEEN_AT)) }
        coVerify(exactly = 1) { dataSource.reportShown(CHANGE_ID) }
        coVerify(exactly = 1) { dataSource.giveLastChance(CHANGE_ID, ANSWER_BY + DAY) }
        coVerify(exactly = 1) { dataSource.close(CHANGE_ID) }
        assertEquals(
            "only a start schedules; the caller of a step schedules what follows",
            emptyList<String>(),
            events
        )
    }

    @Test
    fun `a cancelled write is not reported as a refusal`() = runTest {
        coEvery { dataSource.update(any(), any()) } throws CancellationException("the scope went away")

        val outcome = runCatching { repository.markSeen(openClock(), SEEN_AT) }

        assertTrue(
            "cancellation belongs to the coroutine, not to the write",
            outcome.exceptionOrNull() is CancellationException
        )
    }

    // ---- reading ------------------------------------------------------------------------------

    @Test
    fun `a listener that keeps failing reads as no clocks`() = runTest {
        var subscriptions = 0
        every { dataSource.observeFamily(ME, FAMILY) } returns flow {
            subscriptions++
            throw FirebaseFirestoreException("The query requires an index.", Code.FAILED_PRECONDITION)
        }

        val emitted = repository.observeFamily(ME, FAMILY).toList()

        assertEquals(listOf(emptyList<RequestDeadline>()), emitted)
        assertEquals("the first try, then eight retries", 9, subscriptions)
        assertEquals(
            "backing off 1, 2, 4, 8, 16 and 32 seconds, then a minute twice",
            183_000L,
            testScheduler.currentTime
        )
    }

    @Test
    fun `a listener that fails once is re-established and delivers what it then receives`() = runTest {
        // The case seen in production for chat: denied a moment before the other phone's write landed.
        val clock = openClock()
        var subscriptions = 0
        every { dataSource.observeFamily(ME, FAMILY) } returns flow {
            subscriptions++
            if (subscriptions == 1) throw denied()
            emit(listOf(clock))
        }

        val emitted = repository.observeFamily(ME, FAMILY).toList()

        assertEquals(listOf(listOf(clock)), emitted)
        assertEquals(2, subscriptions)
    }

    @Test
    fun `a clock that cannot be read reads as no clock, and open clocks as none`() = runTest {
        coEvery { dataSource.get(CHANGE_ID) } throws unavailable()
        coEvery { dataSource.openFor(ME, SINCE) } throws unavailable()

        assertNull(repository.get(CHANGE_ID))
        assertEquals(emptyList<RequestDeadline>(), repository.openFor(ME, SINCE))
    }

    @Test
    fun `a clock and the open clocks come back as the data source read them`() = runTest {
        val clock = openClock()
        coEvery { dataSource.get(CHANGE_ID) } returns clock
        coEvery { dataSource.get("swap_uidA_nobody") } returns null
        coEvery { dataSource.openFor(ME, SINCE) } returns listOf(clock)

        assertEquals(clock, repository.get(CHANGE_ID))
        assertNull(repository.get("swap_uidA_nobody"))
        assertEquals(listOf(clock), repository.openFor(ME, SINCE))
    }

    @Test
    fun `the signed-in uid is the auth service's, and null when nobody is signed in`() {
        assertEquals(ME, repository.signedInUid())

        every { authService.getCurrentUser() } returns null

        assertNull(repository.signedInUid())
    }

    // ---- fixtures ----------------------------------------------------------------------------

    private fun openClock() = RequestDeadline(
        id = CHANGE_ID,
        kind = RequestKind.CHANGE,
        ref = "cr-1",
        familyId = FAMILY,
        proposerUid = ME,
        responderUid = "uidB",
        answerByMillis = ANSWER_BY
    )

    private fun denied() = FirebaseFirestoreException(
        "Missing or insufficient permissions.",
        Code.PERMISSION_DENIED
    )

    private fun unavailable() = FirebaseFirestoreException(
        "Failed to get document because the client is offline.",
        Code.UNAVAILABLE
    )

    /** A [RequestClockScheduler] that writes what it is asked to into [log], in order. */
    private class RecordingScheduler(private val log: MutableList<String>) : RequestClockScheduler {
        override fun scheduleReminder(clockId: String, atMillis: Long) {
            log += "reminder $clockId @ $atMillis"
        }

        override fun scheduleDeadline(clockId: String, atMillis: Long) {
            log += "deadline $clockId @ $atMillis"
        }

        override fun scheduleEnd(clockId: String, atMillis: Long) {
            log += "end $clockId @ $atMillis"
        }

        override fun cancelAll() {
            log += "cancelAll"
        }
    }

    private companion object {
        const val ME = "uidA"
        const val FAMILY = "uidA__uidB"
        const val SWAP_ID = "swap_uidA_g-1"
        const val CHANGE_ID = "change_uidA_cr-1"
        const val DAY = 86_400_000L

        /** 2026-05-05T16:53:20Z. */
        const val NOW = 1_778_000_000_000L
        const val ANSWER_BY = NOW + 3 * DAY
        const val CAP = NOW + 10 * DAY
        const val DELIVERED_AT = NOW + 60_000L
        const val SEEN_AT = NOW + 600_000L
        const val SINCE = NOW - 4 * DAY
    }
}
