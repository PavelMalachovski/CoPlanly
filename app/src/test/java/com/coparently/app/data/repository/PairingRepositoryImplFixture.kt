package com.coparently.app.data.repository

import android.content.Context
import com.coparently.app.data.family.SelectedFamilySource
import com.coparently.app.data.local.dao.UserDao
import com.coparently.app.data.local.entity.UserEntity
import com.coparently.app.data.remote.firebase.FirebaseAuthService
import com.coparently.app.data.remote.firebase.PairingFunctions
import com.coparently.app.data.sync.SyncRequester
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
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.flow.flowOf
import org.junit.Before

/**
 * What [PairingRepositoryImplTest] and [PairingRepositoryImplCallablesTest] share: a repository over
 * relaxed Firebase mocks, and the helpers that drive its snapshot listeners and invitation
 * queries. Split out so neither class outgrows detekt's `LargeClass` budget; it holds no tests.
 */
abstract class PairingRepositoryImplFixture {

    protected lateinit var firestore: FirebaseFirestore
    protected lateinit var authService: FirebaseAuthService
    protected lateinit var pairingFunctions: PairingFunctions
    protected lateinit var messageRepository: MessageRepository
    protected lateinit var conversationMigrator: ConversationMigrator
    protected lateinit var userDao: UserDao
    protected lateinit var context: Context
    protected lateinit var selectedFamilySource: SelectedFamilySource
    protected lateinit var syncRequester: SyncRequester
    protected lateinit var repository: PairingRepositoryImpl

    protected lateinit var usersCollection: CollectionReference

    /** The `users/{id}` snapshot every profile read in this class resolves to. */
    protected lateinit var userSnapshot: DocumentSnapshot
    protected lateinit var invitationsCollection: CollectionReference
    protected lateinit var invitationsQuery: Query

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
    protected class RealtimeListeners(
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

    protected fun stubRealtimeListeners(): RealtimeListeners {
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
    protected fun userDoc(
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
    protected fun partnerDoc(
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

    protected fun userEntity(partnerId: String?) = UserEntity(
        id = "user-a",
        email = "a@example.com",
        name = "Alice",
        role = "mom",
        colorCode = "#FF4081",
        partnerId = partnerId
    )

    protected fun stubOwnInvitesQuery(vararg docs: DocumentSnapshot) {
        val snapshot = mockk<QuerySnapshot>(relaxed = true)
        every { snapshot.documents } returns docs.toList()
        every { invitationsQuery.get() } returns Tasks.forResult(snapshot)
    }

    protected fun pendingInviteDoc(
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
    protected fun DocumentSnapshot.ofKind(kind: String): DocumentSnapshot = apply {
        every { this@ofKind.getString("kind") } returns kind
    }

    protected companion object {
        const val HOUR_MILLIS = 60L * 60 * 1000
        const val PARTNER_PHOTO = "https://lh3.googleusercontent.com/a/bob"
    }
}
