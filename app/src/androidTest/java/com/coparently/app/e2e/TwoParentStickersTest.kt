package com.coparently.app.e2e

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.coparently.app.data.remote.firebase.PushPayload
import com.coparently.app.domain.changerequests.RequestKind
import com.coparently.app.domain.changerequests.RequestStart
import com.coparently.app.domain.family.FamilyKey
import com.coparently.app.domain.stickers.ParentRole
import com.coparently.app.domain.stickers.ParentRoleAnswer
import com.coparently.app.domain.stickers.StickerAwards
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
import java.time.Instant
import java.time.ZoneId

/**
 * Stickers between two phones (October 2026, owner decisions), through the production
 * `StickerStore`, `ParentRoles` and `StickerAwarder` against the real `firestore.rules`.
 *
 * What it proves:
 * - **A parent's sticker reaches the co-parent** with its `sticker_received` push, read back from
 *   `notification_queue` addressed to them, and the sender sees when it was seen.
 * - **The role is the parent's own answer**, stored and read back on their phone.
 * - **A stranger can neither send one into the family nor read one.**
 * - **An answer before the deadline gives the app's on-time sticker, once a week**: a second answer
 *   the same week gives nothing new, and the app's sticker pushes nobody.
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

    @Test
    fun anAnswerBeforeTheDeadlineGivesTheOnTimeStickerOnceAWeek() = runBlocking<Unit> {
        EmulatorEnvironment.step("stickers: Alice's request waits on Bob, with a day to answer")
        val now = System.currentTimeMillis()
        val clock = checkNotNull(
            alice.requestDeadlineRepository.start(
                RequestStart(
                    kind = RequestKind.SWAP,
                    ref = "stickers-g1",
                    familyId = familyId,
                    responderUid = bob.uid,
                    answerByMillis = now + DAY_MS,
                    capMillis = now + 2 * DAY_MS
                )
            )
        ) { "the rules refused the request's clock" }

        EmulatorEnvironment.step("stickers: Bob answers in time, twice in one week")
        bob.stickerAwarder.onAnswered(clock.id)
        bob.stickerAwarder.onAnswered(clock.id)
        val week = StickerAwards.isoWeek(Instant.ofEpochMilli(now).atZone(ZoneId.systemDefault()).toLocalDate())
        val received = withTimeout(WAIT_MS) {
            bob.stickerStore.observeReceived(bob.uid).first { it.isNotEmpty() }
        }
        assertEquals(listOf(StickerAwards.appStickerId(bob.uid, StickerKind.ON_TIME, week)), received.map { it.id })
        assertEquals(null, received.single().fromUid)
        assertTrue(
            "the app's sticker pushed somebody",
            EmulatorEnvironment.queryAsAdmin("notification_queue", "targetUserId", bob.uid)
                .mapNotNull { it["data"] as? Map<*, *> }
                .none { it["type"] == PushPayload.STICKER_RECEIVED }
        )
    }

    private companion object {
        const val WAIT_MS = 30_000L
        const val DAY_MS = 24L * 60 * 60 * 1000
    }
}
