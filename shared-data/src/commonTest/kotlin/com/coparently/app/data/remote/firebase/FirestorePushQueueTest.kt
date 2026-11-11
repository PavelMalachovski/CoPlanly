package com.coparently.app.data.remote.firebase

import com.coparently.app.data.remote.firebase.RecordingFirestoreClient.Operation
import com.coparently.app.domain.family.FamilyKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Clock

/**
 * The iPhone's push queue (MON-24 phase 3, Task 6): the `notification_queue` document it writes is
 * the one the app's `FcmService.queueNotificationForUser` writes, key for key and type for type —
 * `targetUserId`, `data` stamped with the pair's family unless it names one, `createdAt` as epoch
 * millis in a `Long` and `status` "pending", and nothing else — because `firestore.rules` validates
 * the create and the Cloud Function that sends it reads those keys (CLAUDE.md item 15).
 */
class FirestorePushQueueTest {

    private val firestore = RecordingFirestoreClient()
    private val signedIn = SignedInAs("a")
    private val queue = FirestorePushQueue(firestore, signedIn)

    @Test
    fun `a push is one new queue document with the Android keys and the pair's family`() = runTest {
        val before = Clock.System.now().toEpochMilliseconds()

        val result = queue.queueNotificationForUser("b", mapOf(PushPayload.TYPE to PushPayload.EVENT_IMPORTANT))

        val after = Clock.System.now().toEpochMilliseconds()
        assertTrue(result.isSuccess)
        val write = assertIs<Operation.Set>(firestore.writes.single())
        assertEquals(DocumentPath("notification_queue", "auto-1"), write.path)
        assertEquals(SetMerge.Overwrite, write.merge)
        assertEquals(setOf("targetUserId", "data", "createdAt", "status"), write.data.keys)
        assertEquals("b", write.data["targetUserId"])
        assertEquals(
            mapOf(PushPayload.TYPE to PushPayload.EVENT_IMPORTANT, PushPayload.FAMILY_ID to FamilyKey.of("a", "b")),
            write.data["data"]
        )
        assertEquals("pending", write.data["status"])
        val createdAt = assertIs<Long>(write.data["createdAt"])
        assertTrue(createdAt in before..after, "createdAt $createdAt is not between $before and $after")
    }

    @Test
    fun `a payload that already names a family keeps it`() = runTest {
        val data = mapOf(PushPayload.TYPE to PushPayload.EVENT_IMPORTANT, PushPayload.FAMILY_ID to "c__d")

        queue.queueNotificationForUser("b", data)

        assertEquals(data, firestore.lastSet(DocumentPath("notification_queue", "auto-1"))["data"])
    }

    @Test
    fun `with nobody signed in the payload is written unstamped`() = runTest {
        signedIn.uid = null
        val data = mapOf(PushPayload.TYPE to PushPayload.EVENT_IMPORTANT)

        queue.queueNotificationForUser("b", data)

        assertEquals(data, firestore.lastSet(DocumentPath("notification_queue", "auto-1"))["data"])
    }

    @Test
    fun `a failed write is returned and never thrown`() = runTest {
        firestore.failNext(RecordingFirestoreClient.Failure(FirestoreErrorCode.PERMISSION_DENIED))

        val result = queue.queueNotificationForUser("b", mapOf(PushPayload.TYPE to PushPayload.EVENT_IMPORTANT))

        assertTrue(result.isFailure)
    }

    @Test
    fun `there is no token yet and keeping one writes nothing`() = runTest {
        assertNull(queue.getCurrentToken())

        val result = queue.updateUserToken("token")

        assertTrue(result.isSuccess)
        assertEquals(emptyList(), firestore.writes)
    }

    /** A [SignedInUser] whose uid the test sets. */
    private class SignedInAs(var uid: String?) : SignedInUser {
        override fun getCurrentUid(): String? = uid
        override fun getCurrentDisplayName(): String? = null
        override fun getCurrentEmail(): String? = null
        override fun getCurrentPhotoUrl(): String? = null
        override fun observeCurrentUid(): Flow<String?> = flowOf(uid)
        override fun getCurrentAccount(): SignedInAccount? = null
    }
}
