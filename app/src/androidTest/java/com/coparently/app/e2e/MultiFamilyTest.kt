package com.coparently.app.e2e

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.coparently.app.domain.chat.ConversationKey
import com.coparently.app.domain.family.FamilyKey
import com.coparently.app.domain.model.Event
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDateTime
import java.util.UUID

/**
 * One parent in two families (M-8): what Alice writes while the Carol family is selected belongs
 * to that family — its audience, its `familyId` and the thread its announcement lands in — and
 * none of it reaches Bob.
 *
 * **What this does not assert.** The Chat tab following the selection (`ChatPartnerSource`, the
 * fix for the old "Chat follows the first co-parent") is a screen, so `OnScreenFamiliesTest`
 * drives it on Alice's real app. The conversation id checked here is the one every writer derives
 * from the Room projection — `ActivityAnnouncer`, and the chat thread the Chat tab opens.
 */
@RunWith(AndroidJUnit4::class)
class MultiFamilyTest : TwoParentTest() {

    @Test
    fun whatAliceWritesInTheCarolFamilyStaysInTheCarolFamily() = runBlocking<Unit> {
        val carol = newParent("Carol")
        // Alice invites a second co-parent. Her own snapshot keeps naming Bob — the server pins
        // `partnerId` to the first relationship and adds Carol to `partnerIds` — so her phone
        // learns the new family from the set, through her own pairing listener, with no sync run
        // by hand (tester report #4; this test used to copy the server's set into Room itself).
        val invite = alice.pairingRepository.createOrReuseInviteCode().getOrThrow()
        carol.pairingRepository.redeem(invite.code).getOrThrow()
        carol.awaitPairedWith(alice.uid)
        alice.awaitFamilyWith(carol.uid)

        val carolFamily = FamilyKey.of(alice.uid, carol.uid)
        assertEquals(2, alice.selectedFamilySource.families().size)
        assertEquals(
            "a new family took over Alice's phone",
            FamilyKey.of(alice.uid, bob.uid),
            alice.selectedFamilySource.selected()?.familyId
        )
        assertNotNull(alice.selectedFamilySource.select(carolFamily))
        assertEquals(carol.uid, alice.parentsSource.coParentUid())

        val event = Event(
            id = UUID.randomUUID().toString(),
            title = "Carol's parents' evening",
            startDateTime = LocalDateTime.now().plusDays(2),
            eventType = "school",
            parentOwner = alice.database.userDao().getUserById(alice.uid)?.role ?: "mom",
            createdAt = LocalDateTime.now(),
            updatedAt = LocalDateTime.now()
        )
        alice.eventRepository.insertEvent(event)

        // The event: Carol's family's audience and id, readable by Carol, invisible to Bob.
        val carolsFeed = carol.eventDataSource.observeEventsSharedWith(carol.uid, null).first()
        val document = carolsFeed.documents.firstOrNull { it["id"] == event.id }
        assertNotNull("Carol cannot see the event Alice made in their family", document)
        assertEquals(carolFamily, document!!["familyId"])
        assertEquals(setOf(alice.uid, carol.uid), (document["sharedWith"] as List<*>).toSet())
        val bobsFeed = bob.eventDataSource.observeEventsSharedWith(bob.uid, null).first()
        assertFalse(bobsFeed.documents.any { it["id"] == event.id })

        // The announcement: in the Alice–Carol thread, and not in the Alice–Bob one.
        val carolThread = ConversationKey.of(alice.uid, carol.uid)
        withTimeout(EmulatorParent.WAIT_MS) {
            carol.messageRepository.observeMessages(carolThread).first { messages ->
                messages.any { event.id in it.attachments }
            }
        }
        val bobThread = bob.firestore.collection("messages")
            .whereEqualTo("conversationId", ConversationKey.of(alice.uid, bob.uid))
            .orderBy("timestamp")
            .get()
            .await()
        assertTrue(
            "Alice's announcement leaked into the Bob thread",
            bobThread.documents.none { (it.get("attachments") as? List<*>).orEmpty().contains(event.id) }
        )
    }
}
