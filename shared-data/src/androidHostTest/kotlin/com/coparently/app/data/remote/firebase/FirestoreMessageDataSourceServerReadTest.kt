package com.coparently.app.data.remote.firebase

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * That [FirestoreMessageDataSource.fetchMessageIds] reads the **server**, never the offline
 * cache.
 *
 * This one-shot read is the only thing that can discover a message which reached Firestore under
 * a legacy conversation id but never made it into this device's Room, and
 * `ConversationMigrator` archives the legacy conversation once it believes it has seen them all.
 * With offline persistence on, a `ReadSource.DEFAULT` read may be answered from the local cache —
 * which, for a legacy id nothing ever listens on, holds nothing. The read would then succeed with
 * an empty result and the migration would archive the thread on it, stranding the very message it
 * was looking for.
 *
 * Both tests below pass under `ReadSource.DEFAULT` semantics only if the cache happens to agree
 * with the server, so the client answers the two apart: the cache is empty, the server is not.
 */
class FirestoreMessageDataSourceServerReadTest {

    private val client = RecordingFirestoreClient()
    private lateinit var dataSource: FirestoreMessageDataSource

    @Before
    fun setUp() {
        dataSource = FirestoreMessageDataSource(client)
    }

    @Test
    fun `it returns what the server holds, not what the empty offline cache holds`() = runTest {
        client.queryAnswer = { query ->
            // The offline cache holds nothing for a legacy conversation id — nothing in the app has
            // ever attached a listener to one. Only a server read sees the messages.
            val source = client.operations.filterIsInstance<RecordingFirestoreClient.Operation.Query>().last().source
            if (query == legacyThread() && source == ReadSource.SERVER) {
                listOf(StoredDocument("msg-1", mapOf()), StoredDocument("msg-remote-only", mapOf()))
            } else {
                emptyList()
            }
        }

        assertEquals(setOf("msg-1", "msg-remote-only"), dataSource.fetchMessageIds(LEGACY_ID))
    }

    @Test
    fun `an offline device fails the read instead of reporting an empty thread`() = runTest {
        client.failNext(RecordingFirestoreClient.Failure(FirestoreErrorCode.UNAVAILABLE))

        // Not an empty set: "there are no remote messages" and "this device could not find out"
        // must not look the same to ConversationMigrator, which archives the legacy conversation
        // on the first of those and retries on the second.
        val outcome = runCatching { dataSource.fetchMessageIds(LEGACY_ID) }

        val thrown = outcome.exceptionOrNull()
        assertTrue("Expected the server read to fail; got ${outcome.getOrNull()}", thrown != null)
        assertEquals(FirestoreErrorCode.UNAVAILABLE, client.errorCodeOf(thrown!!))
    }

    private fun legacyThread() = DocumentQuery("messages").whereEqualTo("conversationId", LEGACY_ID)

    private companion object {
        const val LEGACY_ID = "random-uuid-legacy"
    }
}
