package com.coparently.app.data.repository

import android.content.Context
import app.cash.turbine.test
import com.coparently.app.data.family.SelectedFamilySource
import com.coparently.app.data.local.dao.UserDao
import com.coparently.app.data.local.entity.UserEntity
import com.coparently.app.data.remote.firebase.AcceptInvitationResult
import com.coparently.app.data.remote.firebase.FirebaseAuthService
import com.coparently.app.data.remote.firebase.PairingException
import com.coparently.app.data.remote.firebase.PairingFunctions
import com.coparently.app.data.sync.SyncRequester
import com.coparently.app.domain.model.InvitePreview
import com.coparently.app.domain.model.PairingError
import com.coparently.app.domain.model.PairingState
import com.coparently.app.domain.model.PartnerSummary
import com.coparently.app.domain.repository.MessageRepository
import com.google.android.gms.tasks.Tasks
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.firestore.CollectionReference
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.EventListener
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.QuerySnapshot
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PairingRepositoryImplTest {

    private lateinit var firestore: FirebaseFirestore
    private lateinit var authService: FirebaseAuthService
    private lateinit var pairingFunctions: PairingFunctions
    private lateinit var messageRepository: MessageRepository
    private lateinit var conversationMigrator: ConversationMigrator
    private lateinit var userDao: UserDao
    private lateinit var context: Context
    private lateinit var selectedFamilySource: SelectedFamilySource
    private lateinit var syncRequester: SyncRequester
    private lateinit var repository: PairingRepositoryImpl

    private lateinit var usersCollection: CollectionReference

    /** The `users/{id}` snapshot every profile read in this class resolves to. */
    private lateinit var userSnapshot: DocumentSnapshot
    private lateinit var invitationsCollection: CollectionReference
    private lateinit var invitationsQuery: Query

    @Before
    fun setUp() {
        firestore = mockk(relaxed = true)
        authService = mockk(relaxed = true)
        pairingFunctions = mockk(relaxed = true)
        messageRepository = mockk(relaxed = true)
        conversationMigrator = mockk(relaxed = true)

        val firebaseUser = mockk<FirebaseUser>(relaxed = true)
        every { firebaseUser.uid } returns "user-a"
        every { firebaseUser.email } returns "a@example.com"
        every { authService.getCurrentUser() } returns firebaseUser

        // Any users/{id} read (partner name lookups) resolves immediately to an empty
        // snapshot. Without this, an un-stubbed relaxed-mockk Task never completes its
        // addOnCompleteListener, and `.await()` on it hangs for real wall-clock time
        // instead of failing fast — that is what made the first version of this test
        // class hang for a full minute on "redeem normalizes the code...".
        usersCollection = mockk(relaxed = true)
        val userDocument = mockk<DocumentReference>(relaxed = true)
        userSnapshot = mockk(relaxed = true)
        every { firestore.collection("users") } returns usersCollection
        every { usersCollection.document(any()) } returns userDocument
        every { userDocument.get() } returns Tasks.forResult(userSnapshot)

        // The conversation id is derived from the participant pair, so there is no
        // get-or-create lookup to stub any more — the relaxed mock answers `ensureConversation`.

        invitationsCollection = mockk(relaxed = true)
        invitationsQuery = mockk(relaxed = true)
        every { firestore.collection("invitations") } returns invitationsCollection
        every { invitationsCollection.whereEqualTo("fromUserId", any<String>()) } returns invitationsQuery
        every { invitationsQuery.whereEqualTo("status", any<String>()) } returns invitationsQuery

        userDao = mockk(relaxed = true)
        context = mockk(relaxed = true)
        every { context.getString(any()) } returns "Co-parent"
        // Relaxed: reconciling the shown family needs Firebase Auth and a real row, and what
        // these tests pin is that the mirror *delegates* the decision rather than making it.
        selectedFamilySource = mockk(relaxed = true)
        syncRequester = mockk(relaxed = true)

        repository = PairingRepositoryImpl(
            firestore = firestore,
            authService = authService,
            pairingFunctions = pairingFunctions,
            postPairingConversationSetup = PostPairingConversationSetup(messageRepository, conversationMigrator),
            userDao = userDao,
            selectedFamilySource = selectedFamilySource,
            syncRequester = syncRequester,
            context = context
        )
    }

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

    // ---- previewInvite --------------------------------------------------

    @Test
    fun `previewInvite rejects a malformed code without calling the backend`() = runTest {
        val result = repository.previewInvite("nope")

        assertEquals(PairingError.NotFound, (result.exceptionOrNull() as PairingException).error)
        coVerify(exactly = 0) { pairingFunctions.previewInvitation(any()) }
    }

    @Test
    fun `previewInvite normalizes the code and passes the name through`() = runTest {
        coEvery { pairingFunctions.previewInvitation("4F7K2M") } returns
            Result.success(InvitePreview(inviterName = "Alice"))

        val result = repository.previewInvite("  4f7k2m ")

        assertEquals(InvitePreview(inviterName = "Alice"), result.getOrNull())
        coVerify(exactly = 0) { pairingFunctions.acceptInvitation(any(), any()) }
    }

    // ---- redeem ---------------------------------------------------------

    @Test
    fun `redeem rejects a malformed code without calling the backend`() = runTest {
        val result = repository.redeem("nope")

        assertTrue(result.isFailure)
        assertEquals(
            PairingError.NotFound,
            (result.exceptionOrNull() as PairingException).error
        )
        coVerify(exactly = 0) { pairingFunctions.acceptInvitation(any(), any()) }
    }

    @Test
    fun `redeem normalizes the code before calling the backend`() = runTest {
        coEvery { pairingFunctions.acceptInvitation(code = "4F7K2M") } returns
            Result.success(AcceptInvitationResult(partnerId = "user-b", role = "dad"))

        val result = repository.redeem("  4f7k2m ")

        assertTrue(result.isSuccess)
        coVerify { pairingFunctions.acceptInvitation(code = "4F7K2M", invitationId = null) }
    }

    @Test
    fun `redeem surfaces the newly assigned slot from the callable, the same as acceptIncoming`() = runTest {
        // QR scan, manual code entry and deep link all funnel through redeem(), and reach the
        // same acceptPairingInvitation callable as an addressed-invitation accept — this path
        // must carry the role through too, or PairingViewModel has nothing to re-stamp with.
        coEvery { pairingFunctions.acceptInvitation(code = "4F7K2M") } returns
            Result.success(AcceptInvitationResult(partnerId = "user-b", role = "dad"))

        val result = repository.redeem("4F7K2M")

        assertEquals("dad", result.getOrNull())
    }

    @Test
    fun `redeem surfaces a null role without failing when the callable does not report one`() = runTest {
        coEvery { pairingFunctions.acceptInvitation(code = "4F7K2M") } returns
            Result.success(AcceptInvitationResult(partnerId = "user-b", role = null))

        val result = repository.redeem("4F7K2M")

        assertTrue(result.isSuccess)
        assertEquals(null, result.getOrNull())
    }

    // ---- acceptIncoming ----------------------------------------------------

    @Test
    fun `acceptIncoming surfaces the newly assigned slot from the callable`() = runTest {
        coEvery { pairingFunctions.acceptInvitation(invitationId = "invite-1") } returns
            Result.success(AcceptInvitationResult(partnerId = "user-b", role = "dad"))

        val result = repository.acceptIncoming("invite-1")

        assertEquals("dad", result.getOrNull())
    }

    @Test
    fun `acceptIncoming surfaces a null role without failing when the callable does not report one`() = runTest {
        coEvery { pairingFunctions.acceptInvitation(invitationId = "invite-1") } returns
            Result.success(AcceptInvitationResult(partnerId = "user-b", role = null))

        val result = repository.acceptIncoming("invite-1")

        assertTrue(result.isSuccess)
        assertEquals(null, result.getOrNull())
    }

    @Test
    fun `acceptIncoming surfaces the backend error unchanged`() = runTest {
        coEvery { pairingFunctions.acceptInvitation(invitationId = "invite-1") } returns
            Result.failure(PairingException(PairingError.NotFound))

        val result = repository.acceptIncoming("invite-1")

        assertEquals(
            PairingError.NotFound,
            (result.exceptionOrNull() as PairingException).error
        )
    }

    @Test
    fun `redeem surfaces the backend error unchanged`() = runTest {
        coEvery { pairingFunctions.acceptInvitation(code = "4F7K2M") } returns
            Result.failure(PairingException(PairingError.AlreadyPaired))

        val result = repository.redeem("4F7K2M")

        assertEquals(
            PairingError.AlreadyPaired,
            (result.exceptionOrNull() as PairingException).error
        )
    }

    // ---- unpair -----------------------------------------------------------

    @Test
    fun `unpair delegates to the callable`() = runTest {
        coEvery { pairingFunctions.unpair() } returns Result.success("user-b")

        val result = repository.unpair()

        assertTrue(result.isSuccess)
        coVerify { pairingFunctions.unpair() }
    }

    @Test
    fun `unpair treats a null unpairedFrom as success`() = runTest {
        coEvery { pairingFunctions.unpair() } returns Result.success(null)

        val result = repository.unpair()

        assertTrue(result.isSuccess)
    }

    // ---- createOrReuseInviteCode -------------------------------------------

    @Test
    fun `createOrReuseInviteCode reuses an existing unexpired code invite without writing`() = runTest {
        val existing = pendingInviteDoc(
            code = "ABCDEF",
            toEmail = "",
            expiresAt = System.currentTimeMillis() + HOUR_MILLIS,
            createdAt = System.currentTimeMillis()
        )
        stubOwnInvitesQuery(existing)

        val result = repository.createOrReuseInviteCode()

        assertTrue(result.isSuccess)
        assertEquals("ABCDEF", result.getOrNull()?.code)
        verify(exactly = 0) { invitationsCollection.document(any()) }
    }

    @Test
    fun `createOrReuseInviteCode writes a new invite when the existing one is expired`() = runTest {
        val expired = pendingInviteDoc(
            code = "OLDCOD",
            toEmail = "",
            expiresAt = System.currentTimeMillis() - HOUR_MILLIS,
            createdAt = System.currentTimeMillis() - HOUR_MILLIS
        )
        stubOwnInvitesQuery(expired)
        val newInviteRef = mockk<DocumentReference>(relaxed = true)
        every { invitationsCollection.document(any()) } returns newInviteRef
        every { newInviteRef.set(any()) } returns Tasks.forResult<Void>(null)

        val result = repository.createOrReuseInviteCode()

        assertTrue(result.isSuccess)
        assertNotEquals("OLDCOD", result.getOrNull()?.code)
        verify { newInviteRef.set(any()) }
    }

    /**
     * Knock-on of the profile gap, not a regression test for the fix itself.
     *
     * `writeNewInvite` takes `fromUserName` from the inviter's own profile document and
     * falls back to the raw email address. That name is what the share text says and what
     * the co-parent's incoming-invitation card shows, so while nothing wrote a profile the
     * invitation was degraded in exactly the same way the partner card was. These two cases
     * pin the dependency: the fallback is only reached when the profile carries no name.
     */
    @Test
    fun `a new invite carries the profile name once the profile has one`() = runTest {
        every { userSnapshot.getString("name") } returns "Alice Novak"
        stubOwnInvitesQuery()
        val newInviteRef = mockk<DocumentReference>(relaxed = true)
        every { invitationsCollection.document(any()) } returns newInviteRef
        every { newInviteRef.set(any()) } returns Tasks.forResult<Void>(null)

        val result = repository.createOrReuseInviteCode()

        assertEquals("Alice Novak", result.getOrNull()?.fromUserName)
    }

    @Test
    fun `a new invite falls back to the email address when the profile has no name`() = runTest {
        every { userSnapshot.getString("name") } returns null
        stubOwnInvitesQuery()
        val newInviteRef = mockk<DocumentReference>(relaxed = true)
        every { invitationsCollection.document(any()) } returns newInviteRef
        every { newInviteRef.set(any()) } returns Tasks.forResult<Void>(null)

        val result = repository.createOrReuseInviteCode()

        assertEquals("a@example.com", result.getOrNull()?.fromUserName)
    }

    @Test
    fun `createOrReuseInviteCode ignores an email invitation and picks the newest unexpired code invite`() =
        runTest {
            val emailInvite = pendingInviteDoc(
                code = "EMAIL1",
                toEmail = "other@example.com",
                expiresAt = System.currentTimeMillis() + HOUR_MILLIS,
                createdAt = System.currentTimeMillis() + 1
            )
            val expiredCodeInvite = pendingInviteDoc(
                code = "EXPIRD",
                toEmail = "",
                expiresAt = System.currentTimeMillis() - HOUR_MILLIS,
                createdAt = System.currentTimeMillis() - HOUR_MILLIS
            )
            val olderCodeInvite = pendingInviteDoc(
                code = "OLDER1",
                toEmail = "",
                expiresAt = System.currentTimeMillis() + HOUR_MILLIS,
                createdAt = System.currentTimeMillis() - 1_000
            )
            val newestCodeInvite = pendingInviteDoc(
                code = "NEWEST",
                toEmail = "",
                expiresAt = System.currentTimeMillis() + HOUR_MILLIS,
                createdAt = System.currentTimeMillis()
            )
            stubOwnInvitesQuery(emailInvite, expiredCodeInvite, olderCodeInvite, newestCodeInvite)

            val result = repository.createOrReuseInviteCode()

            assertTrue(result.isSuccess)
            assertEquals("NEWEST", result.getOrNull()?.code)
            verify(exactly = 0) { invitationsCollection.document(any()) }
        }

    /**
     * A calendar friend's and a professional's codes are written by a paired parent with an
     * empty `toEmail`, like a co-parent code. Reusing one handed that parent the grandmother's
     * code under "Add another co-parent", which the pairing callable then refuses.
     */
    @Test
    fun `createOrReuseInviteCode never reuses a guest, friend or professional code`() = runTest {
        val others = listOf("guest", "friend", "professional").map { kind ->
            pendingInviteDoc(code = kind.take(6).uppercase().padEnd(6, 'X'), toEmail = "").ofKind(kind)
        }
        stubOwnInvitesQuery(*others.toTypedArray())
        val newInviteRef = mockk<DocumentReference>(relaxed = true)
        every { invitationsCollection.document(any()) } returns newInviteRef
        every { newInviteRef.set(any()) } returns Tasks.forResult<Void>(null)

        val result = repository.createOrReuseInviteCode()

        assertTrue(result.isSuccess)
        val code = result.getOrNull()?.code.orEmpty()
        assertFalse(code in setOf("GUESTX", "FRIEND", "PROFES"))
        verify { newInviteRef.set(any()) }
    }

    // ---- revokeActiveInvite -------------------------------------------------

    @Test
    fun `revokeActiveInvite cancels only the code invite, not email invitations`() = runTest {
        val codeInviteRef = mockk<DocumentReference>(relaxed = true)
        val codeInvite = pendingInviteDoc(code = "ABCDEF", toEmail = "", reference = codeInviteRef)
        every { codeInviteRef.update("status", "cancelled") } returns Tasks.forResult<Void>(null)

        val emailInviteRef = mockk<DocumentReference>(relaxed = true)
        val emailInvite = pendingInviteDoc(
            code = "EMAIL1",
            toEmail = "other@example.com",
            reference = emailInviteRef
        )
        stubOwnInvitesQuery(codeInvite, emailInvite)

        val result = repository.revokeActiveInvite()

        assertTrue(result.isSuccess)
        verify { codeInviteRef.update("status", "cancelled") }
        verify(exactly = 0) { emailInviteRef.update(any<String>(), any()) }
    }

    @Test
    fun `revokeActiveInvite leaves guest, friend and professional codes alone`() = runTest {
        val codeInviteRef = mockk<DocumentReference>(relaxed = true)
        val codeInvite = pendingInviteDoc(code = "ABCDEF", toEmail = "", reference = codeInviteRef)
        every { codeInviteRef.update("status", "cancelled") } returns Tasks.forResult<Void>(null)
        val otherRefs = List(3) { mockk<DocumentReference>(relaxed = true) }
        val others = listOf("guest", "friend", "professional").zip(otherRefs) { kind, ref ->
            pendingInviteDoc(code = "GHJKMN", toEmail = "", reference = ref).ofKind(kind)
        }
        stubOwnInvitesQuery(codeInvite, *others.toTypedArray())

        val result = repository.revokeActiveInvite()

        assertTrue(result.isSuccess)
        verify { codeInviteRef.update("status", "cancelled") }
        otherRefs.forEach { ref -> verify(exactly = 0) { ref.update(any<String>(), any()) } }
    }

    // ---- runPairing error normalization -------------------------------------

    @Test
    fun `runPairing normalizes an unexpected Firestore failure to PairingError Unknown`() = runTest {
        val docRef = mockk<DocumentReference>(relaxed = true)
        every { invitationsCollection.document("invite-1") } returns docRef
        every { docRef.update("status", "rejected") } throws
            mockk<FirebaseFirestoreException>(relaxed = true)

        val result = repository.rejectIncoming("invite-1")

        assertTrue(result.isFailure)
        assertTrue((result.exceptionOrNull() as PairingException).error is PairingError.Unknown)
    }

    @Test
    fun `runPairing passes a thrown PairingException through unchanged`() = runTest {
        every { authService.getCurrentUser() } returns null

        val result = repository.createOrReuseInviteCode()

        assertTrue(result.isFailure)
        assertEquals(
            PairingError.Unknown("Not signed in"),
            (result.exceptionOrNull() as PairingException).error
        )
    }

    // ---- test helpers ---------------------------------------------------

    /**
     * Handles for driving the four snapshot listeners `observePairingState` registers while
     * paired: the account's own document, its two invitation queries and the co-parent's
     * profile.
     *
     * The repository reads pairing state from realtime listeners, so a test can only move it
     * by invoking the captured [EventListener]s directly — there is no request/response to
     * stub. [emitInvites] satisfies the two invitation listeners the `combine` also waits on;
     * without it no combined value is ever produced.
     */
    private class RealtimeListeners(
        val user: () -> EventListener<DocumentSnapshot>,
        val ownInvites: () -> EventListener<QuerySnapshot>,
        val incomingInvites: () -> EventListener<QuerySnapshot>,
        val partner: () -> EventListener<DocumentSnapshot>,
        val partnerRegistration: ListenerRegistration
    ) {
        fun emitUser(snapshot: DocumentSnapshot) = user().onEvent(snapshot, null)

        fun failUser(error: FirebaseFirestoreException) = user().onEvent(null, error)

        /** The co-parent's own `users/u2` document, as their listener delivers it. */
        fun emitPartner(snapshot: DocumentSnapshot) = partner().onEvent(snapshot, null)

        fun failPartner(error: FirebaseFirestoreException) = partner().onEvent(null, error)

        fun emitInvites() {
            val empty = mockk<QuerySnapshot>(relaxed = true)
            every { empty.documents } returns emptyList()
            ownInvites().onEvent(empty, null)
            incomingInvites().onEvent(empty, null)
        }
    }

    private fun stubRealtimeListeners(): RealtimeListeners {
        val firebaseUser = mockk<FirebaseUser>(relaxed = true)
        every { firebaseUser.uid } returns "user-a"
        every { firebaseUser.email } returns "a@example.com"
        every { authService.getAuthStateFlow() } returns flowOf(firebaseUser)

        val userListener = slot<EventListener<DocumentSnapshot>>()
        val userDocument = mockk<DocumentReference>(relaxed = true)
        every { usersCollection.document("user-a") } returns userDocument
        every { userDocument.addSnapshotListener(capture(userListener)) } returns mockk(relaxed = true)

        // Own invites are filtered by fromUserId; incoming ones by toEmail. Both land on the
        // same relaxed `invitationsQuery`, so they are told apart by which where-clause built
        // them rather than by the object itself.
        val ownQuery = mockk<Query>(relaxed = true)
        val incomingQuery = mockk<Query>(relaxed = true)
        every { invitationsCollection.whereEqualTo("fromUserId", "user-a") } returns ownQuery
        every { invitationsCollection.whereEqualTo("toEmail", "a@example.com") } returns incomingQuery
        every { ownQuery.whereEqualTo("status", "pending") } returns ownQuery
        every { incomingQuery.whereEqualTo("status", "pending") } returns incomingQuery

        val ownListener = slot<EventListener<QuerySnapshot>>()
        val incomingListener = slot<EventListener<QuerySnapshot>>()
        every { ownQuery.addSnapshotListener(capture(ownListener)) } returns mockk(relaxed = true)
        every { incomingQuery.addSnapshotListener(capture(incomingListener)) } returns mockk(relaxed = true)

        // The co-parent "u2" has a listener of their own. Their document's one-shot `get()` stays
        // stubbed as well: the post-pairing conversation still reads their name that way, and a
        // relaxed Task never completes.
        val partnerListener = slot<EventListener<DocumentSnapshot>>()
        val partnerRegistration = mockk<ListenerRegistration>(relaxed = true)
        val partnerDocument = mockk<DocumentReference>(relaxed = true)
        every { usersCollection.document("u2") } returns partnerDocument
        every { partnerDocument.get() } returns Tasks.forResult(userSnapshot)
        every { partnerDocument.addSnapshotListener(capture(partnerListener)) } returns partnerRegistration

        return RealtimeListeners(
            user = { userListener.captured },
            ownInvites = { ownListener.captured },
            incomingInvites = { incomingListener.captured },
            partner = { partnerListener.captured },
            partnerRegistration = partnerRegistration
        )
    }

    /**
     * This account's own document. [partnerIds] is the array `acceptPairingInvitation` grows;
     * absent, as on a document written before it existed, the singular [partnerId] is the set.
     */
    private fun userDoc(
        partnerId: String,
        pairedAt: Long? = null,
        partnerIds: List<String>? = null
    ): DocumentSnapshot {
        val snapshot = mockk<DocumentSnapshot>(relaxed = true)
        every { snapshot.getString("partnerId") } returns partnerId
        every { snapshot.getLong("pairedAt") } returns pairedAt
        every { snapshot.get("partnerIds") } returns partnerIds
        return snapshot
    }

    /** The co-parent's profile document; every field absent unless given. */
    private fun partnerDoc(
        name: String? = null,
        email: String? = null,
        photoUrl: String? = null,
        role: String? = null,
        colorCode: String? = null
    ): DocumentSnapshot = fakeDocument(
        mapOf(
            "name" to name,
            "email" to email,
            "profilePhotoUrl" to photoUrl,
            "role" to role,
            "colorCode" to colorCode
        )
    )

    private fun userEntity(partnerId: String?) = UserEntity(
        id = "user-a",
        email = "a@example.com",
        name = "Alice",
        role = "mom",
        colorCode = "#FF4081",
        partnerId = partnerId
    )

    private fun stubOwnInvitesQuery(vararg docs: DocumentSnapshot) {
        val snapshot = mockk<QuerySnapshot>(relaxed = true)
        every { snapshot.documents } returns docs.toList()
        every { invitationsQuery.get() } returns Tasks.forResult(snapshot)
    }

    private fun pendingInviteDoc(
        code: String,
        toEmail: String,
        expiresAt: Long = System.currentTimeMillis() + HOUR_MILLIS,
        createdAt: Long = System.currentTimeMillis(),
        reference: DocumentReference = mockk(relaxed = true)
    ): DocumentSnapshot {
        val doc = mockk<DocumentSnapshot>(relaxed = true)
        every { doc.getString("code") } returns code
        every { doc.getString("toEmail") } returns toEmail
        every { doc.getString("fromUserId") } returns "user-a"
        every { doc.getString("fromUserName") } returns "Alice"
        every { doc.getString("fromUserEmail") } returns "a@example.com"
        every { doc.getLong("expiresAt") } returns expiresAt
        every { doc.getLong("createdAt") } returns createdAt
        every { doc.reference } returns reference
        return doc
    }

    /** Marks a pending invitation as another kind than a co-parent code (`guest`, `friend`, …). */
    private fun DocumentSnapshot.ofKind(kind: String): DocumentSnapshot = apply {
        every { this@ofKind.getString("kind") } returns kind
    }

    private companion object {
        const val HOUR_MILLIS = 60L * 60 * 1000
        const val PARTNER_PHOTO = "https://lh3.googleusercontent.com/a/bob"
    }
}
