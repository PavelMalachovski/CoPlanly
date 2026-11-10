package com.coparently.app.data.remote.firebase

import com.google.android.gms.tasks.Tasks
import com.google.firebase.Timestamp
import com.google.firebase.firestore.CollectionReference
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.EventListener
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.Filter
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.MetadataChanges
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.QuerySnapshot
import com.google.firebase.firestore.SetOptions
import com.google.firebase.firestore.SnapshotMetadata
import com.google.firebase.firestore.Source
import com.google.firebase.firestore.Transaction
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.slot
import io.mockk.unmockkAll
import io.mockk.verify
import io.mockk.verifyOrder
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import com.google.firebase.firestore.FieldPath as AndroidFieldPath

/**
 * That [firestoreClientOf] turns each [FirestoreClient] operation into the Android SDK call the data
 * sources made before MON-24 phase 2 (Task 8a), through GitLive's Android half.
 *
 * `FirestoreSemanticsTest` proves the data sources *ask* for an update, a server read or the
 * server's clock; this proves the client *makes* that call: an update is `update()`, a merge is
 * `SetOptions.merge()`, a server read is `Source.SERVER`, the sentinels are the SDK's own
 * `FieldValue`s, an increment is the integer one, and a snapshot's metadata and estimated server
 * times come from the document itself. A one-word slip in `GitLiveFirestoreClient` or its Android
 * half fails here, in the fast unit-test job, rather than only in the gated two-phone run.
 *
 * Every mock is strict: a call the client should not make (an overwrite where a merge was asked
 * for, `Source.DEFAULT` for a server read, `ServerTimestampBehavior.NONE` for an estimate) has no
 * answer and fails the test.
 */
class GitLiveFirestoreClientAndroidTest {

    private val firestore = mockk<FirebaseFirestore>()
    private val collection = mockk<CollectionReference>()
    private val document = mockk<DocumentReference>()
    private val snapshot = mockk<DocumentSnapshot>()
    private val metadata = mockk<SnapshotMetadata>()
    private val client = firestoreClientOf(firestore)

    @Before
    fun wireTheSdk() {
        every { firestore.collection(COLLECTION) } returns collection
        every { collection.document(ID) } returns document
        every { snapshot.id } returns ID
        every { snapshot.metadata } returns metadata
        every { metadata.hasPendingWrites() } returns false
        every { metadata.isFromCache } returns false
        every { snapshot.data } returns mapOf("title" to "Dentist")
    }

    @After
    fun unmock() {
        unmockkAll()
    }

    // ---- writes -------------------------------------------------------------------------------

    @Test
    fun `a set overwrites with the SDK's own values and sentinels`() = runTest {
        val written = slot<Any>()
        every { document.set(capture(written)) } returns Tasks.forResult<Void>(null)

        client.set(PATH, PLAIN)

        verify(exactly = 1) { document.set(any()) }
        verify(exactly = 0) { document.set(any(), any()) }
        assertEquals(NATIVE, written.captured)
    }

    @Test
    fun `a merge is SetOptions merge and never an overwrite`() = runTest {
        every { document.set(any(), any()) } returns Tasks.forResult<Void>(null)

        client.set(PATH, mapOf("name" to "Alice"), SetMerge.Merge)

        verify(exactly = 1) { document.set(mapOf("name" to "Alice"), SetOptions.merge()) }
        verify(exactly = 0) { document.set(any()) }
    }

    @Test
    fun `a merge of named fields is SetOptions mergeFieldPaths`() = runTest {
        val options = slot<SetOptions>()
        every { document.set(any(), capture(options)) } returns Tasks.forResult<Void>(null)

        client.set(PATH, mapOf("answers" to mapOf("u1" to "yes")), SetMerge.Fields(listOf(FieldPath("answers", "u1"))))

        assertEquals(SetOptions.mergeFieldPaths(listOf(AndroidFieldPath.of("answers", "u1"))), options.captured)
        verify(exactly = 0) { document.set(any()) }
    }

    @Test
    fun `an update of a map is the SDK's update and never a set`() = runTest {
        every { document.update(any<Map<String, Any>>()) } returns Tasks.forResult<Void>(null)

        client.update(PATH, mapOf("deletedAtMillis" to 5L, "deletedBy" to "u1", "at" to ServerValue.ServerTimestamp))

        verify(exactly = 1) {
            document.update(mapOf("deletedAtMillis" to 5L, "deletedBy" to "u1", "at" to FieldValue.serverTimestamp()))
        }
        verify(exactly = 0) { document.set(any()) }
        verify(exactly = 0) { document.set(any(), any()) }
    }

    @Test
    fun `an update of one dotted field names that field`() = runTest {
        every { document.update(any<String>(), any(), *anyVararg()) } returns Tasks.forResult<Void>(null)

        client.update(PATH, "lastReadAt.u1", 7L)

        verify(exactly = 1) { document.update("lastReadAt.u1", 7L) }
    }

    @Test
    fun `an update of a field path keeps its segments whole`() = runTest {
        every { document.update(any<AndroidFieldPath>(), any(), *anyVararg()) } returns Tasks.forResult<Void>(null)

        client.update(PATH, FieldPath("answers", "u.1"), ServerValue.Delete)

        verify(exactly = 1) { document.update(AndroidFieldPath.of("answers", "u.1"), FieldValue.delete()) }
    }

    @Test
    fun `an increment is the SDK's integer increment`() = runTest {
        mockkStatic(FieldValue::class)
        every { document.update(any<String>(), any(), *anyVararg()) } returns Tasks.forResult<Void>(null)

        client.increment(PATH, "remindersShown", 1)

        verify(exactly = 1) { FieldValue.increment(1L) }
        verify(exactly = 0) { FieldValue.increment(any<Double>()) }
        verify(exactly = 1) { document.update("remindersShown", any()) }
    }

    @Test
    fun `a delete deletes the document`() = runTest {
        every { document.delete() } returns Tasks.forResult<Void>(null)

        client.delete(PATH)

        verify(exactly = 1) { document.delete() }
    }

    @Test
    fun `a new id is the SDK's own and nothing is written`() {
        val fresh = mockk<DocumentReference>()
        every { collection.document() } returns fresh
        every { fresh.id } returns "Xy12"

        assertEquals("Xy12", client.newDocumentId(COLLECTION))
        verify(exactly = 1) { collection.document() }
    }

    // ---- reads --------------------------------------------------------------------------------

    @Test
    fun `a server read asks the SDK for the server`() = runTest {
        every { document.get(Source.SERVER) } returns Tasks.forResult(snapshot)

        client.get(PATH, ReadSource.SERVER)

        verify(exactly = 1) { document.get(Source.SERVER) }
    }

    @Test
    fun `a default read lets the SDK fall back to its cache`() = runTest {
        every { document.get(Source.DEFAULT) } returns Tasks.forResult(snapshot)

        client.get(PATH)

        verify(exactly = 1) { document.get(Source.DEFAULT) }
    }

    @Test
    fun `a read hands over the document's own metadata and plain values`() = runTest {
        every { metadata.hasPendingWrites() } returns true
        every { metadata.isFromCache } returns true
        every { snapshot.data } returns mapOf(
            "title" to "Dentist",
            "createdAt" to null,
            "cursor" to Timestamp(10, 5),
            "nested" to mapOf("at" to Timestamp(11, 0))
        )
        every { snapshot.get("createdAt", DocumentSnapshot.ServerTimestampBehavior.ESTIMATE) } returns Timestamp(20, 0)
        every { document.get(Source.DEFAULT) } returns Tasks.forResult(snapshot)

        val stored = client.get(PATH)

        assertEquals(ID, stored.id)
        assertTrue("the document's unconfirmed write is reported", stored.hasPendingWrites)
        assertTrue("an answer from the cache is reported", stored.isFromCache)
        assertEquals(FirestoreTimestamp(10, 5), stored.timestamp("cursor"))
        assertEquals(mapOf("at" to FirestoreTimestamp(11, 0)), stored.data?.get("nested"))
        assertEquals(null, stored.data?.get("createdAt"))
        assertEquals(FirestoreTimestamp(20, 0), stored.estimated("createdAt"))
    }

    @Test
    fun `a query builds its filters order and bound in order and reads the server`() = runTest {
        mockkStatic(Filter::class)
        val filtered = mockk<Query>()
        val narrowed = mockk<Query>()
        val ordered = mockk<Query>()
        val bounded = mockk<Query>()
        val results = mockk<QuerySnapshot>()
        every { collection.where(any()) } returns filtered
        every { filtered.where(any()) } returns narrowed
        every { narrowed.orderBy(any<String>(), any()) } returns ordered
        every { ordered.limitToLast(any()) } returns bounded
        every { bounded.get(Source.SERVER) } returns Tasks.forResult(results)
        every { results.documents } returns listOf(snapshot)

        val documents = client.get(
            DocumentQuery(COLLECTION)
                .whereEqualTo("familyId", "f1")
                .whereGreaterThan("serverUpdatedAt", FirestoreTimestamp(10, 5))
                .orderBy("date", descending = true)
                .limitToLast(5),
            ReadSource.SERVER
        )

        assertEquals(listOf(ID), documents.map { it.id })
        verifyOrder {
            Filter.equalTo("familyId", "f1")
            Filter.greaterThan("serverUpdatedAt", Timestamp(10, 5))
        }
        verify(exactly = 1) { narrowed.orderBy("date", Query.Direction.DESCENDING) }
        verify(exactly = 1) { ordered.limitToLast(5L) }
        verify(exactly = 1) { bounded.get(Source.SERVER) }
    }

    @Test
    fun `the other comparisons map to their own SDK filters`() = runTest {
        mockkStatic(Filter::class)
        val first = mockk<Query>()
        val second = mockk<Query>()
        val third = mockk<Query>()
        val ordered = mockk<Query>()
        val none = mockk<QuerySnapshot>()
        every { none.documents } returns emptyList()
        every { collection.where(any()) } returns first
        every { first.where(any()) } returns second
        every { second.where(any()) } returns third
        every { third.orderBy(any<String>(), any()) } returns ordered
        every { ordered.get(Source.DEFAULT) } returns Tasks.forResult(none)

        client.get(
            DocumentQuery(COLLECTION)
                .whereArrayContains("sharedWith", "u1")
                .whereGreaterThanOrEqualTo("timestamp", 5L)
                .whereLessThan("timestamp", 9L)
                .orderBy("timestamp")
        )

        verifyOrder {
            Filter.arrayContains("sharedWith", "u1")
            Filter.greaterThanOrEqualTo("timestamp", 5L)
            Filter.lessThan("timestamp", 9L)
        }
        verify(exactly = 1) { third.orderBy("timestamp", Query.Direction.ASCENDING) }
    }

    @Test
    fun `an in filter is the SDK's inArray over the same values`() = runTest {
        mockkStatic(Filter::class)
        val filtered = mockk<Query>()
        val narrowed = mockk<Query>()
        val none = mockk<QuerySnapshot>()
        every { none.documents } returns emptyList()
        every { collection.where(any()) } returns filtered
        every { filtered.where(any()) } returns narrowed
        every { narrowed.get(Source.DEFAULT) } returns Tasks.forResult(none)

        client.get(
            DocumentQuery(COLLECTION)
                .whereEqualTo("familyId", "a__b")
                .whereIn("createdByFirebaseUid", listOf("a", "b"))
        )

        verifyOrder {
            Filter.equalTo("familyId", "a__b")
            Filter.inArray("createdByFirebaseUid", listOf("a", "b"))
        }
    }

    @Test
    fun `a listener with metadata includes metadata changes and hands over the snapshot's own`() = runTest {
        mockkStatic(Filter::class)
        val filtered = mockk<Query>()
        val listener = slot<EventListener<QuerySnapshot>>()
        val registration = mockk<ListenerRegistration>(relaxed = true)
        val results = mockk<QuerySnapshot>()
        val queryMetadata = mockk<SnapshotMetadata>()
        every { collection.where(any()) } returns filtered
        every { filtered.addSnapshotListener(MetadataChanges.INCLUDE, capture(listener)) } answers {
            listener.captured.onEvent(results, null)
            registration
        }
        every { results.documents } returns listOf(snapshot)
        every { results.metadata } returns queryMetadata
        every { queryMetadata.hasPendingWrites() } returns true
        every { queryMetadata.isFromCache } returns true

        val first = client.observeWithMetadata(DocumentQuery(COLLECTION).whereEqualTo("familyId", "a__b")).first()

        assertEquals(listOf(ID), first.documents.map { it.id })
        assertTrue(first.hasPendingWrites)
        assertTrue(first.isFromCache)
        verify(exactly = 1) { filtered.addSnapshotListener(MetadataChanges.INCLUDE, any()) }
        verify(exactly = 1) { registration.remove() }
    }

    // ---- transactions and failures ------------------------------------------------------------

    @Test
    fun `a transaction reads and writes through the SDK's transaction`() = runTest {
        val transaction = mockk<Transaction>()
        every { firestore.runTransaction(any<Transaction.Function<Any?>>()) } answers {
            Tasks.forResult(firstArg<Transaction.Function<Any?>>().apply(transaction))
        }
        every { transaction.get(document) } returns snapshot
        every { transaction.set(document, any()) } returns transaction

        val title = client.transaction {
            val current = get(PATH)
            set(PATH, mapOf("title" to "Swimming", "at" to ServerValue.ServerTimestamp))
            current.data?.get("title")
        }

        assertEquals("Dentist", title)
        verify(exactly = 1) { transaction.get(document) }
        verify(exactly = 1) {
            transaction.set(document, mapOf("title" to "Swimming", "at" to FieldValue.serverTimestamp()))
        }
        verify(exactly = 0) { transaction.set(any(), any(), any()) }
    }

    @Test
    fun `a Firestore failure is classified by its code`() {
        val failure = FirebaseFirestoreException("gone", FirebaseFirestoreException.Code.NOT_FOUND)

        assertEquals(FirestoreErrorCode.NOT_FOUND, client.errorCodeOf(failure))
        assertEquals(null, client.errorCodeOf(IllegalStateException("not Firestore")))
    }

    private companion object {
        const val COLLECTION = "events"
        const val ID = "e1"
        val PATH = DocumentPath(COLLECTION, ID)

        /** A write as a data source hands it over. */
        val PLAIN: Map<String, Any?> = mapOf(
            "title" to "Dentist",
            "patternDays" to 14,
            "deviceTimeMillis" to 1_778_000_000_000L,
            "share" to 0.5,
            "isPrivate" to false,
            "absent" to null,
            "sharedWith" to listOf("u1", "u2"),
            "nested" to mapOf("lastReadAt" to mapOf("u1" to 5L)),
            "recordedAt" to ServerValue.ServerTimestamp,
            "allergies" to ServerValue.Delete,
            "cursor" to FirestoreTimestamp(1_778_000_000L, 5)
        )

        /** The same write as the SDK must receive it: the types unchanged, the sentinels its own. */
        val NATIVE: Map<String, Any?> = mapOf(
            "title" to "Dentist",
            "patternDays" to 14,
            "deviceTimeMillis" to 1_778_000_000_000L,
            "share" to 0.5,
            "isPrivate" to false,
            "absent" to null,
            "sharedWith" to listOf("u1", "u2"),
            "nested" to mapOf("lastReadAt" to mapOf("u1" to 5L)),
            "recordedAt" to FieldValue.serverTimestamp(),
            "allergies" to FieldValue.delete(),
            "cursor" to Timestamp(1_778_000_000L, 5)
        )
    }
}
