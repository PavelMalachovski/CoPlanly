package com.coparently.app.data.repository

import com.coparently.app.data.local.dao.ChangeRequestDao
import com.coparently.app.data.local.entity.ChangeRequestEntity
import com.coparently.app.data.remote.firebase.FcmService
import com.coparently.app.data.remote.firebase.FirebaseAuthService
import com.coparently.app.data.remote.firebase.FirestoreChangeRequestDataSource
import com.coparently.app.domain.model.ChangeRequestStatus
import com.google.firebase.auth.FirebaseUser
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime

/**
 * That "sent" is claimed for a change request only once the server has it (CQ-20, and the phase 2
 * History's "Queued"; final review of phase 2, finding 2).
 *
 * Two ways an answer given offline used to read as sent at once: [ChangeRequestRepositoryImpl.updateStatus]
 * copied the mirrored row, which every request downloaded from the co-parent carries as synced,
 * and the mirror filed Firestore's local echo of this phone's own unsent write as synced too.
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
    }
    private val dataSource = mockk<FirestoreChangeRequestDataSource>()
    private val olya = mockk<FirebaseUser> {
        every { uid } returns MY_UID
        every { displayName } returns "Olya"
    }
    private val repository = ChangeRequestRepositoryImpl(
        changeRequestDao = dao,
        firestoreDataSource = dataSource,
        firebaseAuthService = mockk<FirebaseAuthService> { every { getCurrentUser() } returns olya },
        fcmService = mockk<FcmService>(relaxed = true)
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
        val AT: LocalDateTime = LocalDateTime.of(2026, 10, 10, 9, 0)
    }
}
