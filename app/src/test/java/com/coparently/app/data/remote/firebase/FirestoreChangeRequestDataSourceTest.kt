package com.coparently.app.data.remote.firebase

import app.cash.turbine.test
import com.google.firebase.firestore.CollectionReference
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.EventListener
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.QuerySnapshot
import com.google.firebase.firestore.SnapshotMetadata
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Before
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

    private val listeners = mutableListOf<EventListener<QuerySnapshot>>()
    private val collection = mockk<CollectionReference>()
    private lateinit var dataSource: FirestoreChangeRequestDataSource

    @Before
    fun setUp() {
        listOf("requestedBy", "requestedTo").forEach { field ->
            val query = mockk<Query>()
            every { collection.whereEqualTo(field, MY_UID) } returns query
            every { query.addSnapshotListener(any<EventListener<QuerySnapshot>>()) } answers {
                listeners += firstArg<EventListener<QuerySnapshot>>()
                mockk<ListenerRegistration>(relaxed = true)
            }
        }
        val firestore = mockk<FirebaseFirestore>()
        every { firestore.collection("change_requests") } returns collection
        dataSource = FirestoreChangeRequestDataSource(firestore)
    }

    @Test
    fun `a request this phone has written but not sent says so`() = runTest {
        dataSource.observeChangeRequestsForUser(MY_UID).test {
            listeners.first().onEvent(snapshotOf(document(pendingWrites = true)), null)

            assertEquals(true, awaitItem().single()[FirestoreChangeRequestDataSource.HAS_PENDING_WRITES])
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `a request the server holds says so too`() = runTest {
        dataSource.observeChangeRequestsForUser(MY_UID).test {
            listeners.first().onEvent(snapshotOf(document(pendingWrites = false)), null)

            assertEquals(false, awaitItem().single()[FirestoreChangeRequestDataSource.HAS_PENDING_WRITES])
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `the pending requests from one co-parent are listed by id`() = runTest {
        val listener = slot<EventListener<QuerySnapshot>>()
        val last = mockk<Query>()
        every { last.addSnapshotListener(capture(listener)) } returns mockk<ListenerRegistration>(relaxed = true)
        val byStatus = mockk<Query> { every { whereEqualTo("status", "PENDING") } returns last }
        val byRequester = mockk<Query> { every { whereEqualTo("requestedBy", "u2") } returns byStatus }
        every { collection.whereEqualTo("requestedTo", MY_UID) } returns byRequester

        dataSource.observePendingIdsFrom(MY_UID, "u2").test {
            listener.captured.onEvent(snapshotOf(idOnly("r1"), idOnly("r2")), null)

            assertEquals(setOf("r1", "r2"), awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    private fun idOnly(id: String): DocumentSnapshot = mockk { every { this@mockk.id } returns id }

    private fun document(pendingWrites: Boolean): DocumentSnapshot {
        val metadata = mockk<SnapshotMetadata> { every { hasPendingWrites() } returns pendingWrites }
        return mockk {
            every { id } returns "r1"
            every { data } returns mapOf("status" to "DECLINED")
            every { this@mockk.metadata } returns metadata
        }
    }

    private fun snapshotOf(vararg documents: DocumentSnapshot): QuerySnapshot =
        mockk { every { this@mockk.documents } returns documents.toList() }

    private companion object {
        const val MY_UID = "u1"
    }
}
