package com.coparently.app.data.remote.firebase

import com.coparently.app.data.remote.firebase.RecordingFirestoreClient.Operation
import com.coparently.app.data.requests.RequestDeadlineDocument
import com.coparently.app.domain.changerequests.RequestDeadline
import com.coparently.app.domain.changerequests.RequestKind
import com.coparently.app.domain.changerequests.RequestStart
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [FirestoreRequestDeadlineDataSource]: the Firestore calls behind a request's clock (phase 3a).
 *
 * The recording client keeps each operation as the data source asked for it, so a different query
 * shape, a different way of reading the server's time or a write that is not the one the rules
 * accept turns a test red.
 */
class FirestoreRequestDeadlineDataSourceTest {

    private val client = RecordingFirestoreClient()
    private val dataSource = FirestoreRequestDeadlineDataSource(client)

    // ---- reading ------------------------------------------------------------------------------

    @Test
    fun `the family listener asks for the parent's clocks in that family and skips what it cannot read`() = runTest {
        client.queryAnswer = { listOf(stored(), stored(kind = "handover")) }

        assertEquals(
            "the unknown kind is left out, not guessed",
            listOf(clock()),
            dataSource.observeFamily(ME, FAMILY).first()
        )
        assertEquals(
            DocumentQuery("request_deadlines").whereArrayContains("participants", ME).whereEqualTo("familyId", FAMILY),
            client.operations.filterIsInstance<Operation.ObserveQuery>().single().query
        )
    }

    @Test
    fun `a listener error fails the flow, so the repository can retry it`() = runTest {
        client.queryFlow = { flow { throw RecordingFirestoreClient.Failure(FirestoreErrorCode.PERMISSION_DENIED) } }

        val failure = runCatching { dataSource.observeFamily(ME, FAMILY).first() }.exceptionOrNull()

        assertEquals(FirestoreErrorCode.PERMISSION_DENIED, failure?.let(client::errorCodeOf))
    }

    @Test
    fun `the open clocks are the parent's, from the date given, read once`() = runTest {
        client.queryAnswer = { listOf(stored(), stored(kind = "handover")) }

        assertEquals(listOf(clock()), dataSource.openFor(ME, SINCE))
        val read = client.operations.filterIsInstance<Operation.Query>().single()
        assertEquals(
            DocumentQuery("request_deadlines")
                .whereArrayContains("participants", ME)
                .whereGreaterThanOrEqualTo("answerByMillis", SINCE),
            read.query
        )
        assertEquals(ReadSource.DEFAULT, read.source)
    }

    @Test
    fun `a clock is read with the server's times as estimates, so a write not yet stamped still has one`() = runTest {
        // The stored data holds no time at all: only the estimate answers one.
        client.documents[PATH] = stored()

        assertEquals(clock(), dataSource.get(ID))
    }

    @Test
    fun `a server time that is not a timestamp reads as absent and does not throw in the listener`() = runTest {
        client.documents[PATH] = stored(createdAt = "yesterday")

        assertEquals(clock().copy(createdAtMillis = null), dataSource.get(ID))
    }

    @Test
    fun `a clock that does not exist reads as no clock, though the rules answer with a denial`() = runTest {
        client.failNext(RecordingFirestoreClient.Failure(FirestoreErrorCode.PERMISSION_DENIED))

        assertNull(dataSource.get(ID))
    }

    @Test
    fun `a document that is empty or unreadable reads as no clock`() = runTest {
        assertNull(dataSource.get(ID))

        client.documents[PATH] = stored(kind = "handover")
        assertNull(dataSource.get(ID))
    }

    @Test
    fun `any other failure to read a clock is not hidden as a missing one`() = runTest {
        client.failNext(RecordingFirestoreClient.Failure(FirestoreErrorCode.UNAVAILABLE))

        val outcome = runCatching { dataSource.get(ID) }

        val thrown = outcome.exceptionOrNull()
        assertTrue("Expected the read to fail; got ${outcome.getOrNull()}", thrown != null)
        assertEquals(FirestoreErrorCode.UNAVAILABLE, client.errorCodeOf(thrown!!))
    }

    // ---- writing ------------------------------------------------------------------------------

    @Test
    fun `a new clock is set with its fields and the server's time as its creation`() = runTest {
        val fields = RequestDeadlineDocument.createFields(
            RequestStart(RequestKind.CHANGE, "cr-1", FAMILY, "uidB", ANSWER_BY, capMillis = null),
            proposerUid = ME
        )

        dataSource.create(ID, fields)

        val set = client.operations.filterIsInstance<Operation.Set>().single()
        assertEquals("a create is a whole-document set", SetMerge.Overwrite, set.merge)
        assertEquals(fields.keys + "createdAt", set.data.keys)
        fields.forEach { (key, value) -> assertEquals(key, value, set.data[key]) }
        assertEquals(
            "the rule wants createdAt to be request.time, which only the server's clock can be",
            ServerValue.ServerTimestamp,
            set.data["createdAt"]
        )
    }

    @Test
    fun `an update writes the named fields and nothing else`() = runTest {
        client.put(PATH, mapOf("kind" to "change"))

        dataSource.update(ID, mapOf("seenAtMillis" to SEEN_AT))

        assertEquals(listOf(Operation.Update(PATH, mapOf("seenAtMillis" to SEEN_AT))), client.writes)
    }

    @Test
    fun `a last chance is its end and the server's time for when it was given`() = runTest {
        client.put(PATH, mapOf("kind" to "change"))

        dataSource.giveLastChance(ID, UNTIL)

        val update = client.writes.single() as Operation.Update
        assertEquals(setOf("lastChanceAt", "lastChanceUntilMillis"), update.fields.keys)
        assertEquals(ServerValue.ServerTimestamp, update.fields["lastChanceAt"])
        assertEquals(UNTIL, update.fields["lastChanceUntilMillis"])
    }

    @Test
    fun `a closure is the one outcome and the server's time for when`() = runTest {
        client.put(PATH, mapOf("kind" to "change"))

        dataSource.close(ID)

        val update = client.writes.single() as Operation.Update
        assertEquals(setOf("outcome", "closedAt"), update.fields.keys)
        assertEquals("no_answer", update.fields["outcome"])
        assertEquals(ServerValue.ServerTimestamp, update.fields["closedAt"])
    }

    @Test
    fun `a reminder shown is counted by an integer increment, not by a number this phone read`() = runTest {
        dataSource.reportShown(ID)

        assertEquals(listOf(Operation.Increment(PATH, "remindersShown", 1)), client.writes)
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

    /**
     * A stored clock: the document, with `createdAt` not yet stamped by the server, so it reads as
     * absent from the data and as [createdAt] from the estimate.
     */
    private fun stored(kind: String = "change", createdAt: Any? = FirestoreTimestamp(CREATED_AT / 1000, 0)) =
        StoredDocument(
            id = ID,
            data = mapOf(
                "kind" to kind,
                "ref" to "cr-1",
                "familyId" to FAMILY,
                "proposerUid" to ME,
                "responderUid" to "uidB",
                "participants" to listOf(ME, "uidB"),
                "answerByMillis" to ANSWER_BY
            ),
            estimated = { field -> if (field == "createdAt") createdAt else null }
        )

    private companion object {
        const val ME = "uidA"
        const val FAMILY = "uidA__uidB"
        const val ID = "change_uidA_cr-1"
        val PATH = DocumentPath("request_deadlines", ID)

        /** 2026-05-05T16:53:20Z. */
        const val CREATED_AT = 1_778_000_000_000L
        const val ANSWER_BY = CREATED_AT + 3 * 86_400_000L
        const val SINCE = CREATED_AT - 4 * 86_400_000L
        const val SEEN_AT = CREATED_AT + 600_000L
        const val UNTIL = ANSWER_BY + 86_400_000L
    }
}
