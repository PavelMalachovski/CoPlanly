package com.coparently.app.data.repository

import com.coparently.app.data.local.dao.ChangeRequestDao
import com.coparently.app.data.local.entity.ChangeRequestEntity
import com.coparently.app.data.remote.firebase.FcmService
import com.coparently.app.data.remote.firebase.FirebaseAuthService
import com.coparently.app.data.remote.firebase.FirestoreChangeRequestDataSource
import com.coparently.app.data.remote.firebase.PushPayload
import com.coparently.app.domain.changerequests.AnswerBy
import com.coparently.app.domain.changerequests.AnswerByPick
import com.coparently.app.domain.changerequests.RequestDeadline
import com.coparently.app.domain.changerequests.RequestKind
import com.coparently.app.domain.changerequests.RequestStart
import com.coparently.app.domain.model.ChangeRequest
import com.coparently.app.domain.model.ChangeRequestStatus
import com.coparently.app.domain.repository.RequestDeadlineRepository
import com.google.firebase.auth.FirebaseUser
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.coVerifyOrder
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * That "sent" is claimed for a change request only once the server has it (CQ-20, and the phase 2
 * History's "Queued"; final review of phase 2, finding 2).
 *
 * Two ways an answer given offline used to read as sent at once: [ChangeRequestRepositoryImpl.updateStatus]
 * copied the mirrored row, which every request downloaded from the co-parent carries as synced,
 * and the mirror filed Firestore's local echo of this phone's own unsent write as synced too.
 *
 * And, since phase 3a, that a request given a deadline starts its clock once it is published, and
 * that its first push says when the answer is due (spec §5.1, §5.4). A clock that is not written
 * leaves the request working as one without a clock (ruling 8).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ChangeRequestRepositoryImplTest {

    /** Room, as a map: what the repository last wrote under each id. */
    private val stored = mutableMapOf<String, ChangeRequestEntity>()
    private val dao = mockk<ChangeRequestDao> {
        coEvery { insertChangeRequest(any()) } answers {
            val entity = firstArg<ChangeRequestEntity>()
            stored[entity.id] = entity
        }
        coEvery { getChangeRequestById(any()) } answers { stored[firstArg()] }
        coEvery { getUnsyncedChangeRequests() } answers { stored.values.filter { !it.syncedToFirestore } }
    }
    private val dataSource = mockk<FirestoreChangeRequestDataSource>()
    private val olya = mockk<FirebaseUser> {
        every { uid } returns MY_UID
        every { displayName } returns "Olya"
    }
    private val fcmService = mockk<FcmService>(relaxed = true)

    /** Strict: a clock started where none should be fails the test by itself, not only by a verify. */
    private val requestDeadlines = mockk<RequestDeadlineRepository>()
    private val repository = ChangeRequestRepositoryImpl(
        changeRequestDao = dao,
        firestoreDataSource = dataSource,
        firebaseAuthService = mockk<FirebaseAuthService> { every { getCurrentUser() } returns olya },
        fcmService = fcmService,
        requestDeadlines = requestDeadlines
    )

    /** An event ten days off: far enough for every deadline to be offered. */
    private val inTenDays: LocalDateTime = LocalDateTime.now().plusDays(10)

    /** Olya's own request, as she sent it: stamped with its family, on the server already, so synced. */
    private val olyasRequest = ChangeRequestEntity(
        id = "r2",
        eventId = "e2",
        eventTitle = "Dentist",
        requestedBy = MY_UID,
        requestedTo = CO_PARENT_UID,
        currentStartDateTime = inTenDays,
        proposedStartDateTime = inTenDays.plusDays(1),
        status = "PENDING",
        createdAt = AT,
        syncedToFirestore = true,
        familyId = FAMILY_ID
    )

    /** Pavel's request, as the mirror stored it: downloaded, so synced. */
    private val pavelsRequest = ChangeRequestEntity(
        id = "r1",
        eventId = "e1",
        eventTitle = "Swimming",
        requestedBy = CO_PARENT_UID,
        requestedTo = MY_UID,
        currentStartDateTime = AT,
        proposedStartDateTime = AT.plusDays(1),
        status = "PENDING",
        createdAt = AT,
        syncedToFirestore = true
    )

    @Test
    fun `an answer given offline is not sent until the server has it`() = runTest {
        stored[pavelsRequest.id] = pavelsRequest
        // Offline: Firestore holds the write and the call waits for a server that does not answer.
        coEvery { dataSource.setChangeRequest(any(), any()) } coAnswers { awaitCancellation() }

        val answering = launch { repository.updateStatus("r1", ChangeRequestStatus.DECLINED) }
        runCurrent()

        val row = stored.getValue("r1")
        assertEquals("DECLINED", row.status)
        assertFalse("an unsent answer was filed as sent", row.syncedToFirestore)
        answering.cancel()
    }

    @Test
    fun `an answer the server took is sent`() = runTest {
        stored[pavelsRequest.id] = pavelsRequest
        coEvery { dataSource.setChangeRequest(any(), any()) } returns Unit

        repository.updateStatus("r1", ChangeRequestStatus.DECLINED)

        assertTrue(stored.getValue("r1").syncedToFirestore)
    }

    @Test
    fun `the echo of this phone's unsent answer stays unsent`() = runTest {
        every { dataSource.observeChangeRequestsForUser(MY_UID) } returns
            flowOf(listOf(document(status = "DECLINED", pendingWrites = true)))

        repository.observeRemote()

        assertEquals("DECLINED", stored.getValue("r1").status)
        assertFalse("Firestore's local echo was filed as sent", stored.getValue("r1").syncedToFirestore)
    }

    @Test
    fun `a request the server holds is sent`() = runTest {
        every { dataSource.observeChangeRequestsForUser(MY_UID) } returns
            flowOf(listOf(document(status = "PENDING", pendingWrites = false)))

        repository.observeRemote()

        assertTrue(stored.getValue("r1").syncedToFirestore)
    }

    // ---- the request's clock (phase 3a) --------------------------------------
    //
    // A request given a deadline starts its clock in the same action, once the request is on the
    // server, and the push that tells the co-parent says when the answer is due — but only when the
    // clock was written (ruling 8). An answer is not a request, and a request sent from the outbox
    // later goes without one.

    @Test
    fun `a request with a deadline starts its clock after it is published and before the push, which says when`() =
        runTest {
            coEvery { dataSource.setChangeRequest(any(), any()) } returns Unit
            var syncedWhenStarted: Boolean? = null
            val started = slot<RequestStart>()
            coEvery { requestDeadlines.start(capture(started)) } answers {
                syncedWhenStarted = stored["r2"]?.syncedToFirestore
                clockFor(firstArg())
            }
            val request = myRequest()

            repository.createChangeRequest(request, AnswerByPick.DEFAULT)

            coVerifyOrder {
                dataSource.setChangeRequest("r2", any())
                requestDeadlines.start(
                    match {
                        it.kind == RequestKind.CHANGE && it.ref == "r2" && it.responderUid == CO_PARENT_UID
                    }
                )
                fcmService.queueNotificationForUser(
                    CO_PARENT_UID,
                    match {
                        it[PushPayload.TYPE] == PushPayload.CHANGE_REQUEST_CREATED &&
                            it[PushPayload.ANSWER_BY] == CLOCK_ANSWER_BY.toString()
                    }
                )
            }
            // The request is filed as sent before its clock starts: a clock never outruns its request.
            assertEquals(true, syncedWhenStarted)
            // The clock names the pair's family, and its cap is the moment the event would change: here
            // the event's current start. The deadline leaves a day for a last chance, and is ahead.
            val cap = millisOf(request.currentStartDateTime)
            assertEquals(FAMILY_ID, started.captured.familyId)
            assertEquals(cap, started.captured.capMillis)
            assertTrue(started.captured.answerByMillis <= cap - AnswerBy.LAST_CHANCE_RESERVE_MILLIS)
            assertTrue(started.captured.answerByMillis > System.currentTimeMillis())
            assertNull(started.captured.firstDate)
            assertNull(started.captured.dayCount)
        }

    @Test
    fun `a change to a series is capped at its proposed start, the stored one being long past`() = runTest {
        coEvery { dataSource.setChangeRequest(any(), any()) } returns Unit
        val started = slot<RequestStart>()
        coEvery { requestDeadlines.start(capture(started)) } answers { clockFor(firstArg()) }
        val request = myRequest(currentStart = LocalDateTime.now().minusYears(1), proposedStart = inTenDays)

        repository.createChangeRequest(request, AnswerByPick.DEFAULT)

        assertEquals(millisOf(inTenDays), started.captured.capMillis)
    }

    @Test
    fun `a request that changes something within two hours goes out without a clock`() = runTest {
        coEvery { dataSource.setChangeRequest(any(), any()) } returns Unit

        repository.createChangeRequest(
            myRequest(currentStart = LocalDateTime.now().plusMinutes(90), proposedStart = inTenDays),
            AnswerByPick.DEFAULT
        )

        // Too soon for a deadline: it goes out as a plain request, published and announced as ever.
        coVerify(exactly = 0) { requestDeadlines.start(any()) }
        assertTrue(stored.getValue("r2").syncedToFirestore)
        coVerify(exactly = 1) {
            fcmService.queueNotificationForUser(
                CO_PARENT_UID,
                match { it[PushPayload.TYPE] == PushPayload.CHANGE_REQUEST_CREATED && PushPayload.ANSWER_BY !in it }
            )
        }
    }

    @Test
    fun `a request without a deadline starts no clock and sends its push as it always did`() = runTest {
        coEvery { dataSource.setChangeRequest(any(), any()) } returns Unit

        repository.createChangeRequest(myRequest())

        coVerify(exactly = 0) { requestDeadlines.start(any()) }
        coVerify(exactly = 1) {
            fcmService.queueNotificationForUser(
                CO_PARENT_UID,
                mapOf(
                    PushPayload.TYPE to PushPayload.CHANGE_REQUEST_CREATED,
                    PushPayload.CHANGE_REQUEST_ID to "r2",
                    PushPayload.EVENT_ID to "e2",
                    PushPayload.SUBJECT to "Dentist",
                    PushPayload.ACTOR to "Olya"
                )
            )
        }
    }

    @Test
    fun `a refused clock still sends the request, without answerBy`() = runTest {
        coEvery { dataSource.setChangeRequest(any(), any()) } returns Unit
        coEvery { requestDeadlines.start(any()) } returns null

        repository.createChangeRequest(myRequest(), AnswerByPick.DEFAULT)

        // The rules refused the clock (an offline queue landing late, a fast phone), and the request
        // is still published, filed as sent and announced — as a request with no deadline (ruling 8).
        coVerify(exactly = 1) { requestDeadlines.start(any()) }
        assertTrue(stored.getValue("r2").syncedToFirestore)
        coVerify(exactly = 1) {
            fcmService.queueNotificationForUser(
                CO_PARENT_UID,
                match { it[PushPayload.TYPE] == PushPayload.CHANGE_REQUEST_CREATED && PushPayload.ANSWER_BY !in it }
            )
        }
    }

    @Test
    fun `a clock that fails does not stop the request being sent`() = runTest {
        coEvery { dataSource.setChangeRequest(any(), any()) } returns Unit
        coEvery { requestDeadlines.start(any()) } throws IllegalStateException("WorkManager is not initialised")

        repository.createChangeRequest(myRequest(), AnswerByPick.DEFAULT)

        // The request is already on the server: the co-parent is told, with no deadline to name.
        assertTrue(stored.getValue("r2").syncedToFirestore)
        coVerify(exactly = 1) {
            fcmService.queueNotificationForUser(
                CO_PARENT_UID,
                match { it[PushPayload.TYPE] == PushPayload.CHANGE_REQUEST_CREATED && PushPayload.ANSWER_BY !in it }
            )
        }
    }

    @Test
    fun `a request flushed from the outbox later goes without a clock`() = runTest {
        // The server refuses the write: the request stays queued in Room, announced to nobody.
        coEvery { dataSource.setChangeRequest(any(), any()) } throws IllegalStateException("UNAVAILABLE")
        repository.createChangeRequest(myRequest(), AnswerByPick.DEFAULT)
        assertFalse(stored.getValue("r2").syncedToFirestore)
        coVerify(exactly = 0) { requestDeadlines.start(any()) }
        coVerify(exactly = 0) { fcmService.queueNotificationForUser(any(), any()) }

        // The next pass sends it. The deadline was a choice made in a form and is not stored in Room, so
        // the request goes out as a plain one (ruling 8): no clock, and a push that says nothing of one.
        coEvery { dataSource.setChangeRequest(any(), any()) } returns Unit
        repository.flushOutbox()

        assertTrue(stored.getValue("r2").syncedToFirestore)
        coVerify(exactly = 0) { requestDeadlines.start(any()) }
        coVerify(exactly = 1) {
            fcmService.queueNotificationForUser(
                CO_PARENT_UID,
                match { it[PushPayload.TYPE] == PushPayload.CHANGE_REQUEST_CREATED && PushPayload.ANSWER_BY !in it }
            )
        }
    }

    @Test
    fun `an answer starts no clock`() = runTest {
        coEvery { dataSource.setChangeRequest(any(), any()) } returns Unit

        // Accepting and declining Pavel's request, and withdrawing her own, are answers rather than
        // requests: nobody waits on them. Both requests are about an event still ahead and name their
        // family, so a clock would have everything it needs to start — that none does is down to an
        // answer being no request.
        val pavelsAhead = pavelsRequest.copy(
            currentStartDateTime = inTenDays,
            proposedStartDateTime = inTenDays.plusDays(1),
            familyId = FAMILY_ID
        )
        listOf(ChangeRequestStatus.ACCEPTED, ChangeRequestStatus.DECLINED).forEach { status ->
            stored[pavelsAhead.id] = pavelsAhead
            repository.updateStatus("r1", status)
            assertEquals(status.name, stored.getValue("r1").status)
        }
        stored[olyasRequest.id] = olyasRequest
        repository.updateStatus("r2", ChangeRequestStatus.CANCELLED)
        assertEquals("CANCELLED", stored.getValue("r2").status)

        coVerify(exactly = 0) { requestDeadlines.start(any()) }
        // Each was announced to the other parent, and none of them says anything of a deadline.
        coVerify(exactly = 3) { fcmService.queueNotificationForUser(any(), any()) }
        coVerify(exactly = 0) { fcmService.queueNotificationForUser(any(), match { PushPayload.ANSWER_BY in it }) }
    }

    /** Olya's own request to Pavel about an event in the future: the shape `RequestChangeViewModel` sends. */
    private fun myRequest(
        currentStart: LocalDateTime = inTenDays,
        proposedStart: LocalDateTime = currentStart.plusDays(1)
    ) = ChangeRequest(
        id = "r2",
        eventId = "e2",
        eventTitle = "Dentist",
        requestedBy = MY_UID,
        requestedTo = CO_PARENT_UID,
        currentStartDateTime = currentStart,
        proposedStartDateTime = proposedStart,
        createdAt = LocalDateTime.now()
    )

    /**
     * The clock [start] would write, with a deadline of its own ([CLOCK_ANSWER_BY]), so that a push
     * which says the clock's deadline can be told from one which says anything else.
     */
    private fun clockFor(start: RequestStart) = RequestDeadline(
        id = "change_${MY_UID}_${start.ref}",
        kind = start.kind,
        ref = start.ref,
        familyId = start.familyId,
        proposerUid = MY_UID,
        responderUid = start.responderUid,
        answerByMillis = CLOCK_ANSWER_BY,
        capMillis = start.capMillis
    )

    /** [local] as the phone reads it: a wall-clock time in the device's zone. */
    private fun millisOf(local: LocalDateTime): Long =
        local.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()

    /** Pavel's request as the data source hands it over, with its pending-writes mark. */
    private fun document(status: String, pendingWrites: Boolean): Map<String, Any> = mapOf(
        "id" to "r1",
        "eventId" to "e1",
        "eventTitle" to "Swimming",
        "requestedBy" to CO_PARENT_UID,
        "requestedTo" to MY_UID,
        "currentStartDateTime" to "2026-10-10T09:00:00",
        "proposedStartDateTime" to "2026-10-11T09:00:00",
        "status" to status,
        "createdAt" to "2026-10-10T09:00:00",
        "respondedAt" to "",
        FirestoreChangeRequestDataSource.HAS_PENDING_WRITES to pendingWrites
    )

    private companion object {
        const val MY_UID = "u1"
        const val CO_PARENT_UID = "u2"

        /** The pair's family: the two uids, sorted and joined. */
        const val FAMILY_ID = "u1__u2"
        val AT: LocalDateTime = LocalDateTime.of(2026, 10, 10, 9, 0)

        /** A deadline no computation here would produce, 2030-01-01T00:00:00Z. */
        const val CLOCK_ANSWER_BY = 1_893_456_000_000L
    }
}
