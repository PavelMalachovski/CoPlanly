package com.coparently.app.data.remote.firebase

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * That a change request this phone wrote, and the server has not confirmed, says so on its way to
 * the repository (final review of phase 2, finding 2).
 *
 * Firestore applies a write locally at once and echoes it to the listener before the server has
 * it, so an answer given offline arrives back looking exactly like one the server holds. Without
 * the mark, the mirror filed it as sent, and the History's "Queued" never showed.
 */
class FirestoreChangeRequestDataSourceTest {

    private val client = RecordingFirestoreClient()
    private val dataSource = FirestoreChangeRequestDataSource(client)

    @Test
    fun `a request this phone has written but not sent says so`() = runTest {
        client.queryFlow = { flowOf(listOf(request(pendingWrites = true))) }

        val requests = dataSource.observeChangeRequestsForUser(MY_UID).first()

        assertEquals(true, requests.single()[FirestoreChangeRequestDataSource.HAS_PENDING_WRITES])
    }

    @Test
    fun `a request the server holds says so too`() = runTest {
        client.queryFlow = { flowOf(listOf(request(pendingWrites = false))) }

        val requests = dataSource.observeChangeRequestsForUser(MY_UID).first()

        assertEquals(false, requests.single()[FirestoreChangeRequestDataSource.HAS_PENDING_WRITES])
    }

    @Test
    fun `both sides of a request are listened to and one request is listed once`() = runTest {
        client.queryFlow = { flowOf(listOf(request(pendingWrites = false))) }

        dataSource.observeChangeRequestsForUser(MY_UID).first()

        val queries = client.operations.filterIsInstance<RecordingFirestoreClient.Operation.ObserveQuery>()
            .map { it.query }
        assertEquals(
            listOf(
                DocumentQuery("change_requests").whereEqualTo("requestedBy", MY_UID),
                DocumentQuery("change_requests").whereEqualTo("requestedTo", MY_UID)
            ),
            queries
        )
    }

    @Test
    fun `the pending requests from one co-parent are listed by id`() = runTest {
        client.queryAnswer = { listOf(StoredDocument("r1", mapOf()), StoredDocument("r2", mapOf())) }

        assertEquals(setOf("r1", "r2"), dataSource.observePendingIdsFrom(MY_UID, "u2").first())
        val query = client.operations.filterIsInstance<RecordingFirestoreClient.Operation.ObserveQuery>().single().query
        assertEquals(
            DocumentQuery("change_requests")
                .whereEqualTo("requestedTo", MY_UID)
                .whereEqualTo("requestedBy", "u2")
                .whereEqualTo("status", "PENDING"),
            query
        )
    }

    private fun request(pendingWrites: Boolean) =
        StoredDocument("r1", mapOf("status" to "DECLINED"), hasPendingWrites = pendingWrites)

    private companion object {
        const val MY_UID = "u1"
    }
}
