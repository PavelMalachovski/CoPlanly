package com.coparently.app.data.repository

import android.util.Log
import com.coparently.app.data.local.preferences.EncryptedPreferences
import com.coparently.app.data.remote.firebase.FcmService
import com.coparently.app.data.remote.firebase.FirestoreFamilySettingsDataSource
import com.coparently.app.data.remote.firebase.PushPayload
import com.coparently.app.domain.activity.ActivityAnnouncement
import com.coparently.app.domain.activity.ActivityAnnouncer
import com.coparently.app.domain.activity.ActivityEntityType
import com.coparently.app.domain.activity.ActivityKind
import com.coparently.app.domain.changerequests.AnswerBy
import com.coparently.app.domain.changerequests.AnswerByPick
import com.coparently.app.domain.changerequests.RequestKind
import com.coparently.app.domain.changerequests.RequestStart
import com.coparently.app.domain.expenses.FULL_SHARE_BASIS_POINTS
import com.coparently.app.domain.expenses.FamilySettings
import com.coparently.app.domain.expenses.SplitRatio
import com.coparently.app.domain.expenses.SplitRatioProposal
import com.coparently.app.domain.expenses.SplitRatioTransition
import com.coparently.app.domain.repository.RequestDeadlineRepository
import com.coparently.app.domain.repository.UserRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.withContext
import java.time.ZoneId
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
 *
 * **A proposal is a request** (October 2026, owner decision), like a custody proposal: it may carry
 * a deadline, whose clock starts once the proposal is written ([submitRatio]), and each step — the
 * proposal, the answer, a withdrawal — is a card in the family's thread as well as a push, so the
 * proposer sees it delivered and read, and the export keeps it. At "No answer" the clock's end
 * withdraws it ([withdrawUnanswered]).
 *
 * @param activityAnnouncer Posts the chat cards, each to the thread of the family it is about.
 * @param fcmService Queues the pushes, a type and nothing else (SEC-3).
 * @param requestDeadlines Starts a proposal's clock.
 */
@Singleton
class FamilySettingsRepository @Inject constructor(
    private val dataSource: FirestoreFamilySettingsDataSource,
    private val userRepository: UserRepository,
    private val preferences: EncryptedPreferences,
    fcmService: FcmService,
    activityAnnouncer: ActivityAnnouncer,
    requestDeadlines: RequestDeadlineRepository
) {

    /** The signed-in parent's families, read from their own Room row (M-8). */
    private val families = SignedInFamiliesReader(userRepository)

    private val documents = SettingsDocuments(dataSource)

    private val telling = SplitTelling(activityAnnouncer, fcmService, requestDeadlines)

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

    /**
     * The settings of [familyId], live, by the document's id: null while they are unknown — before
     * the listener has answered — while the family has none, and once the listener has failed. It
     * never throws.
     *
     * For a screen that speaks for one family, the Requests inbox above all: asked by its id, it
     * never answers with another family's proposal, whatever the switcher shows by then.
     *
     * @param familyId The family's id, which is also its document's.
     */
    fun observeSettingsOf(familyId: String): Flow<FamilySettings?> = dataSource.observeSettings(familyId)
        .onStart { emit(null) }
        .catch { cause ->
            // A denial or an offline cold cache is "no proposal known", never a crash in a collector.
            Log.w(TAG, "A family's settings listener ended", cause)
            emit(null)
        }
        .distinctUntilChanged()

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
        preferences.putSplitRatioSlot(userRepository.signedInSlot())
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
     * A proposal is a request (October 2026): it is posted to the family's thread as a card, and
     * the co-parent is pushed. Given [answerBy], it also starts a clock once it is written, and the
     * push says when the answer is due.
     *
     * @param familyId The family whose split the screen showed, or null for an account with no
     *   co-parent. A family the signed-in parent is no longer in is refused: its document is not
     *   theirs to write any more.
     * @param answerBy The deadline the parent chose for the co-parent's answer, or null for none. It
     *   rides on a proposal only — a ratio applied outright has nobody to wait for — and a split has
     *   no cap: it changes no day ahead, only the expenses recorded once it is agreed.
     * @return which of the two happened, or a failure carrying the transition's own refusal.
     */
    suspend fun submitRatio(
        ratio: SplitRatio,
        familyId: String?,
        answerBy: AnswerByPick? = null
    ): Result<RatioSubmission> {
        if (familyId == null) {
            // Nobody to agree with, and no document to write it to. Cached so the expense screen
            // prices by it immediately; [publishCachedRatioIfMissing] is what carries it across
            // to the pair, and it runs on the next sync after pairing. The slot goes with it —
            // see [rememberUnpairedRatio] for why a bare number is not enough.
            rememberUnpairedRatio(ratio)
            return Result.success(RatioSubmission.APPLIED)
        }
        val pair = families.current().pairIn(familyId) ?: return noCoParent()
        return agreeOrPropose(pair, ratio, answerBy)
    }

    /** [submitRatio] for a family the signed-in parent is in: agree outright, or propose. */
    private suspend fun agreeOrPropose(
        pair: CustodyPair,
        ratio: SplitRatio,
        answerBy: AnswerByPick?
    ): Result<RatioSubmission> {
        val now = System.currentTimeMillis()
        val existing = documents.read(pair.documentId) ?: return agreeFirst(pair, ratio, now)
        return SplitRatioTransition.propose(existing, ratio, pair.myUid, now).fold(
            onSuccess = { proposed ->
                documents.write(pair, proposed).map {
                    // The clock starts once the proposal is written and before it is announced, so
                    // the push can say when the answer is due — and only when the clock was written.
                    //
                    // **Not cancellable from here on.** The server has the proposal, and the clock
                    // waits for the server for up to ten seconds, during which the dialog can be
                    // left: a caller cancelled that way must not leave the proposal on the server
                    // with no clock, no card and no push. Before the write is acknowledged nothing is
                    // shielded, and leaving cancels.
                    withContext(NonCancellable) {
                        proposed.proposal?.let { telling.proposed(pair, it, answerBy) }
                    }
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
            telling.push(pair, PushPayload.SPLIT_RATIO_AGREED)
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
            telling.push(pair, PushPayload.SPLIT_RATIO_AGREED)
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
        val currentSlot = userRepository.signedInSlot() ?: return null
        return if (capturedSlot == currentSlot) {
            cached
        } else {
            SplitRatio(FULL_SHARE_BASIS_POINTS - cached.momShareBasisPoints)
        }
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
     * **No push type of its own.** The other three answers announce a decision the co-parent has to
     * know about; a withdrawal decides nothing, and their banner is derived from the document, so
     * it goes on their next read. Adding a type for it would cost the four places SEC-3 requires to
     * agree, for a notification that says something stopped existing. Since the proposal became a
     * request (October 2026) the withdrawal is a card in the thread ([ActivityKind.SPLIT_WITHDRAWN]),
     * so the record says the question was taken back rather than left to lapse.
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
            onSuccess = { next ->
                documents.write(pair, next).onSuccess {
                    withContext(NonCancellable) { telling.withdrawn(pair) }
                }
            },
            onFailure = { Result.failure(it) }
        )
    }

    /**
     * Withdraws this parent's expense split proposal in [familyId] when it is still the one made at
     * [proposedAtMillis]: the end of a proposal nobody answered (phase 3a, spec §4.4, applied to the
     * split in October 2026).
     *
     * A step of a clock acts on the clock's family, never the one on screen, so [familyId] is read
     * against this parent's families, once: it must still be a family they are in. The read and the
     * write are one transaction, so a proposal answered, withdrawn or replaced a moment before is
     * left as it is — an answer racing the end wins. Nothing is announced here: the end posts its
     * own "No answer" card, never [ActivityKind.SPLIT_WITHDRAWN].
     *
     * @return Whether the proposal was withdrawn: false when the family has ended, when the proposal
     *   is no longer this one, and when the document could not be written, for the caller to retry.
     */
    suspend fun withdrawUnanswered(familyId: String, proposedAtMillis: Long): Boolean {
        val pair = families.current().pairIn(familyId) ?: return false
        val written = documents.transact(pair.documentId) { current ->
            withdrawnIfUnanswered(current, proposedAtMillis, pair.myUid)
        } ?: return false
        // As every write of the agreement does; a withdrawal leaves the agreed ratio where it was.
        cacheAgreedRatio(pair.documentId, written.ratio)
        return true
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
                    // The server has the answer: its card and its push go to the proposer even if
                    // the screen that answered is left meanwhile, as a custody decision's do.
                    withContext(NonCancellable) { telling.decided(pair, accept) }
                }
            },
            onFailure = { Result.failure(it) }
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

    /**
     * [FirestoreFamilySettingsDataSource.transact], with a refused or failed transaction — offline
     * among them — read as nothing written.
     */
    suspend fun transact(documentId: String, transform: (FamilySettings?) -> FamilySettings?): FamilySettings? = try {
        dataSource.transact(documentId, transform)
    } catch (e: CancellationException) {
        throw e
    } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
        Log.w(TAG, "The family settings transaction did not go through", e)
        null
    }

    private companion object {
        const val TAG = "FamilySettingsRepo"
    }
}

/**
 * What the money agreement tells the co-parent about a proposal (October 2026): the clock a proposal
 * given a deadline starts, the chat cards that make each step a record in the family's thread — the
 * proposal, the answer, a withdrawal — and the pushes.
 *
 * **Always the pair it is handed**, the family whose document was just written, never the family on
 * screen, which a push tap may have switched by then. Best-effort, like every announcement: a card
 * or a push that cannot be sent never undoes the write that caused it.
 */
private class SplitTelling(
    private val announcer: ActivityAnnouncer,
    private val fcmService: FcmService,
    private val requestDeadlines: RequestDeadlineRepository
) {

    /**
     * A new proposal, in the order a custody proposal is told: its clock when [answerBy] gives a
     * deadline, then its card, then its push — which says when the answer is due only when the clock
     * was written.
     */
    suspend fun proposed(pair: CustodyPair, proposal: SplitRatioProposal, answerBy: AnswerByPick?) {
        val answerByMillis = splitClock(pair, proposal, answerBy)?.let { requestDeadlines.start(it)?.answerByMillis }
        card(pair, ActivityKind.SPLIT_PROPOSED, amount = proposal.ratio.momShareBasisPoints.toString())
        push(pair, PushPayload.SPLIT_RATIO_PROPOSED, answerByMillis)
    }

    /** The co-parent's answer, told to the proposer: its card and its push. */
    suspend fun decided(pair: CustodyPair, accepted: Boolean) {
        card(pair, if (accepted) ActivityKind.SPLIT_ACCEPTED else ActivityKind.SPLIT_DECLINED)
        push(pair, if (accepted) PushPayload.SPLIT_RATIO_ACCEPTED else PushPayload.SPLIT_RATIO_DECLINED)
    }

    /** The proposer took their proposal back: a card, and no push of its own (UX-17). */
    suspend fun withdrawn(pair: CustodyPair) {
        card(pair, ActivityKind.SPLIT_WITHDRAWN)
    }

    /**
     * A best-effort push carrying a **type and nothing else** (SEC-3) — but for a proposal's
     * deadline, an instant the receiving device words in its reader's zone and language: the
     * receiving device writes the sentence from its own resources.
     *
     * @param answerByMillis The deadline of the proposal's clock, when one was written.
     */
    suspend fun push(pair: CustodyPair, type: String, answerByMillis: Long? = null) {
        val partnerUid = pair.otherParent() ?: return
        val data = mapOf(PushPayload.TYPE to type)
        fcmService.queueNotificationForUser(
            targetUserId = partnerUid,
            notificationData = answerByMillis?.let { data + (PushPayload.ANSWER_BY to it.toString()) } ?: data
        )
    }

    /**
     * The card about [pair]'s proposal, posted to the thread with that family's other parent. A split
     * has no name of its own, so the title is blank and the reader words it.
     */
    private suspend fun card(pair: CustodyPair, kind: ActivityKind, amount: String? = null) {
        val partnerUid = pair.otherParent() ?: return
        announcer.announce(
            announcement = ActivityAnnouncement(
                kind = kind,
                entityType = ActivityEntityType.SPLIT_PROPOSAL,
                entityId = pair.documentId,
                title = "",
                amount = amount
            ),
            partnerUid = partnerUid
        )
    }
}

/**
 * The clock an expense split proposal starts (October 2026), or null when it starts none: no
 * [answerBy] was chosen, the pair names no other parent, or the pick gives no deadline.
 *
 * Its reference is the proposal's `proposedAtMillis`, and it has no cap: a split changes no day
 * ahead — only the expenses recorded once it is agreed — so a last chance may always run a full day.
 * The deadline is worked out in the zone of the phone that proposes, the zone its form showed it in.
 */
private fun splitClock(pair: CustodyPair, proposal: SplitRatioProposal, answerBy: AnswerByPick?): RequestStart? {
    val responder = pair.otherParent()
    if (answerBy == null || responder == null) return null
    val deadline = AnswerBy.resolve(answerBy, System.currentTimeMillis(), null, ZoneId.systemDefault()) ?: return null
    return RequestStart(
        kind = RequestKind.SPLIT,
        ref = proposal.proposedAtMillis.toString(),
        familyId = pair.documentId,
        responderUid = responder,
        answerByMillis = deadline,
        capMillis = null
    )
}

/**
 * [current] with [myUid]'s proposal withdrawn when it is still the one made at [proposedAtMillis], or
 * null — nothing to write — when it is not: answered, withdrawn or replaced meanwhile, or the
 * co-parent's.
 */
private fun withdrawnIfUnanswered(current: FamilySettings?, proposedAtMillis: Long, myUid: String): FamilySettings? {
    val proposal = current?.proposal ?: return null
    if (proposal.proposedAtMillis != proposedAtMillis || proposal.proposedBy != myUid) return null
    return SplitRatioTransition.withdraw(current, myUid).getOrNull()
}

/** The other parent of [this] pair, the parent a request made here waits on; null for a malformed pair. */
private fun CustodyPair.otherParent(): String? = participants.firstOrNull { it != myUid }

/** The signed-in parent's slot, or null when they have no local profile row yet. */
private suspend fun UserRepository.signedInSlot(): String? {
    val uid = getCurrentUserId() ?: return null
    return getUserById(uid)?.role?.takeIf { it.isNotBlank() }
}
