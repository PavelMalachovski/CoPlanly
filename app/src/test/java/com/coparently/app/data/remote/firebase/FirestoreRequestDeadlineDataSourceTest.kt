package com.coparently.app.data.remote.firebase

import app.cash.turbine.test
import com.coparently.app.data.requests.RequestDeadlineDocument
import com.coparently.app.domain.changerequests.RequestDeadline
import com.coparently.app.domain.changerequests.RequestKind
import com.coparently.app.domain.changerequests.RequestStart
import com.google.android.gms.tasks.Tasks
import com.google.firebase.Timestamp
import com.google.firebase.firestore.CollectionReference
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.DocumentSnapshot.ServerTimestampBehavior
import com.google.firebase.firestore.EventListener
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.FirebaseFirestoreException.Code
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.QuerySnapshot
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * [FirestoreRequestDeadlineDataSource]: the Firestore calls behind a request's clock (phase 3a).
 *
 * Firestore is mocked down to the calls the class makes, so a different query shape, a different
 * way of reading the server's time or a write that is not the one the rules accept turns a test
 * red: the mock has no answer for a call it was not told to expect.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class FirestoreRequestDeadlineDataSourceTest {

    private lateinit var collection: CollectionReference
    private lateinit var document: DocumentReference
    private lateinit var dataSource: FirestoreRequestDeadlineDataSource

    @Before
    fun setUp() {
        collection = mockk()
        document = mockk()
        every { collection.document(ID) } returns document
        val firestore = mockk<FirebaseFirestore>()
        every { firestore.collection("request_deadlines") } returns collection
        dataSource = FirestoreRequestDeadlineDataSource(firestore)
    }

    // ---- reading ------------------------------------------------------------------------------

    @Test
    fun `the family listener asks for the parent's clocks in that family and skips what it cannot read`() = runTest {
        val listeners = mutableListOf<EventListener<QuerySnapshot>>()
        val byParticipant = mockk<Query>()
        val byFamily = mockk<Query>()
        every { collection.whereArrayContains("participants", ME) } returns byParticipant
        every { byParticipant.whereEqualTo("familyId", FAMILY) } returns byFamily
        every { byFamily.addSnapshotListener(any<EventListener<QuerySnapshot>>()) } answers {
            listeners += firstArg<EventListener<QuerySnapshot>>()
            mockk<ListenerRegistration>(relaxed = true)
        }

        dataSource.observeFamily(ME, FAMILY).test {
            listeners.single().onEvent(snapshotOf(stored(), stored(kind = "handover")), null)

            assertEquals("the unknown kind is left out, not guessed", listOf(clock()), awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `a listener error fails the flow, so the repository can retry it`() = runTest {
        val listeners = mutableListOf<EventListener<QuerySnapshot>>()
        val byParticipant = mockk<Query>()
        val byFamily = mockk<Query>()
        every { collection.whereArrayContains("participants", ME) } returns byParticipant
        every { byParticipant.whereEqualTo("familyId", FAMILY) } returns byFamily
        every { byFamily.addSnapshotListener(any<EventListener<QuerySnapshot>>()) } answers {
            listeners += firstArg<EventListener<QuerySnapshot>>()
            mockk<ListenerRegistration>(relaxed = true)
        }

        dataSource.observeFamily(ME, FAMILY).test {
            listeners.single().onEvent(null, denied())

            val failure = awaitError()
            assertTrue(failure is FirebaseFirestoreException)
            assertEquals(Code.PERMISSION_DENIED, (failure as FirebaseFirestoreException).code)
        }
    }

    @Test
    fun `the open clocks are the parent's, from the date given, read once`() = runTest {
        val byParticipant = mockk<Query>()
        val byDeadline = mockk<Query>()
        every { collection.whereArrayContains("participants", ME) } returns byParticipant
        every { byParticipant.whereGreaterThanOrEqualTo("answerByMillis", SINCE) } returns byDeadline
        every { byDeadline.get() } returns Tasks.forResult(snapshotOf(stored(), stored(kind = "handover")))

        assertEquals(listOf(clock()), dataSource.openFor(ME, SINCE))
    }

    @Test
    fun `a clock is read with the server's times as estimates, so a write not yet stamped still has one`() = runTest {
        // Only the estimate behaviour is stubbed: reading the times any other way has no answer.
        every { document.get() } returns Tasks.forResult(stored())

        assertEquals(clock(), dataSource.get(ID))
    }

    @Test
    fun `a server time that is not a timestamp reads as absent and does not throw in the listener`() = runTest {
        every { document.get() } returns Tasks.forResult(stored(createdAt = "yesterday"))

        assertEquals(clock().copy(createdAtMillis = null), dataSource.get(ID))
    }

    @Test
    fun `a clock that does not exist reads as no clock, though the rules answer with a denial`() = runTest {
        every { document.get() } returns Tasks.forException(denied())

        assertNull(dataSource.get(ID))
    }

    @Test
    fun `a document that is empty or unreadable reads as no clock`() = runTest {
        every { document.get() } returns Tasks.forResult(storedDocument(data = null))
        assertNull(dataSource.get(ID))

        every { document.get() } returns Tasks.forResult(stored(kind = "handover"))
        assertNull(dataSource.get(ID))
    }

    @Test
    fun `any other failure to read a clock is not hidden as a missing one`() = runTest {
        every { document.get() } returns Tasks.forException(unavailable())

        val outcome = runCatching { dataSource.get(ID) }

        val thrown = outcome.exceptionOrNull()
        assertTrue("Expected the read to fail; got ${outcome.getOrNull()}", thrown is FirebaseFirestoreException)
        assertEquals(Code.UNAVAILABLE, (thrown as FirebaseFirestoreException).code)
    }

    // ---- writing ------------------------------------------------------------------------------

    @Test
    fun `a new clock is set with its fields and the server's time as its creation`() = runTest {
        val written = slot<Any>()
        every { document.set(capture(written)) } returns Tasks.forResult(null)
        val fields = RequestDeadlineDocument.createFields(
            RequestStart(RequestKind.CHANGE, "cr-1", FAMILY, "uidB", ANSWER_BY, capMillis = null),
            proposerUid = ME
        )

        dataSource.create(ID, fields)

        val map = written.captured as Map<*, *>
        assertEquals(fields.keys + "createdAt", map.keys)
        fields.forEach { (key, value) -> assertEquals(key, value, map[key]) }
        assertEquals(
            "the rule wants createdAt to be request.time, which only the server's clock can be",
            FieldValue.serverTimestamp().javaClass,
            map["createdAt"]?.javaClass
        )
    }

    @Test
    fun `an update writes the named fields and nothing else`() = runTest {
        val updated = slot<Map<String, Any>>()
        every { document.update(capture(updated)) } returns Tasks.forResult(null)

        dataSource.update(ID, mapOf("seenAtMillis" to SEEN_AT))

        assertEquals(mapOf<String, Any>("seenAtMillis" to SEEN_AT), updated.captured)
    }

    @Test
    fun `a last chance is its end and the server's time for when it was given`() = runTest {
        val updated = slot<Map<String, Any>>()
        every { document.update(capture(updated)) } returns Tasks.forResult(null)

        dataSource.giveLastChance(ID, UNTIL)

        assertEquals(setOf("lastChanceAt", "lastChanceUntilMillis"), updated.captured.keys)
        assertEquals(FieldValue.serverTimestamp().javaClass, updated.captured["lastChanceAt"]?.javaClass)
        assertEquals(UNTIL, updated.captured["lastChanceUntilMillis"])
    }

    @Test
    fun `a closure is the one outcome and the server's time for when`() = runTest {
        val updated = slot<Map<String, Any>>()
        every { document.update(capture(updated)) } returns Tasks.forResult(null)

        dataSource.close(ID)

        assertEquals(setOf("outcome", "closedAt"), updated.captured.keys)
        assertEquals("no_answer", updated.captured["outcome"])
        assertEquals(FieldValue.serverTimestamp().javaClass, updated.captured["closedAt"]?.javaClass)
    }

    @Test
    fun `a reminder shown is counted by an increment, not by a number this phone read`() = runTest {
        val updated = slot<Map<String, Any>>()
        every { document.update(capture(updated)) } returns Tasks.forResult(null)

        dataSource.reportShown(ID)

        assertEquals(setOf("remindersShown"), updated.captured.keys)
        assertEquals(FieldValue.increment(1).javaClass, updated.captured["remindersShown"]?.javaClass)
    }

    // ---- fixtures ----------------------------------------------------------------------------

    /** The clock [stored] holds. */
    private fun clock() = RequestDeadline(
        id = ID,
        kind = RequestKind.CHANGE,
        ref = "cr-1",
        familyId = FAMILY,
        proposerUid = ME,
        responderUid = "uidB",
        answerByMillis = ANSWER_BY,
        createdAtMillis = CREATED_AT
    )

    /** A stored clock: the document, and the three server times the way the SDK is asked for them. */
    private fun stored(kind: String = "change", createdAt: Any? = Timestamp(CREATED_AT / 1000, 0)) =
        storedDocument(
            data = mapOf(
                "kind" to kind,
                "ref" to "cr-1",
                "familyId" to FAMILY,
                "proposerUid" to ME,
                "responderUid" to "uidB",
                "participants" to listOf(ME, "uidB"),
                "answerByMillis" to ANSWER_BY
            ),
            createdAt = createdAt
        )

    private fun storedDocument(data: Map<String, Any>?, createdAt: Any? = Timestamp(CREATED_AT / 1000, 0)) =
        mockk<DocumentSnapshot> {
            every { id } returns ID
            every { this@mockk.data } returns data
            every { get("createdAt", ServerTimestampBehavior.ESTIMATE) } returns createdAt
            every { get("lastChanceAt", ServerTimestampBehavior.ESTIMATE) } returns null
            every { get("closedAt", ServerTimestampBehavior.ESTIMATE) } returns null
        }

    private fun snapshotOf(vararg documents: DocumentSnapshot): QuerySnapshot =
        mockk { every { this@mockk.documents } returns documents.toList() }

    private fun denied() = FirebaseFirestoreException("Missing or insufficient permissions.", Code.PERMISSION_DENIED)

    private fun unavailable() = FirebaseFirestoreException(
        "Failed to get document because the client is offline.",
        Code.UNAVAILABLE
    )

    private companion object {
        const val ME = "uidA"
        const val FAMILY = "uidA__uidB"
        const val ID = "change_uidA_cr-1"

        /** 2026-05-05T16:53:20Z. */
        const val CREATED_AT = 1_778_000_000_000L
        const val ANSWER_BY = CREATED_AT + 3 * 86_400_000L
        const val SINCE = CREATED_AT - 4 * 86_400_000L
        const val SEEN_AT = CREATED_AT + 600_000L
        const val UNTIL = ANSWER_BY + 86_400_000L
    }
}
