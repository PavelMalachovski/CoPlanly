package com.coparently.app.e2e

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.coparently.app.data.remote.firebase.PushPayload
import com.coparently.app.domain.activity.ActivityKind
import com.coparently.app.domain.chat.ConversationKey
import com.coparently.app.domain.family.FamilyKey
import com.coparently.app.domain.stickers.ParentRole
import com.coparently.app.domain.stickers.ParentRoleAnswer
import com.coparently.app.domain.stickers.StickerKind
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Stickers between two phones (October 2026, owner decisions), through the production
 * `StickerStore` and `ParentRoles` against the real `firestore.rules`.
 *
 * What it proves:
 * - **A parent's sticker reaches the co-parent** with its `sticker_received` push, read back from
 *   `notification_queue` addressed to them, and the sender sees when it was seen.
 * - **The role is the parent's own answer**, stored and read back on their phone.
 * - **The sticker is a card in the pair's thread too** (owner decision, 25 October 2026), naming
 *   the sticker and its kind, on the receiver's phone.
 * - **A stranger can neither send one into the family nor read one.**
 *
 * What it cannot: the card and the sheet as drawn, and FCM's delivery of the push.
 */
@RunWith(AndroidJUnit4::class)
class TwoParentStickersTest : TwoParentTest() {

    private val familyId get() = FamilyKey.of(alice.uid, bob.uid)

    @Test
    fun aStickerReachesTheCoParentWithItsPushAndIsSeen() = runBlocking<Unit> {
        EmulatorEnvironment.step("stickers: Bob says his stickers call him dad")
        assertTrue(bob.parentRoles.setMine(ParentRole.DAD))
        val role = withTimeout(WAIT_MS) { bob.parentRoles.observeMine().first { it is ParentRoleAnswer.Answered } }
        assertEquals(ParentRoleAnswer.Answered(ParentRole.DAD), role)

        EmulatorEnvironment.step("stickers: Alice sends Bob a sticker")
        assertTrue(alice.stickerStore.send(familyId, StickerKind.GREAT_PARENT))
        val received = withTimeout(WAIT_MS) { bob.stickerStore.observeReceived(bob.uid).first { it.isNotEmpty() } }
        val sticker = received.single()
        assertEquals(StickerKind.GREAT_PARENT, sticker.kind)
        assertEquals(alice.uid, sticker.fromUid)
        assertEquals(familyId, sticker.familyId)

        val push = EmulatorEnvironment.awaitQueuedPush(bob.uid, PushPayload.STICKER_RECEIVED)
        assertEquals(familyId, push[PushPayload.FAMILY_ID])

        EmulatorEnvironment.step("stickers: the sticker is a card in Bob's thread with Alice")
        val card = withTimeout(WAIT_MS) {
            bob.messageRepository.observeMessages(ConversationKey.of(alice.uid, bob.uid))
                .first { messages -> messages.any { it.activity?.kind == ActivityKind.STICKER_SENT } }
                .first { it.activity?.kind == ActivityKind.STICKER_SENT }
        }
        assertEquals(sticker.id, card.activity?.entityId)
        assertEquals(StickerKind.GREAT_PARENT.wire, card.activity?.title)
        assertEquals(alice.uid, card.senderId)

        EmulatorEnvironment.step("stickers: Bob sees it, and Alice is told it was seen")
        bob.stickerStore.markSeen(sticker)
        withTimeout(WAIT_MS) {
            alice.stickerStore.observeSent(alice.uid).first { sent -> sent.any { it.seenAtMillis != null } }
        }
    }

    @Test
    fun aStrangerNeitherSendsIntoTheFamilyNorReadsItsStickers() = runBlocking<Unit> {
        assertTrue(alice.stickerStore.send(familyId, StickerKind.THANK_YOU))
        val carol = newParent("Carol")

        EmulatorEnvironment.step("stickers: Carol tries to send into Alice and Bob's family")
        assertFalse(carol.stickerStore.send(familyId, StickerKind.THANK_YOU))

        EmulatorEnvironment.step("stickers: Carol tries to read Bob's stickers")
        val failure = runCatching {
            carol.firestore.collection("stickers").whereEqualTo("to", bob.uid).get().await()
        }.exceptionOrNull()
        assertTrue(
            "a stranger's read of Bob's stickers was not refused (got $failure)",
            failure is FirebaseFirestoreException &&
                failure.code == FirebaseFirestoreException.Code.PERMISSION_DENIED
        )
    }

    private companion object {
        const val WAIT_MS = 30_000L
    }
}
