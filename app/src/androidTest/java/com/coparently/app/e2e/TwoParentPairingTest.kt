package com.coparently.app.e2e

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.coparently.app.domain.chat.ConversationKey
import com.coparently.app.domain.family.FamilyKey
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.tasks.await
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Pairing, end to end: a code minted on one phone, redeemed on the other through the deployed-
 * shape `acceptPairingInvitation` callable, and observed on both (CLAUDE.md item 11).
 *
 * The pairing itself runs in [TwoParentTest]'s `@Before` for every test in this package, so a
 * broken pairing fails everything — which is right, because nothing between two parents works
 * without it. This class states what a successful one must have left behind.
 */
@RunWith(AndroidJUnit4::class)
class TwoParentPairingTest : TwoParentTest() {

    @Test
    fun bothProfilesNameTheOtherParentAndTheFamilyHasTwoSlots() = runBlocking<Unit> {
        val familyId = FamilyKey.of(alice.uid, bob.uid)

        // Each parent reads the *other's* profile, which the `users` rule admits only to a partner.
        val bobAsSeenByAlice = checkNotNull(alice.userRepository.getRemoteUserProfile(bob.uid)) {
            "Alice cannot read Bob's profile"
        }
        val aliceAsSeenByBob = checkNotNull(bob.userRepository.getRemoteUserProfile(alice.uid)) {
            "Bob cannot read Alice's profile"
        }
        assertEquals(alice.uid, bobAsSeenByAlice.partnerId)
        assertEquals(bob.uid, aliceAsSeenByBob.partnerId)
        assertNotEquals(
            "the callable must give the two parents different slots",
            bobAsSeenByAlice.role,
            aliceAsSeenByBob.role
        )

        // `families/{id}` is written by the callable alone, and both members may read it.
        val family = bob.firestore.collection("families").document(familyId).get().await()
        assertTrue("families/$familyId was not created", family.exists())
        @Suppress("UNCHECKED_CAST")
        val slots = family.get("slots") as Map<String, String>
        assertEquals(setOf(alice.uid, bob.uid), slots.keys)
        assertEquals(aliceAsSeenByBob.role, slots[alice.uid])
        assertEquals(bobAsSeenByAlice.role, slots[bob.uid])
    }

    @Test
    fun bothPhonesMirrorThePairingIntoRoomAndShareOneConversation() = runBlocking<Unit> {
        // What a hundred and forty call sites read as "my co-parent" (SelectedFamilySource).
        assertEquals(bob.uid, alice.database.userDao().getUserById(alice.uid)?.partnerId)
        assertEquals(alice.uid, bob.database.userDao().getUserById(bob.uid)?.partnerId)
        assertEquals(bob.uid, alice.parentsSource.coParentUid())
        assertEquals(alice.uid, bob.parentsSource.coParentUid())

        // Both phones derived the same thread id without talking to each other (item 13), and the
        // post-pairing setup created it on the server.
        val conversationId = ConversationKey.of(alice.uid, bob.uid)
        assertNotNull(alice.database.messageDao().getConversationById(conversationId))
        assertNotNull(bob.database.messageDao().getConversationById(conversationId))
        assertTrue(EmulatorEnvironment.documentExists("conversations/$conversationId"))
    }
}
