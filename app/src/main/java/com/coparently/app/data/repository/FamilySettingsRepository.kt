package com.coparently.app.data.repository

import android.util.Log
import com.coparently.app.data.local.preferences.EncryptedPreferences
import com.coparently.app.data.remote.firebase.FcmService
import com.coparently.app.data.remote.firebase.FirestoreFamilySettingsDataSource
import com.coparently.app.data.remote.firebase.PushPayload
import com.coparently.app.domain.expenses.FULL_SHARE_BASIS_POINTS
import com.coparently.app.domain.expenses.FamilySettings
import com.coparently.app.domain.expenses.SplitRatio
import com.coparently.app.domain.expenses.SplitRatioTransition
import com.coparently.app.domain.repository.UserRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import javax.inject.Inject
import javax.inject.Singleton

/**
 * How a submitted ratio was applied.
 *
 * The screen has to say which happened: "saved" and "sent to your co-parent" are different
 * outcomes and a parent who is told the first when the second is true will believe a split they
 * have not agreed.
 */
enum class RatioSubmission {
    /** Applied straight away — nobody had to agree, because there is no co-parent yet. */
    APPLIED,

    /** Put to the co-parent. The agreed ratio does not move until they answer. */
    PROPOSED
}

/**
 * One family's expense split as a screen shows it: the agreement, any open proposal, and **which
 * family** they belong to (M-8 — a parent in two families sees one at a time).
 *
 * The family travels with the figures so that an answer goes to the family whose proposal was on
 * screen when it was tapped. Reading the family again at tap time is how a parent who switched
 * from the family with Igor to the family with Carol could accept a proposal of Carol's they had
 * never seen (October 2026 isolation audit).
 *
 * @property familyId The family the figures belong to — the id of its `family_settings`
 *   document — or null while the account has no co-parent.
 * @property settings That family's document, or null while it has none, before its listener has
 *   answered, or after the listener failed.
 * @property ratio What the screen shows and a new expense is priced at: the document's agreed
 *   ratio, else this family's own last-known agreement, else half each — never another family's.
 * @property answered False until the family's document has answered once. A proposal is only
 *   known after that, so nothing is drawn for a family that has not answered yet.
 */
data class FamilySplit(
    val familyId: String?,
    val settings: FamilySettings?,
    val ratio: SplitRatio,
    val answered: Boolean = true
) {
    companion object {
        /** Nothing known yet: the starting value of a screen's state. */
        val UNKNOWN = FamilySplit(familyId = null, settings = null, ratio = SplitRatio.EVEN, answered = false)
    }
}

/**
 * The two parents' agreement about how a shared cost divides.
 *
 * Shaped after `CustodyModelRepository`, and the one piece worth copying deliberately is
 * [submitRatio]'s branch: unpaired, or a pair with no document yet, applies the ratio outright;
 * a pair that already has one proposes it. That is what makes "set it at registration" work even
 * though pairing is the last step of the wizard — there is nobody to agree with yet, so there is
 * nothing to agree.
 *
 * **Every answer names its family.** A parent in two families has two agreements, one per
 * `family_settings/{familyId}` document. [observeSplit] follows the family on screen and says
 * which family its figures are; [submitRatio], [acceptProposal], [declineProposal] and
 * [withdrawProposal] act on the family the caller passes — the one whose figures the screen
 * showed — never on whatever the switcher shows by the time the write runs.
 *
 * **The agreed ratio is cached locally, per family.** The balance math is offline-first and runs
 * on every expense screen, so it cannot wait on a document read; the cache is written on every
 * observed change and read by [agreedRatioOrDefault]. It is keyed by family since the October 2026
 * isolation audit: one device-wide number priced the family with Carol at the split agreed with
 * Igor. The *proposal* is deliberately not cached — it is Firestore-only, exactly as the custody
 * proposal is, because a pending question is not state the app should answer from memory.
 */
@Singleton
class FamilySettingsRepository @Inject constructor(
    private val dataSource: FirestoreFamilySettingsDataSource,
    private val userRepository: UserRepository,
    private val preferences: EncryptedPreferences,
    private val fcmService: FcmService
) {

    /** The signed-in parent's families, read from their own Room row (M-8). */
    private val families = SignedInFamiliesReader(userRepository)

    private val documents = SettingsDocuments(dataSource)

    /**
     * The split of the family on screen, re-read on every switch.
     *
     * The family used to be read once when collection started, so a screen that stayed open
     * across a switch — the Expenses tab carries the switcher in its own top bar — went on showing
     * the previous family's agreement and its proposal. Each family emits a value that is not yet
     * [FamilySplit.answered] first, carrying that family's own cached ratio, so nothing of the
     * previous family lingers while the new one's listener connects.
     *
     * An unpaired account emits one answered value with no family: a family of one has no
     * agreement to read, and a screen must render that as "even split", not as an error.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    fun observeSplit(): Flow<FamilySplit> = families.observe()
        .map { it.pair }
        .distinctUntilChanged()
        .flatMapLatest { pair -> splitOf(pair) }

    /**
     * The settings of the family on screen, or null while it has none; follows every switch.
     *
     * The document as [observeSplit] answers it, without the placeholder each family starts with,
     * so a first value is the family's real answer.
     */
    fun observeSettings(): Flow<FamilySettings?> = observeSplit()
        .filter { it.answered }
        .map { it.settings }

    /** One family's split, from its own document, cached as it arrives. */
    private fun splitOf(pair: CustodyPair?): Flow<FamilySplit> {
        val familyId = pair?.documentId
            ?: return flowOf(FamilySplit(familyId = null, settings = null, ratio = agreedRatioOrDefault(null)))
        return dataSource.observeSettings(familyId)
            .map { settings ->
                settings?.ratio?.let { cacheAgreedRatio(familyId, it) }
                FamilySplit(familyId, settings, settings?.ratio ?: agreedRatioOrDefault(familyId))
            }
            .onStart { emit(FamilySplit(familyId, null, agreedRatioOrDefault(familyId), answered = false)) }
            .catch { cause ->
                // Room is not the source of truth here — the cached ratio is — so a denial or an
                // offline cold cache degrades to "what this family last agreed", never to a crash
                // inside a `viewModelScope.launch`.
                Log.w(TAG, "The family settings listener ended", cause)
                emit(FamilySplit(familyId, null, agreedRatioOrDefault(familyId)))
            }
    }

    /**
     * The agreed ratio of [familyId] to price a **new** expense at — this family's own, or half
     * each when this phone has never seen it agree one. Never another family's.
     *
     * Read from the local cache rather than from Firestore: this is called on the save path of
     * every expense, and blocking a save on a document read would make an offline save fail for
     * a reason that has nothing to do with the expense.
     *
     * @param familyId The family the expense belongs to, or null for an account with no
     *   co-parent, which prices by the ratio it chose while unpaired.
     */
    fun agreedRatioOrDefault(familyId: String?): SplitRatio {
        val stored = if (familyId == null) {
            preferences.getSplitRatioBasisPoints()
        } else {
            preferences.getFamilySplitRatioBasisPoints(familyId)
        }
        return SplitRatio.fromStored(stored) ?: SplitRatio.EVEN
    }

    /** [agreedRatioOrDefault] for the family on screen now, for a wizard that asks once. */
    suspend fun agreedRatioOnScreen(): SplitRatio = agreedRatioOrDefault(families.current().pair?.documentId)

    /**
     * Records [familyId]'s agreed ratio locally, so the save path can read it without a round trip.
     *
     * Clears any capture slot: a family's document now carries an agreement, and the ratio chosen
     * while unpaired must not be published to a later family that has none — the same reason
     * [reanchored] refuses a figure with no capture slot. Only [rememberUnpairedRatio] stores a
     * value that still needs anchoring.
     */
    fun cacheAgreedRatio(familyId: String, ratio: SplitRatio) {
        preferences.putFamilySplitRatioBasisPoints(familyId, ratio.momShareBasisPoints)
        preferences.putSplitRatioSlot(null)
    }

    /**
     * Records a ratio chosen while there is nobody to agree it with, **and the slot it means**.
     *
     * The stored share is slot 1's — that is the schema, and `Expense.splitBasisPoints` and
     * `firestore.rules` both speak it — while what a parent sets is *their own* share. Those
     * coincide only while this device holds slot 1, which an unpaired account does by default
     * (`UserRepositoryImpl.DEFAULT_ROLE`) and which pairing can change: `assignSlots` hands out
     * the two slots, nobody chooses one. Without the capture slot, a parent who set "I pay 70",
     * then paired into slot 2, would have published a document giving *the co-parent* 70.
     */
    private suspend fun rememberUnpairedRatio(ratio: SplitRatio) {
        preferences.putSplitRatioBasisPoints(ratio.momShareBasisPoints)
        preferences.putSplitRatioSlot(signedInSlot())
    }

    /**
     * Applies [ratio] to the family on screen now, or puts it to that family's co-parent.
     *
     * For a wizard, which asks once and saves at once. A screen that shows a family's split passes
     * that family to the other overload instead.
     */
    suspend fun submitRatio(ratio: SplitRatio): Result<RatioSubmission> =
        submitRatio(ratio, families.current().pair?.documentId)

    /**
     * Applies [ratio] to [familyId], or puts it to that family's co-parent when there is one to ask.
     *
     * @param familyId The family whose split the screen showed, or null for an account with no
     *   co-parent. A family the signed-in parent is no longer in is refused: its document is not
     *   theirs to write any more.
     * @return which of the two happened, or a failure carrying the transition's own refusal.
     */
    suspend fun submitRatio(ratio: SplitRatio, familyId: String?): Result<RatioSubmission> {
        if (familyId == null) {
            // Nobody to agree with, and no document to write it to. Cached so the expense screen
            // prices by it immediately; [publishCachedRatioIfMissing] is what carries it across
            // to the pair, and it runs on the next sync after pairing. The slot goes with it —
            // see [rememberUnpairedRatio] for why a bare number is not enough.
            rememberUnpairedRatio(ratio)
            return Result.success(RatioSubmission.APPLIED)
        }
        val pair = families.current().pairIn(familyId) ?: return noCoParent()
        return agreeOrPropose(pair, ratio)
    }

    /** [submitRatio] for a family the signed-in parent is in: agree outright, or propose. */
    private suspend fun agreeOrPropose(pair: CustodyPair, ratio: SplitRatio): Result<RatioSubmission> {
        val now = System.currentTimeMillis()
        val existing = documents.read(pair.documentId) ?: return agreeFirst(pair, ratio, now)
        return SplitRatioTransition.propose(existing, ratio, pair.myUid, now).fold(
            onSuccess = { proposed ->
                documents.write(pair, proposed).map {
                    notifyPartner(pair, PushPayload.SPLIT_RATIO_PROPOSED)
                    RatioSubmission.PROPOSED
                }
            },
            onFailure = { Result.failure(it) }
        )
    }

    /** The pair's first agreement: nobody has agreed anything yet, so there is nothing to ask. */
    private suspend fun agreeFirst(pair: CustodyPair, ratio: SplitRatio, now: Long): Result<RatioSubmission> {
        val settings = FamilySettings(
            ratio = ratio,
            participants = pair.participants,
            lastModifiedBy = pair.myUid,
            lastModifiedAtMillis = now
        )
        return documents.write(pair, settings).map {
            cacheAgreedRatio(pair.documentId, ratio)
            // The pair's first agreement, made from a screen rather than carried over from
            // the wizard — the same announcement `publishCachedRatioIfMissing` makes, for
            // the same reason (UX-18): it prices every expense from this moment, and the
            // co-parent otherwise learned of it only by opening Settings. With the link now
            // made first, a second parent's wizard reaches this branch routinely.
            notifyPartner(pair, PushPayload.SPLIT_RATIO_AGREED)
            RatioSubmission.APPLIED
        }
    }

    /**
     * Carries a ratio agreed while unpaired across to the pair, once there is one.
     *
     * The unpaired branch of [submitRatio] can only cache: there is no pair document to write and
     * nobody to ask. Nothing then published it — the comment there used to claim "the first write
     * after pairing publishes it" and no such write existed — so a parent who set 70/30 in the
     * wizard paired, saw 70/30 in Settings, and had both phones go on splitting every expense
     * evenly. The onboarding step's own wording promised the co-parent would confirm it; they
     * never saw it at all.
     *
     * Writes **only when the pair has no agreement yet**, which is what makes it safe to run on
     * every sync: it can never overwrite one the two of them have since made, nor the co-parent's
     * own cached ratio if theirs published first. Whoever's tick lands first sets the pair's
     * opening figure, and the other's next change is a proposal like any other.
     *
     * Silent about an unset cache: `null` means this parent never chose a ratio, and publishing
     * an even split on their behalf would be inventing an agreement.
     */
    suspend fun publishCachedRatioIfMissing() {
        val cached = SplitRatio.fromStored(preferences.getSplitRatioBasisPoints()) ?: return
        val pair = families.current().pair ?: return
        if (documents.read(pair.documentId) != null) return

        val anchored = reanchored(cached) ?: return
        Log.i(TAG, "Publishing the ratio agreed before pairing to ${pair.documentId}")
        documents.write(
            pair,
            FamilySettings(
                ratio = anchored,
                participants = pair.participants,
                lastModifiedBy = pair.myUid,
                lastModifiedAtMillis = System.currentTimeMillis()
            )
        ).onSuccess {
            // The document is the record now; the cache goes back to merely mirroring it, and the
            // capture slot has done its one job.
            cacheAgreedRatio(pair.documentId, anchored)
            // **And the co-parent is told (UX-18).** This write makes one parent's wizard answer
            // the pair's agreement of record, priced onto every expense from this moment; until
            // it was announced the other parent learned of it only by opening Settings.
            //
            // Its own type, not `SPLIT_RATIO_PROPOSED`: there is nothing here to confirm or
            // decline, and routing it through the proposal path — the tempting fix — would leave
            // an unanswered proposal splitting the pair evenly, which is the exact bug this
            // method was written to end.
            notifyPartner(pair, PushPayload.SPLIT_RATIO_AGREED)
        }
    }

    /**
     * The cached share expressed as **slot 1's**, whatever slot it was captured under.
     *
     * Flips it when pairing moved this device to the other slot: what the parent set was their
     * own share, and the stored form is slot 1's. Returns null — publishing nothing — when the
     * current slot is unknown, because a coin toss here writes the wrong number into the one
     * document that prices every future expense; and when there is **no capture slot at all**,
     * which marks a figure that already belongs to a pair rather than one chosen before there
     * was one. Silence merely leaves a pair on an even split they can still change.
     */
    private suspend fun reanchored(cached: SplitRatio): SplitRatio? {
        val capturedSlot = preferences.getSplitRatioSlot()
            // No capture slot means this figure is already some pair's agreement of record —
            // every paired write clears it, see [cacheAgreedRatio] — so it belongs to that pair
            // and to no other. Nothing clears the unpaired number on unpair: the client only
            // invokes the callable, and `AccountSwitchGuard` wipes Room and deliberately not the
            // preferences. Publishing it here would hand the *next* co-parent a split neither of
            // them made, reviving on the client the very document `unpairCoParent` deletes on the
            // server for that reason — "left behind, it would silently reattach if these two ever
            // re-paired".
            ?: return null
        val currentSlot = signedInSlot() ?: return null
        return if (capturedSlot == currentSlot) {
            cached
        } else {
            SplitRatio(FULL_SHARE_BASIS_POINTS - cached.momShareBasisPoints)
        }
    }

    /** This device's slot, or null when it has no local profile row yet. */
    private suspend fun signedInSlot(): String? {
        val uid = userRepository.getCurrentUserId() ?: return null
        return userRepository.getUserById(uid)?.role?.takeIf { it.isNotBlank() }
    }

    /**
     * Agrees to the co-parent's proposal in [familyId]; the proposed ratio becomes the agreed one.
     *
     * @param familyId The family whose proposal the screen showed.
     */
    suspend fun acceptProposal(familyId: String): Result<Unit> = decide(familyId, accept = true)

    /**
     * Turns the co-parent's proposal in [familyId] down; the agreed ratio does not move.
     *
     * @param familyId The family whose proposal the screen showed.
     */
    suspend fun declineProposal(familyId: String): Result<Unit> = decide(familyId, accept = false)

    /**
     * Takes this parent's own pending proposal in [familyId] back (UX-17).
     *
     * `SplitRatioTransition.withdraw` has existed and been unit-tested since the feature landed;
     * nothing called it, so a parent who proposed 70/30 by mistake could only wait for the
     * co-parent to answer it. The custody schedule has had the whole shape — transition,
     * repository method and a Withdraw button — all along, and this is the split ratio catching
     * up with its sibling.
     *
     * **No push.** The other three answers announce a decision the co-parent has to know about;
     * a withdrawal decides nothing, and their banner is derived from the document, so it goes on
     * their next read. Adding a type for it would cost the four places SEC-3 requires to agree,
     * for a notification that says something stopped existing.
     *
     * The transition refuses a withdrawal by anyone but the proposer, so a stale tap on the
     * co-parent's device fails rather than silently cancelling an answer they were owed.
     *
     * @param familyId The family whose proposal the screen showed.
     */
    suspend fun withdrawProposal(familyId: String): Result<Unit> {
        val pair = families.current().pairIn(familyId) ?: return noCoParent()
        val existing = documents.read(pair.documentId)
            ?: return Result.failure(IllegalStateException("There is no proposal to withdraw"))
        return SplitRatioTransition.withdraw(existing, pair.myUid).fold(
            onSuccess = { next -> documents.write(pair, next) },
            onFailure = { Result.failure(it) }
        )
    }

    private suspend fun decide(familyId: String, accept: Boolean): Result<Unit> {
        val pair = families.current().pairIn(familyId) ?: return noCoParent()
        val existing = documents.read(pair.documentId)
            ?: return Result.failure(IllegalStateException("There is no proposal to answer"))
        val now = System.currentTimeMillis()
        val decided = if (accept) {
            SplitRatioTransition.accept(existing, pair.myUid, now)
        } else {
            SplitRatioTransition.decline(existing, pair.myUid, now)
        }
        return decided.fold(
            onSuccess = { next ->
                documents.write(pair, next).map {
                    if (accept) cacheAgreedRatio(pair.documentId, next.ratio)
                    notifyPartner(
                        pair,
                        if (accept) PushPayload.SPLIT_RATIO_ACCEPTED else PushPayload.SPLIT_RATIO_DECLINED
                    )
                }
            },
            onFailure = { Result.failure(it) }
        )
    }

    /**
     * A best-effort push carrying a **type and nothing else** (SEC-3): the receiving device
     * writes the sentence from its own resources, in the reader's language.
     */
    private suspend fun notifyPartner(pair: CustodyPair, type: String) {
        val partnerUid = pair.participants.firstOrNull { it != pair.myUid } ?: return
        fcmService.queueNotificationForUser(
            targetUserId = partnerUid,
            notificationData = mapOf(PushPayload.TYPE to type)
        )
    }

    private companion object {
        const val TAG = "FamilySettingsRepo"

        /** The refusal for a family the signed-in parent is not in, or no longer in. */
        fun <T> noCoParent(): Result<T> =
            Result.failure(IllegalStateException("No co-parent to agree a split with"))
    }
}

/**
 * The family settings documents, read and written without ever throwing: a refused or failed
 * read is no document, a refused write is a failure the caller reports.
 */
private class SettingsDocuments(private val dataSource: FirestoreFamilySettingsDataSource) {

    suspend fun read(documentId: String): FamilySettings? = try {
        dataSource.getSettings(documentId)
    } catch (e: CancellationException) {
        throw e
    } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
        Log.w(TAG, "Could not read the family settings document", e)
        null
    }

    suspend fun write(pair: CustodyPair, settings: FamilySettings): Result<Unit> = try {
        dataSource.setSettings(pair.documentId, settings)
        Result.success(Unit)
    } catch (e: CancellationException) {
        throw e
    } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
        Log.w(TAG, "The family settings write was refused", e)
        Result.failure(e)
    }

    private companion object {
        const val TAG = "FamilySettingsRepo"
    }
}
