package com.coparently.app.data.repository

import android.content.Context
import android.util.Log
import com.coparently.app.R
import com.coparently.app.data.family.SelectedFamilySource
import com.coparently.app.data.local.dao.UserDao
import com.coparently.app.data.remote.firebase.FirebaseAuthService
import com.coparently.app.data.remote.firebase.PairingException
import com.coparently.app.data.remote.firebase.PairingFunctions
import com.coparently.app.data.sync.SyncRequester
import com.coparently.app.domain.model.InvitePreview
import com.coparently.app.domain.model.PairingError
import com.coparently.app.domain.model.PairingInvite
import com.coparently.app.domain.model.PairingState
import com.coparently.app.domain.model.PartnerSummary
import com.coparently.app.domain.pairing.InviteCodeGenerator
import com.coparently.app.domain.repository.PairingRepository
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.gson.Gson
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.tasks.await
import java.util.UUID
import java.util.concurrent.atomic.AtomicReference
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Firestore-backed [PairingRepository].
 *
 * Reads are realtime snapshot listeners; the two writes that touch the other
 * parent's document (accept, unpair) go through Cloud Functions.
 */
@Singleton
class PairingRepositoryImpl @Inject constructor(
    private val firestore: FirebaseFirestore,
    private val authService: FirebaseAuthService,
    private val pairingFunctions: PairingFunctions,
    private val postPairingConversationSetup: PostPairingConversationSetup,
    private val userDao: UserDao,
    private val selectedFamilySource: SelectedFamilySource,
    private val syncRequester: SyncRequester,
    // Application context only, for the localized conversation-title fallback. This is a
    // repository, not a ViewModel, so it is allowed to resolve resources directly.
    @ApplicationContext private val context: Context
) : PairingRepository {

    /** For the stored co-parent list, which is a JSON array of plain uid strings. */
    private val gson = Gson()

    /**
     * What [onPairingStateObserved] last mirrored into Room, so repeated emissions of an unchanged
     * link do no work. Null means "nothing applied yet".
     *
     * Shared by every collector of [observePairingState]: each one has listeners of its own and
     * runs the hook on every value, and the `getAndSet` here is what lets exactly one of them act
     * on a given change.
     */
    private val appliedPairing = AtomicReference<AppliedPairing?>(null)

    /**
     * One mirrored link: whose account, and every co-parent the server named for it.
     *
     * **The set, not `partnerId`.** `acceptPairingInvitation` keeps the singular field on the
     * *first* co-parent and adds a second one only to `partnerIds`, so a parent who already had a
     * co-parent and then linked another saw no transition at all here: no sync, no family in Room
     * for the switcher, no thread, until a sync tick or a push happened along (tester report #4).
     * Keyed on the set, a co-parent the server adds is a transition whichever field names them —
     * and a family switch, which moves only the local projection, never is one.
     */
    private data class AppliedPairing(val uid: String, val coParents: Set<String>)

    /**
     * One value of the pairing listeners: the [state] every screen sees, and every co-parent this
     * account's own document names ([coParents]: `partnerIds` with `partnerId`).
     *
     * The set stays here rather than on [PairingState] because only the mirror needs it, and a
     * change of the set alone — say a second family ending while the first one stays — must still
     * reach [onPairingStateObserved] without re-emitting a state no screen would draw differently.
     */
    private data class ObservedPairing(val state: PairingState, val coParents: Set<String> = emptySet())

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun observePairingState(): Flow<PairingState> =
        authService.getAuthStateFlow()
            .flatMapLatest { user ->
                if (user == null) {
                    // Signed out (or auth has not restored its session yet). There is no
                    // dedicated "unauthenticated" case in PairingState, and Loading is
                    // accurate either way: as soon as auth resolves to a user, flatMapLatest
                    // restarts this branch with the real Firestore-backed flow below.
                    flowOf(ObservedPairing(PairingState.Loading))
                } else {
                    combine(
                        observeLinkedPartner(user.uid),
                        observeOwnInvites(user.uid),
                        observeIncomingInvites(user.email.orEmpty())
                    ) { linked, own, incoming ->
                        val partner = linked.partner
                        if (partner == null) {
                            ObservedPairing(
                                PairingState.NotPaired(activeInvite = own.firstOrNull(), incoming = incoming)
                            )
                        } else {
                            ObservedPairing(
                                state = PairingState.Paired(
                                    partner = partner,
                                    // Carried into the paired state too, so the screen can offer
                                    // a second co-parent: a person may have more than one, and
                                    // without these the invite has nothing to render.
                                    activeInvite = own.firstOrNull(),
                                    incoming = incoming
                                ),
                                coParents = linked.coParents
                            )
                        }
                    }
                }
            }
            // The very first value a new subscriber sees, before auth resolves or the
            // first Firestore snapshot arrives.
            .onStart { emit(ObservedPairing(PairingState.Loading)) }
            .distinctUntilChanged()
            // Mirror every observed transition into Room. This hook, rather than the
            // redeem/acceptIncoming call sites, is what makes both phones learn about the
            // link: the inviter never calls anything, it only sees its own snapshot.
            .onEach { onPairingStateObserved(it) }
            .map { it.state }
            // A change of the co-parent set alone has been mirrored above; it is not a new state.
            .distinctUntilChanged()
            // A transient failure (offline with no cache, a rules mismatch before task 11
            // deploys) must not permanently end this flow — that would freeze the pairing
            // screen on stale content forever. Recover to Loading instead; the underlying
            // snapshot listeners keep retrying on their own.
            .catch { emit(PairingState.Loading) }

    override fun observeCoParentProfile(partnerUid: String): Flow<PartnerSummary> =
        firestore.partnerProfile(partnerUid, pairedAt = null)

    override suspend fun createOrReuseInviteCode(): Result<PairingInvite> = runPairing {
        val user = requireUser()
        val existing = firestore.collection(INVITATIONS)
            .whereEqualTo("fromUserId", user.uid)
            .whereEqualTo("status", STATUS_PENDING)
            .get()
            .await()
            .documents
            .toActiveCodeInvites()
            .firstOrNull()

        existing ?: writeNewInvite(toEmail = "")
    }

    /**
     * Withdraws this user's active code/QR/link invite (the one
     * [createOrReuseInviteCode] returns).
     *
     * The `toEmail` filter survives the removal of email invitations: guest and friend
     * invitations still write the field (as an empty string), and a co-parent on an older build
     * may still have an email invitation pending. Withdrawing this user's code must not cancel
     * one of those.
     */
    override suspend fun revokeActiveInvite(): Result<Unit> = runPairing {
        val user = requireUser()
        firestore.collection(INVITATIONS)
            .whereEqualTo("fromUserId", user.uid)
            .whereEqualTo("status", STATUS_PENDING)
            .get()
            .await()
            .documents
            .filter { it.getString("toEmail").orEmpty().isEmpty() }
            .filter { it.isCoParentInvite() }
            .forEach { it.reference.update("status", STATUS_CANCELLED).await() }
    }

    override suspend fun previewInvite(code: String): Result<InvitePreview> {
        val normalized = normalizedInviteCode(code)
            ?: return Result.failure(PairingException(PairingError.NotFound))
        return pairingFunctions.previewInvitation(normalized)
    }

    override suspend fun redeem(code: String): Result<String?> {
        val normalized = normalizedInviteCode(code)
            ?: return Result.failure(PairingException(PairingError.NotFound))
        // Conversation creation deliberately does NOT hang off this call: it is driven by the
        // observed Paired transition in [onPairingStateObserved] instead, so the inviter's
        // phone — which never calls anything — ends up with a thread too.
        return pairingFunctions.acceptInvitation(code = normalized).map { it.role }
    }

    override suspend fun acceptIncoming(invitationId: String): Result<String?> =
        pairingFunctions.acceptInvitation(invitationId = invitationId).map { it.role }

    override suspend fun rejectIncoming(invitationId: String): Result<Unit> = runPairing {
        firestore.collection(INVITATIONS).document(invitationId)
            .update("status", STATUS_REJECTED)
            .await()
    }

    override suspend fun unpair(): Result<Unit> = pairingFunctions.unpair().map { }

    // ---- Local mirroring ------------------------------------------------

    /**
     * Mirrors an observed pairing transition into the local Room `users` row and makes sure the
     * 1:1 conversation exists with every co-parent the transition brought.
     *
     * Everything outside the pairing screen reads pairing from Room, not from Firestore:
     * `ChatViewModel` decides between "open chat" and "go pair" from it, `ExpenseRepositoryImpl`
     * derives the `familyId` it queries on from it, `SyncService`
     * sizes the event audience with it, and `HomeViewModel` renders its CTA from it. Before
     * this hook the only writer was `UserRepositoryImpl.pullOnce()` (since deleted) behind a 15-minute
     * `SyncWorker`, so both phones showed "Paired with X" while chat, expenses and
     * events stayed unpaired for up to a quarter of an hour — and after an unpair, the
     * ex-partner's records stayed in view just as long.
     *
     * It hangs off the *observed* state rather than off [redeem]/[acceptIncoming] so it fires
     * on both devices: the inviter's phone never calls anything, it learns about the pairing
     * from its own snapshot listener. That is also why conversation creation moved here from
     * those two call sites — previously only the accepting device created a thread.
     *
     * Best-effort by design: the pairing itself is already durable in Firestore, so a local
     * write failure must never turn into a failed pairing. It is logged and swallowed, and
     * the next emission (or the `SyncWorker`) retries.
     *
     * **A co-parent this device did not know about a moment ago also asks for a sync.** The
     * mirror above tells the app *that* it is paired; it does not fetch anything the pairing
     * entitles the two phones to. On the inviter's phone the next `performFullSync` is what
     * widens the audience of every child, pet and event created while unpaired
     * (`SyncService.backfillChildInfoAudienceForPartner` and its siblings), and on the
     * accepter's phone it is what downloads them. Both used to wait for the fifteen-minute tick
     * — or for a push to happen along — which the onboarding wizard made visible the day it
     * started pairing *first*: a second parent who linked in order to inherit the first one's
     * records was standing on an empty child step, waiting for a worker that had no reason to
     * run. The request is keyed on the transition, not on every emission, and coalesces with
     * the cold-start run when the two land together.
     *
     * **A transition is a co-parent the set gained** ([AppliedPairing]), whichever field names
     * them. A parent who already had a co-parent and then linked a second one used to see none
     * here, because the server keeps `partnerId` on the first: the inviter's phone learnt of the
     * second family only from a later sync, while the accepter's — whose `partnerId` did move —
     * had it at once (tester report #4). Each newcomer now gets what the first pairing gets: the
     * sync, a place in the Room list the switcher reads, and the conversation. The selection is
     * left where it is: a new family joins the switcher, it does not take over the screen. A
     * process that has applied nothing yet finds every co-parent new, as it always found the
     * first: still one sync request, and each thread ensured, which `PostPairingConversationSetup`
     * is built to repeat on every launch.
     */
    private suspend fun onPairingStateObserved(observed: ObservedPairing) {
        val uid = authService.getCurrentUser()?.uid ?: return
        val coParents: Set<String> = when (val state = observed.state) {
            // Loading carries no information about the link — mirroring it would clear a
            // perfectly good partner list every time the flow recovers from a transient error.
            PairingState.Loading -> return
            is PairingState.NotPaired -> emptySet()
            is PairingState.Paired -> (observed.coParents + state.partner.id)
                .filterTo(LinkedHashSet()) { it.isNotBlank() && it != uid }
        }
        // NotPaired also re-emits whenever an invite list changes, so skip the work unless
        // the set itself moved. Keyed by uid as well, so a different account signing in is
        // never mistaken for "already applied".
        val applied = AppliedPairing(uid, coParents)
        val previous = appliedPairing.getAndSet(applied)
        if (previous == applied) return
        val newcomers = coParents - previous?.takeIf { it.uid == uid }?.coParents.orEmpty()

        // Before the Room work below, and outside its try: the request is a WorkManager
        // enqueue that cannot fail the mirror, and a mirror that fails must not stop the sync
        // that would repair what it could not.
        if (newcomers.isNotEmpty()) {
            syncRequester.requestSyncNow()
        }

        try {
            mirrorPartnersIntoRoom(uid, coParents)
            newcomers.forEach { ensureConversationWith(it) }
        } catch (e: CancellationException) {
            // Reset before rethrowing, for the same reason the generic branch below does. This
            // flow is collected under `WhileSubscribed`, so leaving the app mid-`await()`
            // cancels it — and a marker left set here means the conversation document is never
            // created for the rest of the process, which the `messages` create rule turns into
            // "every send from this device is denied". Back to what was applied before, unless
            // another collector has moved it on since, so the next emission retries this change.
            appliedPairing.compareAndSet(applied, previous)
            throw e
        } catch (
            @Suppress("TooGenericExceptionCaught") e: Exception
        ) {
            // Reset so the next emission retries rather than believing this one landed.
            appliedPairing.compareAndSet(applied, previous)
            Log.w(TAG, "Failed to mirror the pairing transition into Room", e)
        }
    }

    /**
     * Adds every co-parent in [coParents] to this account's Room `partnerIds` list and re-points
     * the family projection.
     *
     * The **list** is what a pairing transition changes. `partnerId` is no longer "my
     * co-parent" but "the family this device is showing", and only `SelectedFamilySource`
     * writes it — mirroring the observed partner onto it here would drag a parent looking at
     * one family into another the moment the other one's pairing state re-emitted.
     *
     * **Additive.** A co-parent the server stopped naming is left in the list for the sync to
     * drop (`SyncService.syncUserData` copies the server's set over it), as it always was: an
     * own document that is not there at all — an offline first read with nothing cached — reads
     * here as no co-parents, and a list emptied on that evidence would take every family off the
     * switcher until the next sync. New ones go at the end, in the server's order, so the first
     * family stays the first.
     */
    private suspend fun mirrorPartnersIntoRoom(uid: String, coParents: Set<String>) {
        val local = userDao.getUserById(uid) ?: return
        val stored = gson.fromJson(local.partnerIdsJson, Array<String>::class.java)
            ?.toList().orEmpty()
        val nextJson = gson.toJson(stored + coParents.filterNot { it in stored })
        if (nextJson != local.partnerIdsJson) {
            userDao.updateUser(local.copy(partnerIdsJson = nextJson))
        }
        // Re-point the projection only when what it names has actually gone: an unpair
        // observed from the other side leaves this device showing an ex-partner otherwise,
        // and nothing else would notice until the switcher was opened. It is also what applies
        // a switch a notification asked for before this list held the family it named.
        selectedFamilySource.reconcile()
    }

    // ---- Firestore plumbing -------------------------------------------

    /**
     * The co-parent this account's own document names, kept current: null while it names nobody,
     * then their profile every time it changes — a rename, a new photo, a slot, a colour.
     *
     * **A listener on their `users/{uid}`, not a read** ([partnerProfile]). This used to be a
     * one-shot `get()` inside the combine of this account's own listeners, so it was repeated only
     * when *this* account's document or invitations changed. A co-parent's new colour or name
     * therefore reached this phone only when something unrelated happened here — in practice,
     * when the app was opened again.
     *
     * The listener follows the link: `distinctUntilChanged` keeps it attached while the own
     * document moves for other reasons, and `flatMapLatest` swaps or removes it the moment the
     * link names someone else or nobody, so there is never one left behind per transition.
     *
     * Every co-parent the document names rides along ([LinkedPartner.coParents]) for the mirror.
     * A change of that set re-attaches the listener too; it changes when a family is made or
     * ended, and a family made moves `pairedAt` anyway.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    private fun observeLinkedPartner(uid: String): Flow<LinkedPartner> =
        observeUserDocument(uid)
            .map { snapshot ->
                val partnerId = snapshot?.getString("partnerId").orEmpty()
                PartnerLink(
                    partnerId = partnerId,
                    pairedAt = snapshot?.getLong("pairedAt"),
                    coParents = coParentUids(snapshot?.get("partnerIds"), partnerId)
                )
            }
            .distinctUntilChanged()
            .flatMapLatest { link ->
                if (link.partnerId.isEmpty()) {
                    flowOf(LinkedPartner(partner = null, coParents = link.coParents))
                } else {
                    firestore.partnerProfile(link.partnerId, link.pairedAt)
                        .map { LinkedPartner(partner = it, coParents = link.coParents) }
                }
            }

    /** The link this account's own document names: who, since when, and every co-parent it lists. */
    private data class PartnerLink(val partnerId: String, val pairedAt: Long?, val coParents: Set<String>)

    /** The linked co-parent's profile — null while the document names nobody — and the whole set. */
    private data class LinkedPartner(val partner: PartnerSummary?, val coParents: Set<String>)

    /**
     * The user document, or nothing at all when the read fails.
     *
     * A failed read is deliberately *not* forwarded as a null snapshot. Downstream, a null
     * snapshot reads as `partnerId == ""`, i.e. "definitely not paired" — so a transient
     * error would have flipped a paired account to [PairingState.NotPaired] and offered it
     * an invite to create, and a partially-read snapshot would have produced a
     * [PairingState.Paired] carrying a blank partner as if it were real. Emitting nothing
     * leaves the combined state where it was (or at [PairingState.Loading] if this is the
     * first snapshot), and Firestore keeps retrying the listener on its own.
     */
    private fun observeUserDocument(uid: String): Flow<DocumentSnapshot?> = callbackFlow {
        val registration = firestore.collection(USERS).document(uid)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.w(TAG, "User document listener failed", error)
                    return@addSnapshotListener
                }
                trySend(snapshot)
            }
        awaitClose { registration.remove() }
    }

    private fun observeOwnInvites(uid: String): Flow<List<PairingInvite>> = callbackFlow {
        val registration = firestore.collection(INVITATIONS)
            .whereEqualTo("fromUserId", uid)
            .whereEqualTo("status", STATUS_PENDING)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.w(TAG, "Own-invitations listener failed", error)
                }
                trySend(snapshot?.documents.orEmpty().toActiveCodeInvites())
            }
        awaitClose { registration.remove() }
    }

    private fun observeIncomingInvites(email: String): Flow<List<PairingInvite>> {
        val normalized = email.trim().lowercase()
        if (normalized.isEmpty()) return flowOf(emptyList())
        return callbackFlow {
            val registration = firestore.collection(INVITATIONS)
                .whereEqualTo("toEmail", normalized)
                .whereEqualTo("status", STATUS_PENDING)
                .addSnapshotListener { snapshot, error ->
                    if (error != null) {
                        // The address itself is deliberately not logged — logcat is
                        // readable by anyone with adb access to the device.
                        Log.w(TAG, "Incoming-invitations listener failed", error)
                    }
                    trySend(snapshot?.documents.orEmpty().mapNotNull { it.toInvite() })
                }
            awaitClose { registration.remove() }
        }
    }

    private suspend fun writeNewInvite(toEmail: String): PairingInvite {
        val user = requireUser()
        val profile = firestore.collection(USERS).document(user.uid).get().await()
        val ttl = if (toEmail.isEmpty()) CODE_TTL_MILLIS else EMAIL_TTL_MILLIS
        val invite = PairingInvite(
            id = UUID.randomUUID().toString(),
            code = InviteCodeGenerator.generate(),
            fromUserId = user.uid,
            fromUserName = profile.getString("name") ?: user.email.orEmpty(),
            fromUserEmail = user.email.orEmpty(),
            toEmail = toEmail,
            expiresAtMillis = System.currentTimeMillis() + ttl
        )
        firestore.collection(INVITATIONS).document(invite.id).set(
            mapOf(
                "id" to invite.id,
                "code" to invite.code,
                "fromUserId" to invite.fromUserId,
                "fromUserName" to invite.fromUserName,
                "fromUserEmail" to invite.fromUserEmail,
                "toEmail" to invite.toEmail,
                "status" to STATUS_PENDING,
                "createdAt" to System.currentTimeMillis(),
                "expiresAt" to invite.expiresAtMillis,
                "acceptedBy" to null
            )
        ).await()
        return invite
    }

    /**
     * Creates the 1:1 conversation after pairing, if one does not already exist for
     * this participant pair.
     *
     * Driven by the observed transition in [onPairingStateObserved], once for every co-parent
     * the set gained, so both parents get a thread — it used to hang off
     * [redeem]/[acceptIncoming], which only ever run on the accepting device.
     *
     * Best-effort: the pairing itself already succeeded server-side by the time this
     * runs, so a failure here must not surface as a failure of the pairing — it is logged
     * and swallowed instead. This is safe because nothing depends on it having run:
     * `ChatViewModel.startConversationWithPartner` creates the conversation on demand the
     * first time either parent opens chat, through the same idempotent call as here.
     *
     * There is no get-or-create lookup any more. The id is derived from the participant
     * pair, so both devices compute the same one and a duplicate thread is impossible to
     * create — the lookup existed only to prevent duplicates that can no longer happen.
     *
     * [PostPairingConversationSetup] also folds any legacy conversation for this pair into the
     * canonical one right after creating it, inside this same `try` — so a merge failure
     * degrades exactly like a create failure: logged and swallowed, never surfaced as a failed
     * pairing.
     */
    private suspend fun ensureConversationWith(partnerId: String) {
        val uid = authService.getCurrentUser()?.uid ?: return
        if (partnerId.isEmpty()) return
        try {
            // The partner's display name titles the thread; their profile may not carry one
            // yet, in which case a localized placeholder stands in.
            val partnerName = firestore.collection(USERS).document(partnerId).get().await()
                .getString("name")
                .orEmpty()
                .ifEmpty { context.getString(R.string.pairing_default_partner_name) }
            postPairingConversationSetup.run(uid, partnerId, partnerName)
        } catch (e: CancellationException) {
            throw e
        } catch (
            @Suppress("TooGenericExceptionCaught") e: Exception
        ) {
            Log.w(TAG, "Failed to create the post-pairing conversation", e)
        }
    }

    private fun requireUser() = authService.getCurrentUser()
        ?: throw PairingException(PairingError.Unknown("Not signed in"))

    /**
     * Runs a Firestore block and normalizes any failure into a [PairingException].
     *
     * The lambda is `suspend` because every body calls `await()`; an inline
     * non-suspending version will not compile here. Cancellation must
     * propagate rather than be reported as a pairing failure — otherwise
     * navigating away mid-call surfaces a spurious error instead of the
     * silent cancellation structured concurrency expects.
     */
    private suspend fun <T> runPairing(block: suspend () -> T): Result<T> = try {
        Result.success(block())
    } catch (e: CancellationException) {
        throw e
    } catch (e: PairingException) {
        Result.failure(e)
    } catch (
        // Firestore failures become a typed error instead of crashing the caller.
        @Suppress("TooGenericExceptionCaught") e: Exception
    ) {
        Result.failure(PairingException(PairingError.Unknown(e.message)))
    }

    private companion object {
        const val TAG = "PairingRepositoryImpl"
        const val USERS = "users"
        const val INVITATIONS = "invitations"
        const val STATUS_PENDING = "pending"
        const val STATUS_REJECTED = "rejected"
        const val STATUS_CANCELLED = "cancelled"
        const val CODE_TTL_MILLIS = 24L * 60 * 60 * 1000
        const val EMAIL_TTL_MILLIS = 7L * 24 * 60 * 60 * 1000
        val EMAIL_REGEX = Regex("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$")
    }
}

/**
 * Whether this invitation is a co-parent code, which is the only kind that writes no `kind`
 * field (`firestore.rules`' `validInvitationKind`: absent, or `guest`, `friend` or
 * `professional`).
 *
 * Excluding the guest kind alone was not enough: a calendar friend's and a professional's
 * codes also have an empty `toEmail`, and both are issued by a *paired* parent — exactly the
 * one who opens "Add another co-parent". They were handed the grandmother's code as their
 * co-parent code (which the accept callable then refuses), and "New code" cancelled it.
 */
private fun DocumentSnapshot.isCoParentInvite(): Boolean = getString("kind").isNullOrEmpty()

/**
 * Narrows a batch of invitation documents down to the code/QR/link invites (as
 * opposed to email invitations) that have not yet expired, newest first.
 *
 * Both `PairingRepositoryImpl.createOrReuseInviteCode`'s reuse lookup and its own-invitations
 * listener's exposed "active invite" slot need exactly this: a single, deterministic answer to
 * "what is this user's current shareable code", even if a stale expired document or a
 * second one from a race was never cleaned up. Filtering and sorting happen
 * client-side (not via a Firestore `orderBy`) so this does not require a composite
 * index that has not been created.
 */
private fun List<DocumentSnapshot>.toActiveCodeInvites(): List<PairingInvite> {
    val now = System.currentTimeMillis()
    return this
        .filter { it.getString("toEmail").orEmpty().isEmpty() }
        .filter { it.isCoParentInvite() }
        .filter { (it.getLong("expiresAt") ?: 0L) > now }
        .sortedByDescending { it.getLong("createdAt") ?: 0L }
        .mapNotNull { it.toInvite() }
}

/**
 * The co-parents a `users/{uid}` document names, in either shape it may carry: the
 * [partnerIds] array, and the singular [partnerId] a document written before the array has.
 *
 * Unioned rather than one winning — the rule `UserRepositoryImpl.partnerUids`,
 * `SyncService.remotePartnerUids` and `functions/index.js`' `partnersOf` follow, because the
 * places deciding who somebody co-parents with must decide it identically. A value of the wrong
 * type reads as absent: an `as?` cast, never `getString`, which would throw inside the listener.
 *
 * @param partnerIds The document's `partnerIds` field as read, of whatever type it holds.
 * @param partnerId The document's `partnerId`, or empty when it names nobody.
 */
private fun coParentUids(partnerIds: Any?, partnerId: String): Set<String> {
    val many = (partnerIds as? List<*>)?.mapNotNull { it as? String }.orEmpty()
    return (many + partnerId).filterTo(LinkedHashSet()) { it.isNotBlank() }
}

/** An invitation document as the domain sees it, or null for one carrying no code. */
private fun DocumentSnapshot.toInvite(): PairingInvite? {
    val code = getString("code") ?: return null
    return PairingInvite(
        id = getString("id") ?: id,
        code = code,
        fromUserId = getString("fromUserId").orEmpty(),
        fromUserName = getString("fromUserName").orEmpty(),
        fromUserEmail = getString("fromUserEmail").orEmpty(),
        toEmail = getString("toEmail").orEmpty(),
        expiresAtMillis = getLong("expiresAt") ?: 0L
    )
}

/**
 * [code] trimmed and upper-cased, or null when it cannot be a code at all — refused here as
 * [PairingError.NotFound] rather than spending a callable on it.
 */
private fun normalizedInviteCode(code: String): String? =
    code.trim().uppercase().takeIf { InviteCodeGenerator.isValid(it) }
