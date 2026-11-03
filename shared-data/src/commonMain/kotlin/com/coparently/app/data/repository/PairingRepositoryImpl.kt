@file:OptIn(ExperimentalAtomicApi::class, ExperimentalUuidApi::class)

package com.coparently.app.data.repository

import com.coparently.app.data.family.SelectedFamilySource
import com.coparently.app.data.local.dao.UserDao
import com.coparently.app.data.platform.logger
import com.coparently.app.data.remote.firebase.DocumentPath
import com.coparently.app.data.remote.firebase.DocumentQuery
import com.coparently.app.data.remote.firebase.FirestoreClient
import com.coparently.app.data.remote.firebase.PairingException
import com.coparently.app.data.remote.firebase.PairingFunctions
import com.coparently.app.data.remote.firebase.SignedInUser
import com.coparently.app.data.remote.firebase.StoredDocument
import com.coparently.app.data.sync.SyncRequester
import com.coparently.app.domain.model.InvitePreview
import com.coparently.app.domain.model.PairingError
import com.coparently.app.domain.model.PairingInvite
import com.coparently.app.domain.model.PairingState
import com.coparently.app.domain.model.PartnerSummary
import com.coparently.app.domain.pairing.InviteCodeGenerator
import com.coparently.app.domain.repository.PairingRepository
import com.coparently.app.domain.serialization.StoredJson
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.onStart
import kotlin.concurrent.atomics.AtomicReference
import kotlin.concurrent.atomics.ExperimentalAtomicApi
import kotlin.time.Clock
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * Firestore-backed [PairingRepository].
 *
 * Reads are realtime snapshot listeners; the two writes that touch the other
 * parent's document (accept, unpair) go through Cloud Functions.
 *
 * On the Firestore seam ([FirestoreClient]) and in `:shared-data` since MON-24 phase 2 (Task 8f;
 * the co-parent's profile listener since Task 8d): the same documents, queries, listeners and
 * writes the Android SDK calls made, and the signed-in account from [SignedInUser].
 */
@Suppress("LongParameterList") // the listeners, the callables, the mirror's three and the wording
class PairingRepositoryImpl(
    private val firestoreClient: FirestoreClient,
    private val authService: SignedInUser,
    private val pairingFunctions: PairingFunctions,
    private val postPairingConversationSetup: PostPairingConversationSetup,
    private val userDao: UserDao,
    private val selectedFamilySource: SelectedFamilySource,
    private val syncRequester: SyncRequester,
    // The localized conversation-title fallback, which only the platform's resources hold.
    private val wording: PairingWording
) : PairingRepository {

    /**
     * What [onPairingStateObserved] last mirrored into Room, so repeated emissions of an unchanged
     * link do no work. Null means "nothing applied yet".
     *
     * Shared by every collector of [observePairingState]: each one has listeners of its own and
     * runs the hook on every value, and the `exchange` here (Java's `getAndSet`) is what lets
     * exactly one of them act on a given change.
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
        authService.observeCurrentUid()
            .flatMapLatest { uid ->
                if (uid == null) {
                    // Signed out (or auth has not restored its session yet). There is no
                    // dedicated "unauthenticated" case in PairingState, and Loading is
                    // accurate either way: as soon as auth resolves to a user, flatMapLatest
                    // restarts this branch with the real Firestore-backed flow below.
                    flowOf(ObservedPairing(PairingState.Loading))
                } else {
                    combine(
                        observeLinkedPartner(uid),
                        observeOwnInvites(uid),
                        observeIncomingInvites(authService.getCurrentEmail().orEmpty())
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
        firestoreClient.partnerProfile(partnerUid, pairedAt = null)

    override suspend fun createOrReuseInviteCode(): Result<PairingInvite> = runPairing {
        val user = requireUser()
        val existing = firestoreClient.get(ownPendingInvites(user.uid))
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
        firestoreClient.get(ownPendingInvites(user.uid))
            .filter { it.string("toEmail").orEmpty().isEmpty() }
            .filter { it.isCoParentInvite() }
            .forEach { firestoreClient.update(DocumentPath(INVITATIONS, it.id), "status", STATUS_CANCELLED) }
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
        firestoreClient.update(DocumentPath(INVITATIONS, invitationId), "status", STATUS_REJECTED)
    }

    override suspend fun unpair(partnerUid: String?): Result<Unit> = pairingFunctions.unpair(partnerUid).map { }

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
        val uid = authService.getCurrentUid() ?: return
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
        val previous = appliedPairing.exchange(applied)
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
            logger.w(TAG, "Failed to mirror the pairing transition into Room", e)
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
        val stored = StoredJson.decodeOrNull(StoredJson.strings, local.partnerIdsJson).orEmpty()
        val nextJson = StoredJson.encode(StoredJson.strings, stored + coParents.filterNot { it in stored })
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
                val partnerId = snapshot.string("partnerId").orEmpty()
                PartnerLink(
                    partnerId = partnerId,
                    pairedAt = snapshot.long("pairedAt"),
                    coParents = coParentUids(snapshot.data?.get("partnerIds"), partnerId)
                )
            }
            .distinctUntilChanged()
            .flatMapLatest { link ->
                if (link.partnerId.isEmpty()) {
                    flowOf(LinkedPartner(partner = null, coParents = link.coParents))
                } else {
                    firestoreClient.partnerProfile(link.partnerId, link.pairedAt)
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
    private fun observeUserDocument(uid: String): Flow<StoredDocument> =
        firestoreClient.observe(DocumentPath(USERS, uid))
            .catch { error ->
                // The SDK listener this was reported its error once and then stayed silent.
                logger.w(TAG, "User document listener failed", error)
                awaitCancellation()
            }

    private fun observeOwnInvites(uid: String): Flow<List<PairingInvite>> =
        firestoreClient.observe(ownPendingInvites(uid))
            .map { it.toActiveCodeInvites() }
            .catch { error ->
                // An empty list, as the SDK listener's null snapshot gave, and then nothing.
                logger.w(TAG, "Own-invitations listener failed", error)
                emit(emptyList())
                awaitCancellation()
            }

    private fun observeIncomingInvites(email: String): Flow<List<PairingInvite>> {
        val normalized = email.trim().lowercase()
        if (normalized.isEmpty()) return flowOf(emptyList())
        val query = DocumentQuery(INVITATIONS)
            .whereEqualTo("toEmail", normalized)
            .whereEqualTo("status", STATUS_PENDING)
        return firestoreClient.observe(query)
            .map { documents -> documents.mapNotNull { it.toInvite() } }
            .catch { error ->
                // The address itself is deliberately not logged — logcat is
                // readable by anyone with adb access to the device.
                logger.w(TAG, "Incoming-invitations listener failed", error)
                emit(emptyList())
                awaitCancellation()
            }
    }

    private suspend fun writeNewInvite(toEmail: String): PairingInvite {
        val user = requireUser()
        val profile = firestoreClient.get(DocumentPath(USERS, user.uid))
        val ttl = if (toEmail.isEmpty()) CODE_TTL_MILLIS else EMAIL_TTL_MILLIS
        val invite = PairingInvite(
            id = Uuid.random().toString(),
            code = InviteCodeGenerator.generate(),
            fromUserId = user.uid,
            fromUserName = profile.string("name") ?: user.email.orEmpty(),
            fromUserEmail = user.email.orEmpty(),
            toEmail = toEmail,
            expiresAtMillis = nowMillis() + ttl
        )
        firestoreClient.set(
            DocumentPath(INVITATIONS, invite.id),
            mapOf(
                "id" to invite.id,
                "code" to invite.code,
                "fromUserId" to invite.fromUserId,
                "fromUserName" to invite.fromUserName,
                "fromUserEmail" to invite.fromUserEmail,
                "toEmail" to invite.toEmail,
                "status" to STATUS_PENDING,
                "createdAt" to nowMillis(),
                "expiresAt" to invite.expiresAtMillis,
                "acceptedBy" to null
            )
        )
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
        val uid = authService.getCurrentUid() ?: return
        if (partnerId.isEmpty()) return
        try {
            // The partner's display name titles the thread; their profile may not carry one
            // yet, in which case a localized placeholder stands in.
            val partnerName = firestoreClient.get(DocumentPath(USERS, partnerId))
                .string("name")
                .orEmpty()
                .ifEmpty { wording.defaultPartnerName() }
            postPairingConversationSetup.run(uid, partnerId, partnerName)
        } catch (e: CancellationException) {
            throw e
        } catch (
            @Suppress("TooGenericExceptionCaught") e: Exception
        ) {
            logger.w(TAG, "Failed to create the post-pairing conversation", e)
        }
    }

    /**
     * The signed-in account's uid and address, or a "Not signed in" [PairingException]. Both are
     * read now, as the Android SDK's `currentUser` answered them in one object.
     */
    private fun requireUser(): Inviter {
        val uid = authService.getCurrentUid() ?: throw PairingException(PairingError.Unknown("Not signed in"))
        return Inviter(uid = uid, email = authService.getCurrentEmail())
    }

    /** Who is asking: [requireUser]'s answer. */
    private data class Inviter(val uid: String, val email: String?)

    /**
     * Runs a Firestore block and normalizes any failure into a [PairingException].
     *
     * The lambda is `suspend` because every body calls the client's suspending reads and
     * writes; an inline non-suspending version will not compile here. Cancellation must
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

        /** This account's pending invitations, as the reuse lookup, the revoke and the listener ask. */
        fun ownPendingInvites(uid: String): DocumentQuery = DocumentQuery(INVITATIONS)
            .whereEqualTo("fromUserId", uid)
            .whereEqualTo("status", STATUS_PENDING)
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
private fun StoredDocument.isCoParentInvite(): Boolean = string("kind").isNullOrEmpty()

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
private fun List<StoredDocument>.toActiveCodeInvites(): List<PairingInvite> {
    val now = nowMillis()
    return this
        .filter { it.string("toEmail").orEmpty().isEmpty() }
        .filter { it.isCoParentInvite() }
        .filter { (it.long("expiresAt") ?: 0L) > now }
        .sortedByDescending { it.long("createdAt") ?: 0L }
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
private fun StoredDocument.toInvite(): PairingInvite? {
    val code = string("code") ?: return null
    return PairingInvite(
        id = string("id") ?: id,
        code = code,
        fromUserId = string("fromUserId").orEmpty(),
        fromUserName = string("fromUserName").orEmpty(),
        fromUserEmail = string("fromUserEmail").orEmpty(),
        toEmail = string("toEmail").orEmpty(),
        expiresAtMillis = long("expiresAt") ?: 0L
    )
}

/**
 * The number in [field] as a Long, or null when the field is absent or null. Throws when it holds
 * something else, as the SDK's `DocumentSnapshot.getLong` does, with its words.
 */
// The SDK's own exception type, so a caller's `catch` and its message stay what they were.
@Suppress("TooGenericExceptionThrown")
private fun StoredDocument.long(field: String): Long? = when (val value = data?.get(field)) {
    null -> null
    is Number -> value.toLong()
    // The SDK names the JVM class; this is that text, not a JVM call.
    else -> throw RuntimeException("Field '$field' is not a java.lang.Number")
}

/** This phone's clock, in epoch milliseconds: what `System.currentTimeMillis()` answered. */
private fun nowMillis(): Long = Clock.System.now().toEpochMilliseconds()

/**
 * [code] trimmed and upper-cased, or null when it cannot be a code at all — refused here as
 * [PairingError.NotFound] rather than spending a callable on it.
 */
private fun normalizedInviteCode(code: String): String? =
    code.trim().uppercase().takeIf { InviteCodeGenerator.isValid(it) }
