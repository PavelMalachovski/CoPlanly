package com.coparently.app.data.repository

import app.cash.turbine.test
import com.coparently.app.domain.model.PairingState
import com.coparently.app.domain.model.PartnerSummary
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.EventListener
import com.google.firebase.firestore.FirebaseFirestoreException
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The pairing state as the realtime listeners report it, and what the repository mirrors from it
 * into Room: the link, the co-parent set, the co-parent's live profile.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class PairingRepositoryImplTest : PairingRepositoryImplFixture() {

    // ---- observePairingState --------------------------------------------

    @Test
    fun `observePairingState emits Loading then NotPaired then Paired as the link is made`() = runTest {
        val listeners = stubRealtimeListeners()

        repository.observePairingState().test {
            assertEquals(PairingState.Loading, awaitItem())
            runCurrent()

            listeners.emitInvites()
            listeners.emitUser(userDoc(partnerId = ""))
            assertEquals(PairingState.NotPaired(activeInvite = null, incoming = emptyList()), awaitItem())

            listeners.emitUser(userDoc(partnerId = "u2", pairedAt = 123L))
            listeners.emitPartner(partnerDoc())
            assertEquals(
                PairingState.Paired(
                    PartnerSummary(id = "u2", name = "", email = "", pairedSinceMillis = 123L)
                ),
                awaitItem()
            )

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `the paired partner carries the avatar stored in their profile`() = runTest {
        // Same field, same path as this user's own avatar — the co-parent's card would look
        // accidental showing an initial next to a photo of you. It stays null until their
        // phone runs a build whose `ensureProfile` writes one, so the card keeps its
        // initial-letter fallback for as long as the other device is behind.
        val listeners = stubRealtimeListeners()

        repository.observePairingState().test {
            assertEquals(PairingState.Loading, awaitItem())
            runCurrent()
            listeners.emitInvites()
            listeners.emitUser(userDoc(partnerId = "u2", pairedAt = 123L))
            listeners.emitPartner(
                partnerDoc(name = "Bob Dvorak", email = "bob@example.com", photoUrl = PARTNER_PHOTO)
            )

            assertEquals(
                PairingState.Paired(
                    PartnerSummary(
                        id = "u2",
                        name = "Bob Dvorak",
                        email = "bob@example.com",
                        pairedSinceMillis = 123L,
                        photoUrl = PARTNER_PHOTO
                    )
                ),
                awaitItem()
            )
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `observePairingState mirrors the Paired transition into Room`() = runTest {
        // Everything outside the pairing screen (chat, expenses, budgets, event sync) reads
        // the link from Room, so the observed transition — not the redeem() call, which only
        // ever runs on the accepting device — is what has to write it.
        //
        // What it writes is the **list**. `partnerId` stopped meaning "my co-parent" and
        // started meaning "the family this device is showing", and only `SelectedFamilySource`
        // writes that — mirroring the observed partner onto it here would drag a parent
        // looking at one family into another the moment the other one's state re-emitted.
        coEvery { userDao.getUserById("user-a") } returns userEntity(partnerId = null)
        val listeners = stubRealtimeListeners()

        repository.observePairingState().test {
            assertEquals(PairingState.Loading, awaitItem())
            runCurrent()
            listeners.emitInvites()
            listeners.emitUser(userDoc(partnerId = "u2", pairedAt = 123L))
            listeners.emitPartner(partnerDoc())
            awaitItem()
            cancelAndIgnoreRemainingEvents()
        }

        coVerify {
            userDao.updateUser(userEntity(partnerId = null).copy(partnerIdsJson = "[\"u2\"]"))
        }
        // The legacy-conversation merge runs immediately after the canonical conversation is
        // ensured, inside the same guarded block, so a merge failure can never surface as a
        // failed pairing.
        coVerify { messageRepository.ensureConversation("user-a", "u2", any()) }
        coVerify { conversationMigrator.mergeLegacyConversations("user-a", "u2") }
    }

    @Test
    fun `a newly observed co-parent asks for an immediate sync`() = runTest {
        // The mirror above says *that* the phone is paired; the sync is what fetches anything
        // the pairing entitles it to. On the inviter's phone that run widens the audience of
        // every record created while unpaired; on the accepter's it downloads them. Waiting
        // for the fifteen-minute tick left the onboarding wizard — which pairs first precisely
        // so the second parent inherits the first one's records — on an empty child step.
        coEvery { userDao.getUserById("user-a") } returns userEntity(partnerId = null)
        val listeners = stubRealtimeListeners()

        repository.observePairingState().test {
            assertEquals(PairingState.Loading, awaitItem())
            runCurrent()
            listeners.emitInvites()
            listeners.emitUser(userDoc(partnerId = "u2", pairedAt = 123L))
            listeners.emitPartner(partnerDoc())
            awaitItem()
            cancelAndIgnoreRemainingEvents()
        }

        verify(exactly = 1) { syncRequester.requestSyncNow() }
    }

    @Test
    fun `a co-parent the server adds beside the first is mirrored at once, without a switch`() = runTest {
        // Tester report #4. A parent already paired with u2 shares their code and u3 redeems it.
        // The server adds u3 to `partnerIds` and keeps `partnerId` on u2, so the state this phone
        // shows still names u2 — and the mirror used to watch that one field, so nothing happened
        // here: no sync, no family in Room for the switcher, no thread, until a sync tick or a
        // push happened along. The co-parent the set gained is what a transition is now about.
        coEvery { userDao.getUserById("user-a") } returns
            userEntity(partnerId = "u2").copy(partnerIdsJson = "[\"u2\"]")
        val listeners = stubRealtimeListeners()

        repository.observePairingState().test {
            assertEquals(PairingState.Loading, awaitItem())
            runCurrent()
            listeners.emitInvites()
            listeners.emitUser(userDoc(partnerId = "u2", pairedAt = 123L))
            listeners.emitPartner(partnerDoc(name = "Barbora", role = "dad"))
            awaitItem()
            // The first link a process sees asks for one sync, as it always has.
            verify(exactly = 1) { syncRequester.requestSyncNow() }

            listeners.emitUser(userDoc(partnerId = "u2", pairedAt = 456L, partnerIds = listOf("u2", "u3")))
            runCurrent()
            listeners.emitPartner(partnerDoc(name = "Barbora", role = "dad"))
            assertEquals("u2", (awaitItem() as PairingState.Paired).partner.id)

            verify(exactly = 2) { syncRequester.requestSyncNow() }
            coVerify { userDao.updateUser(match { it.partnerIdsJson == "[\"u2\",\"u3\"]" }) }
            coVerify(exactly = 2) { selectedFamilySource.reconcile() }
            coVerify { messageRepository.ensureConversation("user-a", "u3", any()) }

            // The same set again — the first co-parent renaming themselves — does nothing more.
            listeners.emitPartner(partnerDoc(name = "Barbara", role = "dad"))
            assertEquals("Barbara", (awaitItem() as PairingState.Paired).partner.name)
            cancelAndIgnoreRemainingEvents()
        }

        verify(exactly = 2) { syncRequester.requestSyncNow() }
        coVerify(exactly = 1) { userDao.updateUser(any()) }
        coVerify(exactly = 2) { selectedFamilySource.reconcile() }
        coVerify(exactly = 1) { messageRepository.ensureConversation("user-a", "u3", any()) }
        // A new family joins the switcher; it does not take over the screen.
        coVerify(exactly = 0) { selectedFamilySource.select(any()) }
    }

    @Test
    fun `a co-parent the pairing state does not carry is read from their own document`() = runTest {
        // What a screen showing the second family names: their profile, live, never the first's.
        val listener = slot<EventListener<DocumentSnapshot>>()
        val document = mockk<DocumentReference>(relaxed = true)
        every { usersCollection.document("u3") } returns document
        every { document.addSnapshotListener(capture(listener)) } returns mockk(relaxed = true)

        repository.observeCoParentProfile("u3").test {
            runCurrent()
            listener.captured.onEvent(partnerDoc(name = "Pavel", role = "dad", colorCode = "hue1|200"), null)

            assertEquals(
                PartnerSummary(
                    id = "u3",
                    name = "Pavel",
                    email = "",
                    pairedSinceMillis = null,
                    role = "dad",
                    colorCode = "hue1|200"
                ),
                awaitItem()
            )
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `an ended link asks for no sync, because there is nothing new to fetch`() = runTest {
        coEvery { userDao.getUserById("user-a") } returns userEntity(partnerId = "u2")
        val listeners = stubRealtimeListeners()

        repository.observePairingState().test {
            assertEquals(PairingState.Loading, awaitItem())
            runCurrent()
            listeners.emitInvites()
            listeners.emitUser(userDoc(partnerId = ""))
            awaitItem()
            cancelAndIgnoreRemainingEvents()
        }

        verify(exactly = 0) { syncRequester.requestSyncNow() }
    }

    @Test
    fun `observePairingState hands an ended link to the selected-family source`() = runTest {
        // This used to assert that the mirror cleared `partnerId` directly. It no longer may:
        // that field is now "the family this device is showing", and with two co-parents an
        // ending relationship might not be the one on screen — clearing it here would blank a
        // family the parent is still in. `SelectedFamilySource.reconcile()` is what decides,
        // because it is the only thing that knows both the full list and the choice.
        coEvery { userDao.getUserById("user-a") } returns userEntity(partnerId = "u2")
        val listeners = stubRealtimeListeners()

        repository.observePairingState().test {
            assertEquals(PairingState.Loading, awaitItem())
            runCurrent()
            listeners.emitInvites()
            listeners.emitUser(userDoc(partnerId = ""))
            awaitItem()
            cancelAndIgnoreRemainingEvents()
        }

        coVerify { selectedFamilySource.reconcile() }
    }

    @Test
    fun `observePairingState stays Loading when the user-document listener errors`() = runTest {
        // A failed read must never be reported as a fact about the link: forwarding the null
        // snapshot would read downstream as partnerId == "" and flip a paired account to
        // NotPaired, and a half-read snapshot would produce a Paired carrying a blank partner
        // as if it were real.
        val listeners = stubRealtimeListeners()

        repository.observePairingState().test {
            assertEquals(PairingState.Loading, awaitItem())
            runCurrent()
            listeners.emitInvites()
            listeners.failUser(mockk<FirebaseFirestoreException>(relaxed = true))

            expectNoEvents()
            cancelAndIgnoreRemainingEvents()
        }

        coVerify(exactly = 0) { userDao.updateUser(any()) }
    }

    @Test
    fun `the co-parent's colour change reaches the paired state as it happens`() = runTest {
        // The co-parent's profile used to be read once per change of *this* account's own
        // listeners, so their new colour waited for something unrelated to happen here — in
        // practice, for the app to restart. It is a listener of its own now.
        val listeners = stubRealtimeListeners()

        repository.observePairingState().test {
            assertEquals(PairingState.Loading, awaitItem())
            runCurrent()
            listeners.emitInvites()
            listeners.emitUser(userDoc(partnerId = "u2", pairedAt = 123L))
            listeners.emitPartner(partnerDoc(name = "Bob", role = "dad", colorCode = "#1976D2"))
            assertEquals("#1976D2", (awaitItem() as PairingState.Paired).partner.colorCode)

            listeners.emitPartner(partnerDoc(name = "Bob", role = "dad", colorCode = "hue1|30"))

            assertEquals("hue1|30", (awaitItem() as PairingState.Paired).partner.colorCode)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `a co-parent rename reaches the paired state as it happens`() = runTest {
        val listeners = stubRealtimeListeners()

        repository.observePairingState().test {
            assertEquals(PairingState.Loading, awaitItem())
            runCurrent()
            listeners.emitInvites()
            listeners.emitUser(userDoc(partnerId = "u2", pairedAt = 123L))
            listeners.emitPartner(partnerDoc(name = "Bob", role = "dad"))
            assertEquals("Bob", (awaitItem() as PairingState.Paired).partner.name)

            listeners.emitPartner(partnerDoc(name = "Robert", role = "dad"))

            assertEquals("Robert", (awaitItem() as PairingState.Paired).partner.name)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `a failed co-parent listener keeps the profile already read`() = runTest {
        // A listener denied mid-unpair, a moment before this account's own document says the
        // link ended, must not blank a name and a colour that were already on screen.
        val listeners = stubRealtimeListeners()

        repository.observePairingState().test {
            assertEquals(PairingState.Loading, awaitItem())
            runCurrent()
            listeners.emitInvites()
            listeners.emitUser(userDoc(partnerId = "u2", pairedAt = 123L))
            listeners.emitPartner(partnerDoc(name = "Bob", role = "dad", colorCode = "#1976D2"))
            assertEquals("Bob", (awaitItem() as PairingState.Paired).partner.name)

            listeners.failPartner(mockk<FirebaseFirestoreException>(relaxed = true))

            expectNoEvents()
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `a co-parent listener that fails before any read reports the bare partner`() = runTest {
        // The same answer the one-shot read gave when it failed: the link is real, the profile
        // is unknown, and the screens say "Parent" rather than waiting on Loading for ever.
        val listeners = stubRealtimeListeners()

        repository.observePairingState().test {
            assertEquals(PairingState.Loading, awaitItem())
            runCurrent()
            listeners.emitInvites()
            listeners.emitUser(userDoc(partnerId = "u2", pairedAt = 123L))

            listeners.failPartner(mockk<FirebaseFirestoreException>(relaxed = true))

            assertEquals(
                PairingState.Paired(
                    PartnerSummary(id = "u2", name = "", email = "", pairedSinceMillis = 123L)
                ),
                awaitItem()
            )
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `the co-parent listener is detached when the link ends`() = runTest {
        // One listener per co-parent, for as long as the link lasts — never one left behind per
        // pairing transition.
        val listeners = stubRealtimeListeners()

        repository.observePairingState().test {
            assertEquals(PairingState.Loading, awaitItem())
            runCurrent()
            listeners.emitInvites()
            listeners.emitUser(userDoc(partnerId = "u2", pairedAt = 123L))
            listeners.emitPartner(partnerDoc(name = "Bob", role = "dad"))
            awaitItem()

            listeners.emitUser(userDoc(partnerId = ""))

            assertEquals(PairingState.NotPaired(activeInvite = null, incoming = emptyList()), awaitItem())
            runCurrent()
            verify(exactly = 1) { listeners.partnerRegistration.remove() }
            cancelAndIgnoreRemainingEvents()
        }
    }
}
