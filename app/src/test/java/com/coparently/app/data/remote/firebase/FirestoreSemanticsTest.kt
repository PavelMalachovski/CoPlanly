package com.coparently.app.data.remote.firebase

import com.coparently.app.data.export.CommunicationRecordSource
import com.coparently.app.data.local.dao.ChangeRequestDao
import com.coparently.app.data.local.dao.MessageDao
import com.coparently.app.data.local.entity.ChangeRequestEntity
import com.coparently.app.data.remote.firebase.RecordingFirestoreClient.Operation
import com.coparently.app.data.repository.ChangeRequestRepositoryImpl
import com.coparently.app.data.sync.Tombstone
import com.coparently.app.data.versions.EventVersionDocument
import com.coparently.app.data.versions.EventVersionRecorder
import com.coparently.app.domain.repository.EventRepository
import com.coparently.app.domain.repository.ExpenseRepository
import com.coparently.app.domain.repository.RequestDeadlineRepository
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The four things about Firestore the data layer must keep meaning when the SDK under it changes
 * (MON-24 phase 2, Task 8a: the data sources moved from the Android SDK to [FirestoreClient], over
 * GitLive). Each runs the production classes over [RecordingFirestoreClient], which records the
 * operation as it was asked for — so "an update, not a set" and "from the server, not the cache"
 * are what is checked, not only what ended up stored.
 *
 * 1. A read the export needs from the server is a server read, and its failure marks the record
 *    incomplete ([com.coparently.app.domain.export.RecordSources.serverReached]) rather than
 *    letting this phone's cache stand in for the server.
 * 2. A change request this phone wrote and the server has not confirmed stays unsent in Room.
 * 3. A revision's `recordedAt` is the server's timestamp sentinel, never a phone's clock.
 * 4. A delete is a tombstone: an `update()` of `deletedAtMillis` and `deletedBy` (CLAUDE.md item
 *    14), never a `set()` that would replace the fields the read rules key on.
 *
 * That GitLive hands the SDK these values as the SDK's own — the sentinel as `FieldValue`, numbers
 * as their types — is the R8 probe's `FirestoreWriteProbe`, in the minified build.
 */
class FirestoreSemanticsTest {

    private val client = RecordingFirestoreClient()

    // ---- 1. server reads ------------------------------------------------------------------------

    @Test
    fun `a server read that fails marks the export's record incomplete`() = runTest {
        client.queryAnswer = { _ ->
            val read = client.operations.filterIsInstance<Operation.Query>().last()
            if (read.source == ReadSource.SERVER) throw RecordingFirestoreClient.Failure(FirestoreErrorCode.UNAVAILABLE)
            emptyList()
        }

        val sources = recordSource().gather(ME, CONVERSATION, FROM, TO, TimeZone.UTC)

        assertFalse("a record built without the server must say so", sources.serverReached)
        val reads = client.operations.filterIsInstance<Operation.Query>()
        assertEquals(listOf("event_versions", "messages"), reads.map { it.query.collection })
        assertTrue("every export read asks the server", reads.all { it.source == ReadSource.SERVER })
    }

    @Test
    fun `a server read that answers leaves the record complete`() = runTest {
        val sources = recordSource().gather(ME, CONVERSATION, FROM, TO, TimeZone.UTC)

        assertTrue(sources.serverReached)
    }

    // ---- 2. pending writes ------------------------------------------------------------------

    @Test
    fun `a snapshot with this phone's unconfirmed write keeps a change request unsent`() = runTest {
        val stored = mirror(pendingWrites = true)

        assertEquals("DECLINED", stored.status)
        assertFalse("Firestore's local echo was filed as sent", stored.syncedToFirestore)
    }

    @Test
    fun `a snapshot the server confirmed files the change request as sent`() = runTest {
        assertTrue(mirror(pendingWrites = false).syncedToFirestore)
    }

    // ---- 3. the server's clock --------------------------------------------------------------

    @Test
    fun `a revision's recordedAt is the server timestamp sentinel`() = runTest {
        FirestoreEventVersionDataSource(client).create("v1", mapOf(EventVersionDocument.EVENT_ID to "e1"))

        val set = client.writes.single() as Operation.Set
        assertEquals(SetMerge.Overwrite, set.merge)
        assertEquals(ServerValue.ServerTimestamp, set.data[EventVersionDocument.RECORDED_AT])
    }

    // ---- 4. tombstones ----------------------------------------------------------------------

    @Test
    fun `a tombstone is an update of deletedAtMillis and deletedBy only`() = runTest {
        val tombstone = Tombstone.fields(DELETED_AT, ME)
        listOf("child_info", "pets", "expenses").forEach { client.put(DocumentPath(it, "r1"), mapOf("id" to "r1")) }

        FirestoreChildInfoDataSource(client).tombstoneChildInfo("r1", DELETED_AT, ME).getOrThrow()
        FirestorePetDataSource(client).tombstonePet("r1", DELETED_AT, ME).getOrThrow()
        FirestoreExpenseDataSource(client).tombstoneExpense("r1", DELETED_AT, ME).getOrThrow()

        assertEquals(setOf("deletedAtMillis", "deletedBy"), tombstone.keys)
        assertEquals(
            listOf("child_info", "pets", "expenses").map { Operation.Update(DocumentPath(it, "r1"), tombstone) },
            client.writes
        )
    }

    @Test
    fun `an event's tombstone is an update that also moves the change cursor`() = runTest {
        client.put(DocumentPath("events", "e1"), mapOf("id" to "e1"))

        FirestoreEventDataSource(client).tombstoneEvent("e1", DELETED_AT, ME).getOrThrow()

        val update = client.writes.single() as Operation.Update
        assertEquals(
            Tombstone.fields(DELETED_AT, ME) + ("serverUpdatedAt" to ServerValue.ServerTimestamp),
            update.fields
        )
    }

    @Test
    fun `a tombstone for a document that never landed is done, not retried`() = runTest {
        // `update()` refuses a missing document with NOT_FOUND: there is no remote copy to reach.
        val outcome = FirestoreChildInfoDataSource(client).tombstoneChildInfo("never", DELETED_AT, ME)

        assertTrue(outcome.isSuccess)
        assertTrue("no set() stood in for the update", client.writes.none { it is Operation.Set })
    }

    // ---- fixtures ---------------------------------------------------------------------------

    private fun recordSource() = CommunicationRecordSource(
        versions = FirestoreEventVersionDataSource(client),
        recorder = mockk<EventVersionRecorder> { coEvery { undelivered(any()) } returns emptyList() },
        eventRepository = mockk<EventRepository> { every { getAllEventsInEveryFamily() } returns flowOf(emptyList()) },
        messages = FirestoreMessageDataSource(client),
        messageDao = mockk<MessageDao> { coEvery { getMessagesOnce(any()) } returns emptyList() },
        expenseRepository = mockk<ExpenseRepository> {
            every { getAllExpensesInEveryFamily() } returns flowOf(emptyList())
        }
    )

    /** One mirrored snapshot of a co-parent's request, answered by this phone, through the repository. */
    private suspend fun mirror(pendingWrites: Boolean): ChangeRequestEntity {
        val stored = mutableMapOf<String, ChangeRequestEntity>()
        val dao = mockk<ChangeRequestDao> {
            coEvery { insertChangeRequest(any()) } answers {
                val entity = firstArg<ChangeRequestEntity>()
                stored[entity.id] = entity
            }
            coEvery { getChangeRequestById(any()) } answers { stored[firstArg()] }
        }
        client.queryFlow = { query ->
            val mine = query.filters.single().field == "requestedTo"
            val answer = StoredDocument("r1", answered(), hasPendingWrites = pendingWrites)
            flowOf(if (mine) listOf(answer) else emptyList())
        }
        ChangeRequestRepositoryImpl(
            changeRequestDao = dao,
            firestoreDataSource = FirestoreChangeRequestDataSource(client),
            firebaseAuthService = mockk<SignedInUser> { every { getCurrentUid() } returns ME },
            fcmService = mockk<PushQueue>(relaxed = true),
            requestDeadlines = mockk<RequestDeadlineRepository>()
        ).observeRemote()
        return stored.getValue("r1")
    }

    /** A co-parent's request this phone has answered, as Firestore hands it back. */
    private fun answered(): Map<String, Any?> = mapOf(
        "id" to "r1",
        "eventId" to "e1",
        "eventTitle" to "Swimming",
        "requestedBy" to CO_PARENT,
        "requestedTo" to ME,
        "currentStartDateTime" to "2026-10-10T09:00:00",
        "proposedStartDateTime" to "2026-10-11T09:00:00",
        "status" to "DECLINED",
        "createdAt" to "2026-10-10T09:00:00",
        "respondedAt" to "2026-10-10T10:00:00"
    )

    private companion object {
        const val ME = "u1"
        const val CO_PARENT = "u2"
        const val CONVERSATION = "u1__u2"
        const val DELETED_AT = 1_778_000_000_000L
        val FROM: LocalDate = LocalDate(2026, 10, 1)
        val TO: LocalDate = LocalDate(2026, 10, 31)
    }
}
