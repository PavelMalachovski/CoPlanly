package com.coparently.app.data.repository

import com.coparently.app.data.family.SelectedFamilySource
import com.coparently.app.data.local.dao.UserDao
import com.coparently.app.data.local.entity.UserEntity
import com.coparently.app.data.remote.firebase.DocumentPath
import com.coparently.app.data.remote.firebase.DocumentQuery
import com.coparently.app.data.remote.firebase.PairingFunctions
import com.coparently.app.data.remote.firebase.RecordingFirestoreClient
import com.coparently.app.data.remote.firebase.SignedInUser
import com.coparently.app.data.remote.firebase.StoredDocument
import com.coparently.app.data.sync.SyncRequester
import com.coparently.app.domain.repository.MessageRepository
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.channels.ProducerScope
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOf
import org.junit.Before

/**
 * What [PairingRepositoryImplTest] and [PairingRepositoryImplCallablesTest] share: a repository over
 * the recording Firestore seam and relaxed mocks, and the helpers that drive its listeners and
 * invitation queries. Split out so neither class outgrows detekt's `LargeClass` budget; it holds no
 * tests.
 *
 * Every Firestore call the repository makes goes through [firestoreClient] since MON-24 phase 2
 * (Task 8f; the co-parent's profile listener since Task 8d): a listener on `users/{uid}` there is
 * the one [profileListener] holds for that uid, a listener on an invitation query the one
 * [queryListener] holds for it, and each is silent until a test drives it, as a relaxed SDK mock's
 * listener was. A one-shot read answers from what the client stores: no document unless a test
 * puts one.
 */
abstract class PairingRepositoryImplFixture {

    protected lateinit var firestoreClient: RecordingFirestoreClient
    private val profileListeners = mutableMapOf<String, ProfileListener>()
    private val queryListeners = mutableMapOf<DocumentQuery, QueryListener>()
    protected lateinit var authService: SignedInUser
    protected lateinit var pairingFunctions: PairingFunctions
    protected lateinit var messageRepository: MessageRepository
    protected lateinit var conversationMigrator: ConversationMigrator
    protected lateinit var userDao: UserDao
    protected lateinit var wording: PairingWording
    protected lateinit var selectedFamilySource: SelectedFamilySource
    protected lateinit var syncRequester: SyncRequester
    protected lateinit var repository: PairingRepositoryImpl

    /** How many invitation documents [pendingInviteDoc] has made: the next is `invite-<n + 1>`. */
    private var invitesMade = 0

    @Before
    fun setUp() {
        profileListeners.clear()
        queryListeners.clear()
        firestoreClient = RecordingFirestoreClient().apply {
            documentFlow = { path ->
                callbackFlow {
                    val listener = profileListeners[path.id].takeIf { path.collection == "users" }
                    listener?.attached?.add(this)
                    awaitClose { listener?.let { it.removed++ } }
                }
            }
            queryFlow = { query ->
                callbackFlow {
                    val listener = queryListeners[query]
                    listener?.attached?.add(this)
                    awaitClose { }
                }
            }
        }
        authService = mockk(relaxed = true)
        pairingFunctions = mockk(relaxed = true)
        messageRepository = mockk(relaxed = true)
        conversationMigrator = mockk(relaxed = true)

        every { authService.getCurrentUid() } returns "user-a"
        every { authService.getCurrentEmail() } returns "a@example.com"

        // The conversation id is derived from the participant pair, so there is no
        // get-or-create lookup to stub any more — the relaxed mock answers `ensureConversation`.

        userDao = mockk(relaxed = true)
        wording = mockk { coEvery { defaultPartnerName() } returns "Co-parent" }
        // Relaxed: reconciling the shown family needs Firebase Auth and a real row, and what
        // these tests pin is that the mirror *delegates* the decision rather than making it.
        selectedFamilySource = mockk(relaxed = true)
        syncRequester = mockk(relaxed = true)

        repository = PairingRepositoryImpl(
            firestoreClient = firestoreClient,
            authService = authService,
            pairingFunctions = pairingFunctions,
            postPairingConversationSetup = PostPairingConversationSetup(messageRepository, conversationMigrator),
            userDao = userDao,
            selectedFamilySource = selectedFamilySource,
            syncRequester = syncRequester,
            wording = wording
        )
    }

    // ---- test helpers ---------------------------------------------------

    /**
     * Handles for driving the four listeners `observePairingState` registers while paired: the
     * account's own document, its two invitation queries and the co-parent's profile.
     *
     * The repository reads pairing state from realtime listeners, so a test can only move it by
     * sending on them — there is no request/response to stub. [emitInvites] satisfies the two
     * invitation listeners the `combine` also waits on; without it no combined value is ever
     * produced.
     */
    protected class RealtimeListeners(
        val user: ProfileListener,
        val ownInvites: QueryListener,
        val incomingInvites: QueryListener,
        val partner: ProfileListener
    ) {
        fun emitUser(snapshot: StoredDocument) = user.emit(snapshot)

        fun failUser(error: Throwable) = user.fail(error)

        /** The co-parent's own `users/u2` document, as their listener delivers it. */
        fun emitPartner(snapshot: StoredDocument) = partner.emit(snapshot)

        fun failPartner(error: Throwable) = partner.fail(error)

        /** How many co-parent listeners were detached. */
        fun partnerRemovals(): Int = partner.removed

        fun emitInvites() {
            ownInvites.emit(emptyList())
            incomingInvites.emit(emptyList())
        }
    }

    protected fun stubRealtimeListeners(): RealtimeListeners {
        every { authService.observeCurrentUid() } returns flowOf("user-a")
        return RealtimeListeners(
            user = profileListener("user-a"),
            ownInvites = queryListener(OWN_INVITES),
            incomingInvites = queryListener(INCOMING_INVITES),
            // The co-parent "u2" has a listener of their own. Their document's one-shot `get()`
            // answers from the client: the post-pairing conversation reads their name that way.
            partner = profileListener("u2")
        )
    }

    /** The listener [firestoreClient] attaches to `users/[uid]`; attach it before the flow runs. */
    protected fun profileListener(uid: String): ProfileListener = profileListeners.getOrPut(uid) { ProfileListener() }

    /** The listener [firestoreClient] attaches to [query]; attach it before the flow runs. */
    protected fun queryListener(query: DocumentQuery): QueryListener =
        queryListeners.getOrPut(query) { QueryListener() }

    /** One profile's listeners, in order — a re-attachment adds one — and how many were detached. */
    protected class ProfileListener {
        val attached = mutableListOf<ProducerScope<StoredDocument>>()
        var removed = 0

        /** The document as the newest listener delivers it. */
        fun emit(snapshot: StoredDocument) {
            attached.last().trySend(snapshot)
        }

        /** The newest listener failing with [error], as the client's flow fails. */
        fun fail(error: Throwable) {
            attached.last().close(error)
        }
    }

    /** One query's listeners, in order. */
    protected class QueryListener {
        val attached = mutableListOf<ProducerScope<List<StoredDocument>>>()

        /** The documents as the newest listener delivers them. */
        fun emit(documents: List<StoredDocument>) {
            attached.last().trySend(documents)
        }
    }

    /**
     * This account's own document. [partnerIds] is the array `acceptPairingInvitation` grows;
     * absent, as on a document written before it existed, the singular [partnerId] is the set.
     */
    protected fun userDoc(
        partnerId: String,
        pairedAt: Long? = null,
        partnerIds: List<String>? = null
    ): StoredDocument = StoredDocument(
        "user-a",
        mapOf("partnerId" to partnerId, "pairedAt" to pairedAt, "partnerIds" to partnerIds)
    )

    /** The co-parent's profile document; every field absent unless given. */
    protected fun partnerDoc(
        name: String? = null,
        email: String? = null,
        photoUrl: String? = null,
        role: String? = null,
        colorCode: String? = null
    ): StoredDocument = StoredDocument(
        "u2",
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

    /** What a one-shot read of this account's pending invitations answers. */
    protected fun stubOwnInvitesQuery(vararg docs: StoredDocument) {
        firestoreClient.queryAnswer = { query -> if (query == OWN_INVITES) docs.toList() else emptyList() }
    }

    /** This account's `users/user-a`, as a one-shot read of it answers. */
    protected fun stubOwnProfile(name: String?) {
        firestoreClient.put(DocumentPath("users", "user-a"), mapOf("name" to name))
    }

    protected fun pendingInviteDoc(
        code: String,
        toEmail: String,
        expiresAt: Long = System.currentTimeMillis() + HOUR_MILLIS,
        createdAt: Long = System.currentTimeMillis()
    ): StoredDocument = StoredDocument(
        "invite-${++invitesMade}",
        mapOf(
            "code" to code,
            "toEmail" to toEmail,
            "fromUserId" to "user-a",
            "fromUserName" to "Alice",
            "fromUserEmail" to "a@example.com",
            "expiresAt" to expiresAt,
            "createdAt" to createdAt
        )
    )

    /** Marks a pending invitation as another kind than a co-parent code (`guest`, `friend`, …). */
    protected fun StoredDocument.ofKind(kind: String): StoredDocument =
        StoredDocument(id, data.orEmpty() + ("kind" to kind))

    protected companion object {
        const val HOUR_MILLIS = 60L * 60 * 1000
        const val PARTNER_PHOTO = "https://lh3.googleusercontent.com/a/bob"

        /** The repository's own pending-invitations query: the reuse lookup, the revoke, the listener. */
        val OWN_INVITES = DocumentQuery("invitations")
            .whereEqualTo("fromUserId", "user-a")
            .whereEqualTo("status", "pending")

        /** The invitations addressed to this account's address. */
        val INCOMING_INVITES = DocumentQuery("invitations")
            .whereEqualTo("toEmail", "a@example.com")
            .whereEqualTo("status", "pending")
    }
}
