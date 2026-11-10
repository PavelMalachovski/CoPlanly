package com.coparently.app.data.stickers

import com.coparently.app.data.remote.firebase.DocumentPath
import com.coparently.app.data.remote.firebase.DocumentQuery
import com.coparently.app.data.remote.firebase.FirestoreErrorCode
import com.coparently.app.data.remote.firebase.PushPayload
import com.coparently.app.data.remote.firebase.PushQueue
import com.coparently.app.data.remote.firebase.RecordingFirestoreClient
import com.coparently.app.data.remote.firebase.RecordingFirestoreClient.Operation
import com.coparently.app.data.remote.firebase.ServerValue
import com.coparently.app.data.remote.firebase.SetMerge
import com.coparently.app.data.remote.firebase.SignedInUser
import com.coparently.app.data.remote.firebase.StoredDocument
import com.coparently.app.domain.activity.ActivityAnnouncer
import com.coparently.app.domain.repository.UserRepository
import com.coparently.app.domain.stickers.Sticker
import com.coparently.app.domain.stickers.StickerKind
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The stickers between parents (CLAUDE.md item 47) through the Firestore seam since MON-24 phase
 * 2 (Task 8d): the same document under an id made on the phone, the same query
 * keyed on the reader, the server's clock for `createdAt` and `seenAt`, and a failed listener read
 * as nothing rather than as an error.
 */
class StickerStoreTest {

    private val firestore = RecordingFirestoreClient()
    private val auth = mockk<SignedInUser> { every { getCurrentUid() } returns ME }
    private val userRepository = mockk<UserRepository> {
        coEvery { getCurrentUser() } returns mockk(relaxed = true) { every { name } returns "Alice" }
    }
    private val pushes = mockk<PushQueue> {
        coEvery { queueNotificationForUser(any(), any()) } returns Result.success(Unit)
    }
    private val announcer = mockk<ActivityAnnouncer>(relaxed = true)
    private val store = StickerStore(firestore, auth, userRepository, pushes, announcer)

    @Test
    fun `a sent sticker is stored under a new id with the server's clock and pushed`() = runTest {
        assertTrue(store.send(FAMILY, StickerKind.THANK_YOU))

        val set = firestore.operations.filterIsInstance<Operation.Set>().single()
        assertEquals(DocumentPath("stickers", "auto-1"), set.path)
        assertEquals(SetMerge.Overwrite, set.merge)
        assertEquals(
            mapOf(
                "to" to BOB,
                "from" to ME,
                "participants" to listOf(ME, BOB),
                "familyId" to FAMILY,
                "kind" to "thank_you",
                "createdAt" to ServerValue.ServerTimestamp
            ),
            set.data
        )
        coVerify(exactly = 1) {
            pushes.queueNotificationForUser(
                BOB,
                mapOf(PushPayload.TYPE to PushPayload.STICKER_RECEIVED, PushPayload.ACTOR to "Alice")
            )
        }
        val card = store.cardFor("auto-1", StickerKind.THANK_YOU)
        coVerify(exactly = 1) { announcer.announce(card, "Alice", false, BOB) }
    }

    @Test
    fun `a sticker the store could not write is neither pushed nor announced`() = runTest {
        firestore.failNext(RecordingFirestoreClient.Failure(FirestoreErrorCode.PERMISSION_DENIED))

        assertEquals(false, store.send(FAMILY, StickerKind.THANK_YOU))
        coVerify(exactly = 0) { pushes.queueNotificationForUser(any(), any()) }
        coVerify(exactly = 0) { announcer.announce(any(), any(), any(), any()) }
    }

    @Test
    fun `an app's kind or a family without this parent sends nothing`() = runTest {
        assertEquals(false, store.send(FAMILY, StickerKind.ON_TIME))
        assertEquals(false, store.send("x", StickerKind.THANK_YOU))
        assertTrue(firestore.operations.isEmpty())
    }

    @Test
    fun `seen is the server's clock written once and only by the receiver`() = runTest {
        store.markSeen(sticker(to = ME, seenAt = null))
        store.markSeen(sticker(to = ME, seenAt = 5L))
        store.markSeen(sticker(to = BOB, seenAt = null))

        assertEquals(
            listOf(Operation.UpdateField(DocumentPath("stickers", "s1"), "seenAt", null, ServerValue.ServerTimestamp)),
            firestore.writes
        )
    }

    @Test
    fun `received stickers are read by the receiver's key and newest first`() = runTest {
        firestore.queryAnswer = {
            listOf(
                StoredDocument("old", mapOf("to" to ME, "from" to BOB, "kind" to "thank_you", "createdAt" to 1L)),
                StoredDocument("new", mapOf("to" to ME, "from" to BOB, "kind" to "thank_you", "createdAt" to 9L)),
                StoredDocument("odd", mapOf("to" to ME, "from" to BOB, "kind" to "newer_kind"))
            )
        }

        assertEquals(listOf("new", "old"), store.observeReceived(ME).first().map { it.id })
        assertEquals(
            Operation.ObserveQuery(DocumentQuery("stickers").whereEqualTo("to", ME)),
            firestore.operations.single()
        )
    }

    @Test
    fun `a failed listener reads as no stickers`() = runTest {
        firestore.queryFlow = { flow { throw RecordingFirestoreClient.Failure(FirestoreErrorCode.PERMISSION_DENIED) } }

        assertEquals(emptyList<Sticker>(), store.observeSent(ME).first())
        assertEquals(
            Operation.ObserveQuery(DocumentQuery("stickers").whereEqualTo("from", ME)),
            firestore.operations.single()
        )
    }

    private fun sticker(to: String, seenAt: Long?) = Sticker(
        id = "s1",
        toUid = to,
        fromUid = BOB,
        familyId = FAMILY,
        kind = StickerKind.THANK_YOU,
        createdAtMillis = 1L,
        seenAtMillis = seenAt
    )

    private companion object {
        const val ME = "alice"
        const val BOB = "bob"
        const val FAMILY = "alice__bob"
    }
}
