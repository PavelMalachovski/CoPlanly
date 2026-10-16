package com.coparently.app.data.repository

import android.util.Log
import com.coparently.app.data.local.dao.CustodyModelDao
import com.coparently.app.data.local.entity.CustodyModelEntity
import com.coparently.app.data.remote.firebase.FcmService
import com.coparently.app.data.remote.firebase.FirestoreCustodyDataSource
import com.coparently.app.data.remote.firebase.PushPayload
import com.coparently.app.domain.activity.ActivityAnnouncement
import com.coparently.app.domain.activity.ActivityAnnouncer
import com.coparently.app.domain.activity.ActivityEntityType
import com.coparently.app.domain.activity.ActivityKind
import com.coparently.app.domain.changerequests.AnswerBy
import com.coparently.app.domain.changerequests.AnswerByPick
import com.coparently.app.domain.changerequests.RequestCap
import com.coparently.app.domain.changerequests.RequestKind
import com.coparently.app.domain.changerequests.RequestStart
import com.coparently.app.domain.custody.ChildScheduleOverride
import com.coparently.app.domain.custody.ContactWindow
import com.coparently.app.domain.custody.ContactWindowCodec
import com.coparently.app.domain.custody.CustodyDecisionOutcome
import com.coparently.app.domain.custody.CustodyKey
import com.coparently.app.domain.custody.CustodyProposalTransition
import com.coparently.app.domain.custody.CustodyTimestamp
import com.coparently.app.domain.custody.CustodyWriteKind
import com.coparently.app.domain.custody.DayOverride
import com.coparently.app.domain.custody.DayOverrideStatus
import com.coparently.app.domain.custody.SeasonalLayer
import com.coparently.app.domain.custody.SharedCustody
import com.coparently.app.domain.custody.SharedCustodyRead
import com.coparently.app.domain.model.CustodyModel
import com.coparently.app.domain.model.MidweekContact
import com.coparently.app.domain.repository.RequestDeadlineRepository
import com.coparently.app.domain.repository.UserRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.retryWhen
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.flow.transform
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Repository for managing custody model configurations.
 * Handles conversion between entities and domain models.
 *
 * Room is the source of truth and Firestore mirrors it. The pair shares exactly one custody
 * document, whose id is derived from the two UIDs by [CustodyKey], so both devices reach it
 * without a query and creating it is idempotent. An unpaired user writes Room and nothing else:
 * there is no second parent to share a document with.
 *
 * **Room holds one active row per family** (schema 45), each stamped with the family it is the
 * schedule of, and every read answers for the family on screen through [ActiveCustodyRow]. A row
 * reaches a family's document only if it is that family's — stamped with it, or naming no family
 * while the parent has exactly one co-parent. The phone used to hold one active row for every
 * family, and a parent in two families had the first family's schedule, seasonal layers and
 * children's overrides written into the second family's document by the mirror, the sync and the
 * pattern editor alike.
 *
 * Two over detekt's function threshold, and deliberately so: the overflow is the four `create*`
 * pattern helpers, which are one-liners over [saveAndActivate] and belong beside it. Splitting
 * the class to satisfy the count would separate the write path from the patterns it writes.
 */
/**
 * Whether a submitted custody pattern took effect immediately or is waiting for the co-parent.
 *
 * [ACTIVATED] on an unpaired account or a pair's very first schedule (nobody to ask, nothing to
 * protect); [PROPOSED] once a pair has an agreed pattern that a change must not overwrite without
 * consent (owner decision, Aug 2026 walkthrough, item 7).
 *
 * The last two are refusals, and both mean **nothing was written**, locally or remotely: a pair
 * with a shared schedule never takes a change except as a proposal, so there is no local save to
 * fall back to — the old fallback activated the pattern and pushed it over the shared document,
 * erasing the co-parent's agreed schedule and any proposal of theirs. [COPARENT_PROPOSAL_WAITING]
 * when the co-parent's own proposal waits for this parent's answer (only one proposal fits the
 * document); [NOT_SENT] when the shared document could not be read or the proposal not written,
 * so the parent can simply try again.
 */
enum class PatternSubmission { ACTIVATED, PROPOSED, COPARENT_PROPOSAL_WAITING, NOT_SENT }

/**
 * A run of days that was only partly written.
 *
 * A type rather than a message, because the screen has to *say which part*: the days that landed
 * are already pending on the co-parent's phone and have already been announced, so telling the
 * offering parent "refused" is worse than telling them nothing — they would offer the same run
 * again and the co-parent would be asked twice about days they already have.
 *
 * @property written Days that actually reached the document.
 * @property total Days the run set out to write.
 */
class PartialSwapRun(
    val written: Int,
    val total: Int,
    cause: Throwable
) : IllegalStateException("Only $written of $total days went through", cause)

@Singleton
@Suppress("TooManyFunctions")
class CustodyModelRepository
@Suppress("LongParameterList") // the request clock joins the collaborators (phase 3a)
constructor(
    private val custodyModelDao: CustodyModelDao,
    private val userRepository: UserRepository,
    private val firestoreCustodyDataSource: FirestoreCustodyDataSource,
    private val activityAnnouncer: ActivityAnnouncer,
    private val fcmService: FcmService,
    private val requestDeadlines: RequestDeadlineRepository,
    private val scope: CoroutineScope
) {
    /**
     * The constructor Hilt uses. [scope] is not part of the graph — it is a process-lifetime
     * detail of this singleton, the same way `ParentsSource` owns its own — so it is supplied
     * here rather than bound in a module.
     *
     * The constructor above exists so tests can hand in a scope backed by the test
     * scheduler and drive [observeShared] itself, backoff delays and all, on virtual time.
     * Without it the only testable seam was the unshared upstream, exposed as `internal` — a
     * production-visible way to attach a second, unshared snapshot listener, which is precisely
     * what the sharing exists to prevent.
     */
    @Inject
    constructor(
        custodyModelDao: CustodyModelDao,
        userRepository: UserRepository,
        firestoreCustodyDataSource: FirestoreCustodyDataSource,
        activityAnnouncer: ActivityAnnouncer,
        fcmService: FcmService,
        requestDeadlines: RequestDeadlineRepository
    ) : this(
        custodyModelDao,
        userRepository,
        firestoreCustodyDataSource,
        activityAnnouncer,
        fcmService,
        requestDeadlines,
        defaultScope()
    )

    /** The signed-in parent's families: which is on screen, and every co-parent (schema 45). */
    private val familyReader = SignedInFamiliesReader(userRepository)

    /**
     * The shared remote stream. **Every subscriber gets this one flow**, because each raw
     * collection would attach its own Firestore snapshot listener, and there are two collectors
     * by design: the mirror behind [getActiveModel] and the "your co-parent changed the
     * schedule" banner.
     *
     * `replayExpirationMillis = 0` is not optional. Without it the replay cache outlives the
     * last subscriber indefinitely, and the first collector on the *next* signed-in account
     * would be served the previous account's custody schedule for a frame — Room is not cleared
     * on sign-out either, so nothing else would catch it.
     *
     * `by lazy` so that merely constructing this singleton does not reach for the user
     * repository or start anything.
     *
     * Each value names the family it was read for, for [observeSharedOf].
     */
    private val tagged: Flow<FamilyDocument> by lazy {
        sharedUpstream().shareIn(
            scope,
            SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS, replayExpirationMillis = 0),
            replay = 1
        )
    }

    /** [tagged] without the family: the document of whichever family this device follows. */
    private val shared: Flow<SharedCustody?> by lazy { tagged.map { it.custody } }

    /**
     * The family on screen's custody pattern, as a Flow.
     *
     * Room decides what the caller sees; the remote branch is merged in purely for its side
     * effect of folding the shared document into Room (see [mirrorOnly]). Without that branch a
     * change made on the co-parent's phone would never reach this device's calendar.
     *
     * Follows the family on screen: a switch re-picks the row ([ActiveCustodyRow]), and a family
     * that has no schedule yet reads null rather than another family's.
     */
    fun getActiveModel(): Flow<CustodyModel?> {
        val local = activeRow().map { entity -> entity?.toDomainModel() }
        return merge(shared.mirrorOnly(), local)
    }

    /**
     * The pair's one-off day swaps, keyed by ISO date.
     *
     * Read from Room, for the same reason [getActiveModel] is: Room is the source of truth and
     * the calendar must paint the agreed days offline. The remote branch is merged in only for
     * its mirroring side effect.
     *
     * Deliberately not folded onto [CustodyModel]. That type is the *pattern* — what the grid
     * asks "whose day is this" — and a swap is a fact about the shared document, the same
     * separation [SharedCustody] already draws for `lastModifiedBy` and `createdAt`. Callers join
     * the two through `CustodyResolver`, which is the one place the precedence lives.
     */
    fun observeDayOverrides(): Flow<Map<String, DayOverride>> {
        val local = activeRow()
            .map { entity -> DayOverrideJson.decode(entity?.dayOverridesJson) }
            .distinctUntilChanged()
        return merge(shared.mirrorOnly(), local)
    }

    /** The family on screen's custody pattern, read once — the same row [getActiveModel] reads. */
    suspend fun getActiveModelSync(): CustodyModel? {
        return activeRowNow()?.toDomainModel()
    }

    /**
     * The schedule this parent holds on their own — the newest active row that names no family —
     * or null, and always null while they have two co-parents or more.
     *
     * What a pairing reconciles against the co-parent's document (`PairingViewModel.
     * reconcileCustody`), rather than [getActiveModelSync]: while unpaired that also falls back to
     * the schedule of a family that has ended, which must never move into the new family
     * (CLAUDE.md item 18), and whether the fallback applied would depend on whether the pairing had
     * reached Room yet — the same accept could carry it or not. With two co-parents a row naming no
     * family is nobody's.
     */
    suspend fun getUnsharedModelSync(): CustodyModel? {
        if (currentFamilies().coParents.size > 1) return null
        return ActiveCustodyRow.unshared(custodyModelDao.getActiveModelsSync())?.toDomainModel()
    }

    /**
     * The stored pattern and its accepted swaps, from Room alone and from one row, so the two
     * cannot come from different writes.
     *
     * For a caller outside every screen — the Today widget — which must not attach the Firestore
     * mirror [getActiveModel] and [observeDayOverrides] merge in: it draws what this device
     * holds, which is what Home draws a moment after it opens.
     */
    suspend fun storedCustody(): Pair<CustodyModel?, Map<String, DayOverride>> {
        val entity = activeRowNow()
        return entity?.toDomainModel() to DayOverrideJson.decode(entity?.dayOverridesJson)
    }

    /**
     * Publishes this device's active pattern to the pair, if the pair has none yet.
     *
     * A schedule saved before pairing never left the phone: [saveAndActivate] pushes only when
     * there is a pair, the mirror re-publishes only *over* a document that exists, and the
     * accepter's reconciliation writes only when the accepter has a pattern. The parent who set
     * the schedule first — the ordinary first parent — therefore paired and watched the other
     * phone's calendar stay blank. Runs on every sync, like `publishCachedRatioIfMissing`, and
     * writes only on a read that *proved* the document absent: an unreadable document
     * ([SharedCustodyRead.Unavailable]) is not an absent one, and publishing on its strength is
     * how a co-parent's schedule gets replaced by a device that merely failed to read it.
     */
    suspend fun publishLocalIfMissing() {
        val families = currentFamilies()
        val pair = families.pair ?: return
        // The family on screen's row, never another family's: a family with no document yet is
        // not given the schedule of the family the phone showed before (schema 45).
        val entity = activeRowNow(families) ?: return
        if (readShared(pair) != SharedCustodyRead.Absent) return
        Log.i(TAG, "Publishing the custody pattern this device held before the pair had one")
        pushToFirestore(entity.toDomainModel(), entity, families)
    }

    /**
     * Gets all custody models.
     */
    fun getAllModels(): Flow<List<CustodyModel>> {
        return custodyModelDao.getAllModels().map { entities ->
            entities.map { it.toDomainModel() }
        }
    }

    /**
     * The pair's shared pattern, or null when there is none — no partner, or no document yet.
     *
     * Retries with backoff rather than ending on the first failure. This project already ships
     * a defect of exactly the opposite shape: both mirror branches in `MessageRepositoryImpl`
     * end in `.catch { Log.w(...) }`, which *completes* the flow, so one denied read leaves the
     * chat running on Room alone for the rest of the process — the app looks entirely healthy
     * and receives nothing. It is in CLAUDE.md's known issues with the fix named. A new
     * listener has no excuse to repeat it.
     *
     * The window that makes this concrete: the rules gate the document on a *live* pairing, so
     * a listener attached seconds before the pairing callable finishes writing `partnerId` is
     * denied — once, transiently, exactly the case a terminal `catch` would turn permanent.
     */
    fun observeShared(): Flow<SharedCustody?> = shared

    /**
     * The shared document of [familyId], and null while that is not the family this device
     * follows, or has no document.
     *
     * For a screen that speaks for one family (phase 2 review): the requests inbox, Home's swap
     * pop-up and the mini months. [observeShared] follows the family on screen, but it replays the
     * previous family's document until the next one's listener answers. Asked by family id, the
     * replay never reaches the wrong family. ([observeDayOverrides] read Room's one active model,
     * which kept the previous family's swaps while the next family had no document; it reads the
     * family on screen's row since schema 45.)
     *
     * @param familyId `FamilyKey.of` the two parents, which is also the document's id.
     */
    fun observeSharedOf(familyId: String): Flow<SharedCustody?> =
        tagged.map { document -> document.custody.takeIf { document.documentId == familyId } }

    /**
     * The pair's shared pattern, read once.
     *
     * The one-shot counterpart of [observeShared], for the question a stream cannot answer:
     * what the co-parent's pattern is *at the moment* pairing is accepted.
     *
     * Answers with a [SharedCustodyRead] rather than a nullable [SharedCustody] because the
     * caller's next move differs by *why* there is nothing: [SharedCustodyRead.Absent] means the
     * pair has no document and one may safely be created, while [SharedCustodyRead.Unavailable]
     * means the question could not be answered and nothing may be published on the strength of
     * it. Collapsing the two is how a device that merely failed to read decides the co-parent has
     * no schedule and replaces it.
     *
     * Never propagates: callers run inside `viewModelScope.launch`, where an uncaught
     * `PERMISSION_DENIED` kills the process instead of failing the read. Cancellation is
     * rethrown — it is not a failure.
     */
    suspend fun readShared(): SharedCustodyRead =
        currentPair()?.let { readShared(it) } ?: SharedCustodyRead.Unavailable

    /** [readShared] for [pair]'s document, so a caller reads the family it already decided on. */
    private suspend fun readShared(pair: CustodyPair): SharedCustodyRead {
        return try {
            firestoreCustodyDataSource.getCustody(pair.documentId)
                ?.let { SharedCustodyRead.Found(it) }
                ?: SharedCustodyRead.Absent
        } catch (e: CancellationException) {
            throw e
        } catch (
            @Suppress("TooGenericExceptionCaught") e: Exception
        ) {
            Log.w(
                TAG,
                "Could not read the shared custody document. Reported as Unavailable, not " +
                    "as an absent document: a caller that mistook the two would publish its own " +
                    "pattern over a co-parent's that is simply unreadable right now.",
                e
            )
            SharedCustodyRead.Unavailable
        }
    }

    /**
     * Saves a new custody model, sets it as active, and mirrors it to the pair's document.
     *
     * Room first, always: it is the source of truth, and the remote write is best-effort on top
     * of it. The four `create*` helpers below all funnel through here, so this is the single
     * write-through point — a pattern that synced from one entry point and not another would be
     * worse than one that did not sync at all.
     */
    suspend fun saveAndActivate(model: CustodyModel) = saveAndActivate(model, currentFamilies())

    /** [saveAndActivate] for the [families] a caller has already decided on, read once. */
    private suspend fun saveAndActivate(model: CustodyModel, families: SignedInFamilies) {
        // The family on screen's schedule, stamped as such; no family while unpaired.
        val entity = model.toEntity().copy(isActive = true, familyId = families.pair?.documentId)
        custodyModelDao.deactivateFamily(entity.familyId)
        custodyModelDao.insertModel(entity)
        pushToFirestore(model, entity, families)
    }

    /**
     * Submits [model] the right way for the account's state (owner decision, Aug 2026
     * walkthrough, item 7): a pattern change must not land on the co-parent's calendar until
     * they agree to it.
     *
     * - **Paired, with a shared document already in place** → a *proposal*. The active pattern
     *   stays in force on both phones; the co-parent gets an Accept/Decline. The proposer sees
     *   their own proposal drawn as the calendar's preview overlay plus a "waiting" banner.
     * - **Unpaired, or no shared document yet** → [saveAndActivate], because there is nobody to
     *   ask and a schedule that waited forever for an approval that can never come is worse than
     *   one that simply applies. The very first schedule of a pair also lands this way: there is
     *   no agreed pattern to protect yet.
     * - **Paired, and the answer is not a proposal** → a refusal, with nothing written: the
     *   co-parent's own proposal is waiting ([PatternSubmission.COPARENT_PROPOSAL_WAITING]), or
     *   the document could not be read or the proposal not written ([PatternSubmission.NOT_SENT]).
     *   Never a local save instead — that pushes the pattern over the shared document.
     *
     * @param planCitation The parenting-plan answer the parent built [model] from (MON-21), as a
     *   `PlanCitationCodec` string, or null. It rides on a proposal only — a pattern that is
     *   simply activated has nobody to show its source to — and it is a citation, never an input:
     *   [model] is what the parent built, whatever the plan says.
     * @param answerBy The deadline the parent chose for the co-parent's answer (phase 3a), or null
     *   for none. Like the citation it rides on a proposal only: a proposal given one starts its
     *   clock, capped at the first day the schedule would change, and its push says when.
     * @return whether the pattern was activated, merely proposed, or refused, so the UI can say
     *   which.
     */
    suspend fun submitPattern(
        model: CustodyModel,
        planCitation: String? = null,
        answerBy: AnswerByPick? = null
    ): PatternSubmission = submit(model, planCitation, currentFamilies(), answerBy)

    /**
     * [submitPattern] for the [families] read once when the change began, so every step of it —
     * the read, the proposal, the activation — lands in one family even if the switcher moves on
     * while it runs (a push tap mid-save).
     */
    @Suppress("ReturnCount") // one road per answer: activated, refused, unsent, proposed
    private suspend fun submit(
        model: CustodyModel,
        planCitation: String?,
        families: SignedInFamilies,
        answerBy: AnswerByPick?
    ): PatternSubmission {
        val pair = families.pair
        if (pair == null) {
            saveAndActivate(model, families)
            return PatternSubmission.ACTIVATED
        }
        // Guarded: an unguarded read threw straight out of the caller's `viewModelScope.launch`.
        // And a failed read is not an absent document — only a read that *proved* there is none
        // may activate, because activating pushes this pattern over whatever the pair holds.
        val read = guarded("proposal read") {
            firestoreCustodyDataSource.getCustody(pair.documentId)
                ?.let { SharedCustodyRead.Found(it) }
                ?: SharedCustodyRead.Absent
        } ?: SharedCustodyRead.Unavailable
        val existing = when (read) {
            is SharedCustodyRead.Found -> read.custody
            SharedCustodyRead.Absent -> {
                saveAndActivate(model, families)
                return PatternSubmission.ACTIVATED
            }
            SharedCustodyRead.Unavailable -> return PatternSubmission.NOT_SENT
        }
        // From here on the pair has an agreed schedule, and nothing but a proposal may change it:
        // no refusal below falls back to `saveAndActivate`, which would overwrite the document —
        // the agreed pattern and the co-parent's pending proposal with it.
        val proposed = CustodyProposalTransition.propose(
            current = existing,
            model = model,
            repeatYearly = model.toEntity().repeatYearly,
            byUid = pair.myUid,
            atIso = nowIso(),
            planCitation = planCitation
        ).getOrElse { return PatternSubmission.COPARENT_PROPOSAL_WAITING }

        val written = guarded("propose") {
            firestoreCustodyDataSource.setCustody(pair.documentId, pair.participants, proposed)
        }
        if (written == null) return PatternSubmission.NOT_SENT
        // The clock starts once the proposal is written and before it is announced, so the push can
        // say when the answer is due — and only when the clock was written (ruling 8).
        //
        // **Not cancellable from here on.** The server has the proposal, and the clock waits for the
        // server for up to ten seconds (ruling 14), during which the schedule editor can be left: a
        // caller cancelled that way must not leave the proposal on the server with no clock, no card
        // and no push. Before the write is acknowledged nothing is shielded, and leaving cancels.
        withContext(NonCancellable) {
            val answerByMillis = startClock(proposalClock(pair, existing.model, proposed, answerBy))
            announceProposal(pair, ActivityKind.CUSTODY_PROPOSED, planCitation)
            notifyPartnerOfProposal(pair, "proposed", answerByMillis)
        }
        return PatternSubmission.PROPOSED
    }

    /**
     * Takes up the co-parent's pending proposal: it becomes the agreed pattern and is mirrored
     * into Room so this device's calendar follows immediately, without waiting for the snapshot
     * to round-trip. The proposer is told in their own family's thread; see [decideProposal].
     */
    suspend fun acceptProposal(): Result<Unit> = decideProposal { current, uid, now, nowMillis ->
        CustodyProposalTransition.accept(current, uid, now, nowMillis)
    }

    /**
     * Turns the co-parent's pending proposal down; the agreed pattern is untouched. The proposer is
     * told as for [acceptProposal].
     */
    suspend fun declineProposal(note: String? = null): Result<Unit> =
        decideProposal { current, uid, now, _ ->
            CustodyProposalTransition.decline(current, uid, now, note)
        }

    /** Withdraws this device's own pending proposal. Nothing is decided; the co-parent is told. */
    suspend fun withdrawProposal(): Result<Unit> = decideProposal { current, uid, _, _ ->
        CustodyProposalTransition.withdraw(current, uid)
    }

    /**
     * Withdraws this parent's proposal in [familyId] when it is still the one made at [proposedAt].
     *
     * The end of a proposal nobody answered (phase 3a, spec §4.4). A step of a clock acts on the
     * clock's family, never the one on screen (spec §5.6), so [familyId] is read against this
     * parent's families, once, and never through [currentPair]: it must still be a family they are
     * in. The read and the write are one transaction, so a proposal answered, withdrawn or replaced
     * a moment before is left as it is — an answer racing the end wins. Nothing is announced here;
     * the end posts its own card.
     *
     * @return Whether the proposal was withdrawn: false when the family has ended, when the proposal
     *   is no longer this one, and when the document could not be written, for the caller to retry.
     */
    suspend fun withdrawUnanswered(familyId: String, proposedAt: String): Boolean {
        val pair = currentFamilies().pairIn(familyId) ?: return false
        val written = guarded("unanswered withdrawal") {
            firestoreCustodyDataSource.transact(pair.documentId, pair.participants) { current ->
                withdrawnIfUnanswered(current, proposedAt, pair.myUid)
            }
        } ?: return false
        mirrorIntoRoom(pair.documentId, written)
        return true
    }

    /**
     * Reads the shared document, applies [transform], writes it back, mirrors the result into Room,
     * and — when the write recorded a decision — tells the proposer ([decisionCard]).
     *
     * The transform runs against whatever the document holds *now*, never a held snapshot: both
     * parents write one document, and a stale snapshot would drop whatever the other just did.
     *
     * **One family throughout.** The pair is read once, here, and the decision's card and push go to
     * the pair it was written for. The server's read and write take long enough for a push tap to
     * switch the family on screen, and a second read afterwards told the other family's co-parent of
     * this family's decision. Which decision it was is read off the written document, as a swap's
     * status is: the signature stays as it is (its `ReturnCount` finding is in the detekt baseline).
     *
     * **Not cancellable once the server has the decision**: the mirror, the card and the push run to
     * their end even if the screen that decided is left meanwhile, like a request's own tail.
     */
    private suspend fun decideProposal(
        transform: (SharedCustody, String, String, Long) -> Result<SharedCustody>
    ): Result<Unit> {
        val pair = currentPair()
            ?: return Result.failure(IllegalStateException("No co-parent to agree with"))
        val existing = firestoreCustodyDataSource.getCustody(pair.documentId)
            ?: return Result.failure(IllegalStateException("The pair has no shared schedule yet"))
        val next = transform(existing, pair.myUid, nowIso(), nowMillis())
            .getOrElse { return Result.failure(it) }
        // `setCustody` returns Unit, so `guarded` yields `Unit?` — null on failure, and that is
        // all this value can say. It is the success sentinel the two sibling call sites also
        // treat it as; the thing to mirror is `next`, the document that was just written.
        guarded("decide-proposal") {
            firestoreCustodyDataSource.setCustody(pair.documentId, pair.participants, next)
        } ?: return Result.failure(IllegalStateException("The decision could not be written"))
        withContext(NonCancellable) {
            mirrorIntoRoom(pair.documentId, next)
            decisionCard(existing, next)?.let { announceDecision(pair, it) }
        }
        return Result.success(Unit)
    }

    /**
     * The chat card for a proposal or a decision. [planCitation] rides only on the proposal's own
     * card (MON-21): the custody document drops it once the proposal is answered, and the chat is
     * the immutable place the export can still read it from.
     *
     * Posted to [pair]'s own thread, never the family on screen at the time: a proposal's clock
     * (phase 3a) and a decision's read and write each wait for the server first, and a family
     * switched meanwhile must not be shown another family's schedule.
     */
    private suspend fun announceProposal(pair: CustodyPair, kind: ActivityKind, planCitation: String? = null) {
        activityAnnouncer.announce(
            announcement = ActivityAnnouncement(
                kind = kind,
                entityType = ActivityEntityType.CUSTODY_PROPOSAL,
                entityId = pair.documentId,
                title = pair.documentId,
                whenIso = nowIso(),
                planCitation = planCitation
            ),
            partnerUid = pair.partnerUid()
        )
    }

    /** The card and the push for a decision on [pair]'s proposal, both to the proposer in that family. */
    private suspend fun announceDecision(pair: CustodyPair, kind: ActivityKind) {
        announceProposal(pair, kind)
        notifyPartnerOfProposal(pair, if (kind == ActivityKind.CUSTODY_ACCEPTED) "accepted" else "declined")
    }

    /**
     * Best-effort push to the parent who did not perform this proposal write — same mechanism
     * as [notifyPartnerOfSwap], and like it, carries no text of its own.
     *
     * @param answerByMillis The deadline of the proposal's clock, when one was written (phase 3a).
     */
    private suspend fun notifyPartnerOfProposal(pair: CustodyPair, action: String, answerByMillis: Long? = null) {
        val partnerUid = pair.participants.firstOrNull { it != pair.myUid } ?: return
        fcmService.queueNotificationForUser(
            targetUserId = partnerUid,
            notificationData = mapOf(
                // The type is the whole payload: this notification names nothing a person
                // typed, so the receiving device has everything it needs to write the sentence
                // in the reader's language (SEC-3).
                PushPayload.TYPE to "custody_proposal_$action"
            ).withAnswerBy(answerByMillis)
        )
    }

    /**
     * Rewrites the active pattern in place: Room only, and without re-dating it.
     *
     * For a *re-expression* of an arrangement rather than a change to one — complementing a
     * pattern after pairing moved this device to the other slot, where `momDayIndices` would
     * otherwise silently start describing the co-parent's days. Nothing about who has the child
     * on which date changes; only which slot the same schedule is written from.
     *
     * Both departures from [saveAndActivate] are the point:
     *
     * - **The dates are kept.** [toEntity]'s default stamps `lastModifiedAtMillis` with now, which
     *   would make this device the unconditional winner of every later comparison in
     *   [mirrorIntoRoom] — so the re-slot alone would cause this pattern to be republished over
     *   the co-parent's document, with nobody having chosen anything.
     * - **Nothing is pushed.** The shared document may hold a pattern this device has not been
     *   allowed to see, or one the user is at that moment being asked to choose between. Writing
     *   locally leaves that decision where it belongs and lets the ordinary last-write-wins
     *   mirror settle the rest.
     *
     * The equality guard is [mirrorIntoRoom]'s, for the same reason: an identical re-insert would
     * tick Room's invalidation tracker and re-emit to every observer for no change at all.
     */
    suspend fun saveReslotted(model: CustodyModel) {
        val existing = custodyModelDao.getModelById(model.id)
        val entity = model.toEntity(
            createdAt = existing?.createdAt ?: nowIso(),
            lastModifiedAtMillis = existing?.lastModifiedAtMillis ?: nowMillis()
        ).copy(isActive = true, repeatYearly = existing?.repeatYearly ?: true, familyId = existing?.familyId)
        if (entity == existing) return
        custodyModelDao.deactivateFamily(entity.familyId)
        custodyModelDao.insertModel(entity)
    }

    /**
     * Keeps a deactivated copy of a pattern that is about to be replaced by one sharing its id.
     *
     * Room's insert REPLACEs on the primary key, so when the accepter's pattern and the shared
     * document's carry the *same* id — a re-pair whose document was created from this device's
     * own model — [saveAndActivate] would delete the rejected one rather than deactivate it.
     * The copy is stored under a fresh id, inactive, keeping the original's dates: nobody's
     * schedule disappears because of a choice made in one moment.
     *
     * Inactive rows are never pushed, so the fresh id stays local and cannot confuse the pair's
     * document.
     */
    suspend fun archiveRejected(model: CustodyModel) {
        val original = custodyModelDao.getModelById(model.id)
        val entity = model.toEntity(
            createdAt = original?.createdAt ?: nowIso(),
            lastModifiedAtMillis = original?.lastModifiedAtMillis ?: nowMillis()
        ).copy(id = UUID.randomUUID().toString(), isActive = false, familyId = original?.familyId)
        custodyModelDao.insertModel(entity)
    }

    /**
     * Creates and saves a week-on-week-off custody model.
     *
     * @param startDate The anchor date for the pattern
     * @param momFirst If true, mom has the first week; if false, dad has the first week
     * @param planCitation The parenting-plan answer this was built from (MON-21); see [submitPattern]
     * @param answerBy The deadline for the co-parent's answer, when it is a proposal; see [submitPattern]
     */
    suspend fun createWeekOnWeekOff(
        startDate: LocalDate,
        momFirst: Boolean = true,
        contactWindows: List<ContactWindow> = emptyList(),
        planCitation: String? = null,
        answerBy: AnswerByPick? = null
    ): PatternSubmission {
        val model = CustodyModel.weekOnWeekOff(
            id = UUID.randomUUID().toString(),
            startDate = startDate,
            momFirst = momFirst
        )
        return submitNew(model.withWindows(contactWindows), planCitation, answerBy)
    }

    /**
     * Creates and saves an every-other-weekend model — `výhradní péče se stykem` (MON-6).
     *
     * @param startDate The anchor date, expected to be the Monday the fortnight opens on
     * @param momIsResident True when slot 1 is the parent the child lives with
     * @param midweek The midweek contact day, or null for alternate weekends only
     * @param contactWindows Contact afternoons on top of the days (MON-6b)
     * @param planCitation The parenting-plan answer this was built from (MON-21); see [submitPattern]
     * @param answerBy The deadline for the co-parent's answer, when it is a proposal; see [submitPattern]
     */
    @Suppress("LongParameterList") // the pattern's fields, then how it is sent
    suspend fun createEveryOtherWeekend(
        startDate: LocalDate,
        momIsResident: Boolean = true,
        midweek: MidweekContact? = null,
        contactWindows: List<ContactWindow> = emptyList(),
        planCitation: String? = null,
        answerBy: AnswerByPick? = null
    ): PatternSubmission {
        val model = CustodyModel.everyOtherWeekend(
            id = UUID.randomUUID().toString(),
            startDate = startDate,
            momIsResident = momIsResident,
            midweek = midweek
        )
        return submitNew(model.withWindows(contactWindows), planCitation, answerBy)
    }

    /**
     * Creates and saves a 2-2-3 custody model.
     */
    suspend fun createTwoTwoThree(
        startDate: LocalDate,
        momStartsFirst: Boolean = true,
        contactWindows: List<ContactWindow> = emptyList(),
        planCitation: String? = null,
        answerBy: AnswerByPick? = null
    ): PatternSubmission {
        val model = CustodyModel.twoTwoThree(
            id = UUID.randomUUID().toString(),
            startDate = startDate,
            momStartsFirst = momStartsFirst
        )
        return submitNew(model.withWindows(contactWindows), planCitation, answerBy)
    }

    /**
     * Creates and saves a 3-4-4-3 custody model.
     */
    suspend fun createThreeFourFourThree(
        startDate: LocalDate,
        momStartsFirst: Boolean = true,
        contactWindows: List<ContactWindow> = emptyList(),
        planCitation: String? = null,
        answerBy: AnswerByPick? = null
    ): PatternSubmission {
        val model = CustodyModel.threeFourFourThree(
            id = UUID.randomUUID().toString(),
            startDate = startDate,
            momStartsFirst = momStartsFirst
        )
        return submitNew(model.withWindows(contactWindows), planCitation, answerBy)
    }

    /**
     * Creates and saves a custom custody model.
     */
    @Suppress("LongParameterList") // the pattern's fields, then how it is sent
    suspend fun createCustom(
        startDate: LocalDate,
        patternDays: Int,
        momDayIndices: Set<Int>,
        contactWindows: List<ContactWindow> = emptyList(),
        planCitation: String? = null,
        answerBy: AnswerByPick? = null
    ): PatternSubmission {
        val model = CustodyModel.custom(
            id = UUID.randomUUID().toString(),
            startDate = startDate,
            patternDays = patternDays,
            momDayIndices = momDayIndices
        )
        return submitNew(model.withWindows(contactWindows), planCitation, answerBy)
    }

    /**
     * [this] with [windows] attached, minus any that fall outside the cycle — a window left over
     * from a longer pattern the parent has just shortened would never match a date, and storing
     * it would only make the two phones' documents differ over something nobody can see.
     */
    private fun CustodyModel.withWindows(windows: List<ContactWindow>): CustodyModel =
        copy(contactWindows = ContactWindowCodec.canonical(windows.filter { it.dayIndex < patternDays }))

    /** A fresh pattern from the editor, with the family's layers, for the family on screen now. */
    private suspend fun submitNew(
        model: CustodyModel,
        planCitation: String?,
        answerBy: AnswerByPick?
    ): PatternSubmission {
        val families = currentFamilies()
        return submit(withActiveLayers(model, families), planCitation, families, answerBy)
    }

    /**
     * [model] carrying the active pattern's seasonal layers (MON-14) and each child's own schedule
     * (FAM-4).
     *
     * The pattern editor builds a fresh model from the base form, which knows nothing of layers or
     * of the children who follow a schedule of their own; without this, saving the base pattern
     * would propose deleting every layer and every override the pair agreed — a change nobody
     * asked for, riding on one somebody did. Unreadable entries travel too.
     */
    private suspend fun withActiveLayers(model: CustodyModel, families: SignedInFamilies): CustodyModel {
        val active = activeRowNow(families)?.toDomainModel() ?: return model
        return model.copy(
            seasonalLayers = active.seasonalLayers,
            unreadableLayers = active.unreadableLayers,
            childOverrides = active.childOverrides,
            unreadableChildOverrides = active.unreadableChildOverrides
        )
    }

    /**
     * Gives [childId] a schedule of their own, replaces the one they have, or — with a null
     * [override] — sends them back to the family schedule (FAM-4).
     *
     * A pattern change like any other, through [submitPattern]: applied directly on an unpaired
     * account or before the pair shares a schedule, and otherwise a **proposal** the co-parent
     * accepts or declines — never written onto their calendar unasked. The base pattern, its
     * layers, the other children's overrides and any entry this build cannot read are carried
     * unchanged.
     *
     * @param answerBy The deadline for the co-parent's answer, when it is a proposal; see [submitPattern]
     * @return How the change landed, or null when there is no family schedule to override.
     */
    suspend fun submitChildOverride(
        childId: String,
        override: ChildScheduleOverride?,
        answerBy: AnswerByPick? = null
    ): PatternSubmission? {
        val families = currentFamilies()
        val active = activeRowNow(families)?.toDomainModel() ?: return null
        val others = active.childOverrides.filterNot { it.childId == childId }
        return submit(active.copy(childOverrides = others + listOfNotNull(override)), null, families, answerBy)
    }

    /**
     * Submits a new set of seasonal layers over the active base pattern (MON-14).
     *
     * Through [submitPattern], so it takes the same road a base-pattern change does: applied
     * directly on an unpaired account or before the pair has a shared schedule, and otherwise a
     * **proposal** the co-parent accepts or declines — never written onto their calendar unasked.
     * The base pattern and any layer entries this build cannot read are carried unchanged.
     *
     * @param planCitation The parenting-plan answer the layer was built from (MON-21), or null;
     *   see [submitPattern].
     * @param answerBy The deadline for the co-parent's answer, when it is a proposal; see [submitPattern]
     * @return How the change landed, or null when there is no base pattern to layer on.
     */
    suspend fun submitSeasonalLayers(
        layers: List<SeasonalLayer>,
        planCitation: String? = null,
        answerBy: AnswerByPick? = null
    ): PatternSubmission? {
        val families = currentFamilies()
        val active = activeRowNow(families)?.toDomainModel() ?: return null
        return submit(active.copy(seasonalLayers = layers), planCitation, families, answerBy)
    }

    /**
     * Deletes a custody model.
     */
    suspend fun deleteModel(id: String) {
        custodyModelDao.deleteModelById(id)
    }

    // ---- the shared document ----------------------------------------------

    /**
     * The unshared upstream behind [observeShared]: the pair's document, mirrored into Room on
     * the way past, the whole chain retried with backoff.
     *
     * The mirror sits here rather than on a separate collector so it runs exactly once no matter
     * how many screens observe the stream, and it is the same mirror-and-merge shape
     * `MessageRepositoryImpl` uses — minus the terminal `catch` that shape gets wrong.
     *
     * **[retryWhen] wraps the whole chain, not just the snapshot listener.** Applied inside
     * [flatMapLatest] it covered only the listener, leaving two uncovered sources of failure on
     * the same coroutine: [observePair], which collects a Room flow, and the mirror below, which
     * performs two DAO writes. An exception from either escaped into the sharing coroutine,
     * where it would kill the process — while every [getActiveModel] collector saw nothing at
     * all, because a shared flow never delivers an upstream failure to its subscribers. Both
     * failure shapes this class exists to prevent, on the one path neither guard covered.
     * Retrying from the top re-derives the pair and re-attaches the listener, which is the only
     * safe reset when the failure could have come from any stage.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    private fun sharedUpstream(): Flow<FamilyDocument> =
        observePair()
            .flatMapLatest { pair ->
                if (pair == null) {
                    flowOf(FamilyDocument(documentId = null, custody = null))
                } else {
                    firestoreCustodyDataSource.observeCustody(pair.documentId)
                        .map { custody -> FamilyDocument(pair.documentId, custody) }
                }
            }
            .onEach { document -> document.documentId?.let { mirrorIntoRoom(it, document.custody) } }
            .retryWhen { cause, attempt ->
                Log.w(
                    TAG,
                    "Shared custody stream failed (attempt $attempt), retrying — this covers " +
                        "the pair lookup, the custody_models listener and the Room mirror. A " +
                        "PERMISSION_DENIED here can simply mean the pairing write has not " +
                        "landed yet: the rules require a live pairing to read the document.",
                    cause
                )
                delay(RETRY_BASE_MS shl attempt.coerceAtMost(MAX_BACKOFF_SHIFT).toInt())
                true
            }

    /**
     * Folds the shared document into Room, under the id it arrived with.
     *
     * Reusing the writer's id is what makes the two devices converge on one row instead of
     * accumulating a copy per sync. The document is by definition the pair's current
     * arrangement, so the mirrored row is the active one.
     *
     * The equality guard is not an optimisation: Firestore echoes every write this device makes
     * straight back, and re-inserting an identical row would tick Room's invalidation tracker,
     * re-emit to every observer, and do it again on the next echo.
     *
     * **The staleness guard is what stops this function from undoing the user's own save.**
     * [saveAndActivate] deactivates the family's rows before inserting the new one, so a
     * previously mirrored row is left `isActive = false`; if the remote write behind it was then
     * swallowed by [guarded], the document still holds the old pattern. The equality guard alone
     * does not fire on the replay — the stored row differs from the computed one precisely in `isActive`
     * — so the mirror used to deactivate the model the user had just chosen and re-activate the
     * stale one, silently, with no error anywhere: `CustodySetupViewModel` only catches what
     * [saveAndActivate] rethrows, and it rethrows nothing. Comparing `lastModifiedAtMillis` is the
     * same last-write-wins rule the shared document already runs on, applied in the one
     * direction it was missing.
     */
    private suspend fun mirrorIntoRoom(familyId: String, remote: SharedCustody?) {
        if (remote == null) return
        val families = currentFamilies()
        // A row under the same id is this family's only if it says so, or names no family while
        // this is the parent's only one: the old republish could copy one family's model, id and
        // all, into the other's document, and the keys an older build leaves out must never be
        // filled in from the other family's row.
        val existing = custodyModelDao.getModelById(remote.model.id)
            ?.takeIf { ActiveCustodyRow.belongsTo(it, familyId, families.soleFamily) }
        val entity = remote.model.toEntity(
            createdAt = remote.createdAt.ifBlank { existing?.createdAt ?: nowIso() },
            lastModifiedAtMillis = remote.lastModifiedAtMillis
                .takeIf { it != CustodyTimestamp.UNDATED }
                ?: existing?.lastModifiedAtMillis ?: nowMillis(),
            dayOverrides = remote.dayOverrides
        ).copy(
            isActive = true,
            repeatYearly = remote.repeatYearly,
            // A document with no `contactWindows` key was written by a build that predates
            // MON-6b, which rewrites the whole document without it on every save — including an
            // ordinary swap. That is not a removal, so this row keeps what it had. A removal this
            // build makes is an explicit empty list, never a missing key.
            contactWindowsJson = if (remote.contactWindowsWire == null) {
                existing?.contactWindowsJson
            } else {
                ContactWindowJson.encode(remote.model.contactWindows)
            },
            // The same rule for the seasonal layers (MON-14): a missing key is an older build's
            // write, never "no layers".
            seasonalLayersJson = if (remote.seasonalLayersWire == null) {
                existing?.seasonalLayersJson
            } else {
                SeasonalLayerJson.encode(remote.model.seasonalLayers, remote.model.unreadableLayers)
            },
            // And for each child's own schedule (FAM-4).
            childOverridesJson = ChildOverrideJson.mirrored(remote, existing?.childOverridesJson),
            // The family whose document this is — not the family on screen, which a switch may
            // already have moved on from by the time this document arrives.
            familyId = familyId
        )
        if (entity == existing) return

        // Compared with this family's row only. Another family's row is often newer, and the
        // winner is re-pushed over the loser: that is how one family's schedule overwrote the
        // other family's document.
        val localActive = ActiveCustodyRow.pick(
            custodyModelDao.getActiveModelsSync(),
            CustodyScope(familyId, families.soleFamily)
        )
        if (localActive != null && isNewer(localActive.lastModifiedAtMillis, entity.lastModifiedAtMillis)) {
            republish(localActive, families)
            return
        }

        custodyModelDao.deactivateFamily(familyId)
        custodyModelDao.insertModel(entity)
    }

    /**
     * Re-sends a local model that the document is older than.
     *
     * Not strictly required to stop the revert — refusing to mirror would do that on its own —
     * but refusing alone leaves the pair permanently disagreeing, with the co-parent's phone
     * (and Task 10's banner) reading a document that is this device's *own* lost write. Since
     * the mirror has just proved the listener is alive, the write that failed is worth one more
     * attempt: it turns a swallowed write into a recovered one at the cost of a guarded call
     * that is already written.
     *
     * This cannot loop. The re-push echoes back carrying the same instant the local row
     * holds, so the next pass finds neither side newer and mirrors normally.
     */
    private suspend fun republish(local: CustodyModelEntity, families: SignedInFamilies) {
        pushToFirestore(local.toDomainModel(), local, families)
    }

    /**
     * Whether the instant [candidate] is strictly later than [reference].
     *
     * Both are epoch millis since schema 29, so two parents in different zones order their
     * writes by real time. They used to be naive local date-times, which is why the wrong
     * side could win this comparison — and the winner is not merely kept, it is re-pushed over
     * the loser. That was SEC-4; see [CustodyTimestamp].
     *
     * [CustodyTimestamp.UNDATED] is zero, so a row or document that cannot be dated loses every
     * comparison rather than winning one — the same direction the old string version degraded
     * in, where an unparseable value answered "not newer".
     */
    private fun isNewer(candidate: Long, reference: Long): Boolean = candidate > reference

    /**
     * Pushes the just-saved model to the pair's document, guarded.
     *
     * Guarded because an uncaught `PERMISSION_DENIED` from a suspend call inside
     * `viewModelScope.launch` crashes the app rather than failing the sync — three ViewModels
     * call [saveAndActivate] from exactly there. `addBudget`/`deleteBudget` gained the same
     * guard for the same reason.
     *
     * `createdAt` comes from the existing document when there is one, so an update does not
     * re-date the pair's arrangement, and `lastModifiedBy` is the signed-in uid on every write:
     * it is what tells the co-parent's device that the change was not its own echo.
     *
     * **The document is the row's own family's** ([SignedInFamilies.pairFor]), never simply the
     * family on screen: a mirror pass can republish a row a moment after the switcher moves on.
     */
    private suspend fun pushToFirestore(model: CustodyModel, entity: CustodyModelEntity, families: SignedInFamilies) {
        val pair = families.pairFor(entity) ?: return
        guarded("write") {
            val existing = firestoreCustodyDataSource.getCustody(pair.documentId)
            val existingCreatedAt = existing?.createdAt?.takeIf { it.isNotBlank() }
            firestoreCustodyDataSource.setCustody(
                documentId = pair.documentId,
                participants = pair.participants,
                custody = SharedCustody(
                    model = model,
                    lastModifiedBy = pair.myUid,
                    lastModifiedAtMillis = entity.lastModifiedAtMillis,
                    createdAt = existingCreatedAt ?: entity.createdAt,
                    repeatYearly = entity.repeatYearly,
                    // Carried over, not dropped. `setCustody` replaces the whole document, so a
                    // pattern save that rebuilt this object from scratch would silently delete
                    // every agreed swap — including from `republish`, which runs with no user
                    // action behind it at all, during ordinary mirroring.
                    dayOverrides = existing?.dayOverrides.orEmpty(),
                    lastModifiedKind = CustodyWriteKind.PATTERN,
                    // Always the key, as `[]` for none: this is a pattern write, the one kind
                    // `firestore.rules` lets replace the list, and an explicit empty list is how
                    // a removal is told apart from an older build that never wrote the key.
                    contactWindowsWire = ContactWindowCodec.encodeAll(model.contactWindows),
                    // The same for the seasonal layers (MON-14), unreadable entries included.
                    seasonalLayersWire = model.seasonalLayersWire(),
                    // And each child's own schedule (FAM-4), `[]` for none.
                    childOverridesWire = model.childOverridesWire()
                )
            )
        }
    }

    /**
     * Applies [transform] to the pair's one-off day swaps and writes the result back.
     *
     * The whole document is re-sent — `setCustody` is a `set()` — so everything else it holds is
     * read first and carried across unchanged. Two fields are handled deliberately:
     *
     * - **`lastModifiedAtMillis` keeps the pattern's value.** It is what [isNewer] compares to decide
     *   which phone's document survives, and the winner is then *re-pushed over the loser*.
     *   Re-dating for a swap would make this device win every future comparison.
     * - **`lastModifiedBy` becomes this device's uid**, because `firestore.rules` requires every
     *   update to stamp it with the caller. [CustodyWriteKind.SWAP] is what stops the co-parent's
     *   phone reading that stamp as a pattern change; see `CustodyChangeAnnouncement`.
     *
     * Refuses rather than silently no-ops when there is no pair or no shared document: a swap
     * needs somebody to accept it, and an unpaired parent moving their own day is an edit the
     * custody editor already does.
     *
     * @param date The ISO date this write touches. Named rather than derived because
     *   `firestore.rules` requires the document to carry it: Rules cannot iterate a map, so the
     *   write names its one date and the rule checks the diff affects only that key.
     * @param transform The transition to apply, from [DayOverrideTransition].
     * @param answerBy The deadline the offering parent chose (phase 3a), or null for none. An offer
     *   given one starts its clock once it is written, and its push says when; an answer starts none.
     * @return The applied map, or the transition's own failure.
     */
    suspend fun applyDayOverrides(
        date: String,
        transform: (Map<String, DayOverride>) -> Result<Map<String, DayOverride>>,
        answerBy: AnswerByPick? = null
    ): Result<Map<String, DayOverride>> {
        val pair = currentPair()
            ?: return Result.failure(IllegalStateException("No co-parent to agree a swap with"))
        val next = writeSwap(pair, date, transform).getOrElse { return Result.failure(it) }.overrides

        // Package C left this gap on purpose rather than adding a bespoke card the way the
        // change-request path once had: there must be exactly one way a change reaches the
        // thread, and this is it. The status the transition produced *is* the announcement —
        // offered, agreed or turned down — so nothing here has to be told which it was.
        next[date]?.let { entry -> announceSwap(pair, entry, listOf(date), answerBy) }
        return Result.success(next)
    }

    /**
     * Applies one transition across a run of days, and announces it **once**.
     *
     * Written one date at a time because `firestore.rules` validates a `dayOverrides` diff by
     * naming its single date — Rules cannot iterate a map — so a five-day offer is five writes to
     * the same document. What must not be five times is what the co-parent receives: before this,
     * a week of swapped days produced five chat cards, five inbox rows, five stacked system
     * notifications and five sequential modal dialogs, each asking about one day of the same
     * agreement.
     *
     * **Not atomic, and the caller is told so.** The loop stops at the first refused write and
     * returns a failure carrying how many days did land; the days already written stay written,
     * as pending offers the co-parent can still answer. The alternative — an all-or-nothing write
     * — needs the rule to validate a multi-date diff, which Rules can only do by unrolling a
     * fixed number of per-date checks, and an off-by-one there silently admits an unvalidated key.
     *
     * Refuses, with nothing written, a run with no date or no co-parent; stops at the first write
     * that fails, reporting [PartialSwapRun] when part of the run had landed; and refuses a run that
     * wrote nothing. An offer given a deadline starts one clock, for the days that landed, before
     * the one announcement. A caller cancelled part-way stops the run there like a failed write,
     * and the days that landed are announced all the same ([announceSwap] is not cancellable).
     *
     * @param dates The ISO dates to apply, in the order they should be written.
     * @param transform The transition for one date, from [DayOverrideTransition].
     * @param answerBy The deadline the offering parent chose (phase 3a), or null for none; see
     *   [applyDayOverrides].
     * @return The map after the last successful write.
     */
    @Suppress("ReturnCount") // one early refusal per reason; see the KDoc
    suspend fun applyDayOverridesForDates(
        dates: List<String>,
        transform: (Map<String, DayOverride>, String) -> Result<Map<String, DayOverride>>,
        answerBy: AnswerByPick? = null
    ): Result<Map<String, DayOverride>> {
        if (dates.isEmpty()) {
            return Result.failure(IllegalArgumentException("A swap needs at least one day"))
        }
        val pair = currentPair()
            ?: return Result.failure(IllegalStateException("No co-parent to agree a swap with"))

        var latest: Map<String, DayOverride>? = null
        val changed = mutableListOf<String>()
        for (date in dates) {
            val result = try {
                writeSwap(pair, date) { current -> transform(current, date) }
            } catch (e: CancellationException) {
                // The caller left mid-run: this day's write ends with it and no later day is
                // written, but the days already written are on the server and are told of.
                announceRun(pair, latest, changed, answerBy)
                throw e
            }
            val applied = result.getOrElse { cause ->
                // Announce whatever *did* land before reporting the failure. Returning straight
                // out left the days already written with no chat card and no push at all: the
                // co-parent's schedule had moved and nothing had told them.
                announceRun(pair, latest, changed, answerBy)
                return if (changed.isEmpty()) {
                    Result.failure(cause)
                } else {
                    Result.failure(PartialSwapRun(changed.size, dates.size, cause))
                }
            }
            latest = applied.overrides
            if (applied.changed) changed += date
        }

        val map = latest ?: return Result.failure(IllegalStateException("Nothing was written"))
        announceRun(pair, map, changed, answerBy)
        return Result.success(map)
    }

    /**
     * One chat card and one push for the days a run actually moved.
     *
     * Counts [changed] rather than the dates asked for: a group being finished skips the days
     * already answered, and telling the co-parent "5 days" when two moved is the same kind of
     * wrong as telling them nothing. The status of the first changed day speaks for the run —
     * `decideGroup` and `offerAll` both give every day of a group the same status. So does its
     * clock (phase 3a): one, for the days that landed.
     */
    private suspend fun announceRun(
        pair: CustodyPair,
        map: Map<String, DayOverride>?,
        changed: List<String>,
        answerBy: AnswerByPick?
    ) {
        val first = changed.firstOrNull() ?: return
        map?.get(first)?.let { entry -> announceSwap(pair, entry, changed, answerBy) }
    }

    /**
     * The document write behind one day of a swap, with no announcement.
     *
     * Split out so a group can write N days and announce once; [applyDayOverrides] is this plus
     * a single-day announcement.
     */
    private suspend fun writeSwap(
        pair: CustodyPair,
        date: String,
        transform: (Map<String, DayOverride>) -> Result<Map<String, DayOverride>>
    ): Result<SwapWrite> {
        // Guarded like the write below it. An unguarded read threw straight out of the caller's
        // `viewModelScope.launch` — neither ViewModel's `.onFailure` can catch a throw — and an
        // uncaught exception there terminates the process rather than showing a refusal.
        val existing = guarded("swap read") {
            firestoreCustodyDataSource.getCustody(pair.documentId)
        } ?: return Result.failure(IllegalStateException("The pair has no shared schedule yet"))

        val next = transform(existing.dayOverrides).getOrElse { return Result.failure(it) }
        if (next == existing.dayOverrides) {
            // The transform left this day alone — an already-answered day of a group being
            // finished. Writing it anyway would cost a document write and re-stamp
            // `lastModifiedBy`, which `CustodyChangeAnnouncement` reads to decide whether to
            // tell the other parent their schedule moved.
            return Result.success(SwapWrite(existing.dayOverrides, changed = false))
        }
        val written = guarded("swap") {
            firestoreCustodyDataSource.setCustody(
                documentId = pair.documentId,
                participants = pair.participants,
                custody = existing.copy(
                    lastModifiedBy = pair.myUid,
                    dayOverrides = next,
                    lastSwapDate = date,
                    lastModifiedKind = CustodyWriteKind.SWAP
                )
            )
        }
        return if (written == null) {
            Result.failure(IllegalStateException("The swap could not be written"))
        } else {
            Result.success(SwapWrite(next, changed = true))
        }
    }

    /**
     * One day's write, and whether it moved anything.
     *
     * @property overrides The map as it now stands.
     * @property changed False when the transform left the day exactly as it found it, so the
     *   caller can count and announce the days that really landed rather than the days it tried.
     */
    private data class SwapWrite(
        val overrides: Map<String, DayOverride>,
        val changed: Boolean
    )

    /**
     * What follows a swap once the server has it: an offer's clock (phase 3a), then one chat card
     * and one push, however many days it covers.
     *
     * **To [pair]'s own thread**: never the family on screen at the time, because the clock waits
     * for the server first and a family switched meanwhile must not be shown another family's days.
     * **And not cancellable**: the clock waits for up to ten seconds (ruling 14) while the screen
     * that sent the swap may be left, and the swap is on the server either way — a cancelled caller
     * must not cost the co-parent the card and the push, nor the clock their answer is timed by.
     *
     * @param entry The first day's entry, whose status the whole swap shares: offered, agreed or
     *   turned down.
     * @param dates The days the swap moved; the card and the push name the first. One picks the
     *   single-day wording on the other phone; more picks the grouped one, which is the whole point of
     *   the group.
     * @param answerBy The deadline the offering parent chose, or null for none; see [swapClock].
     */
    private suspend fun announceSwap(
        pair: CustodyPair,
        entry: DayOverride,
        dates: List<String>,
        answerBy: AnswerByPick?
    ) = withContext(NonCancellable) {
        val answerByMillis = startClock(swapClock(pair, entry, dates, answerBy))
        activityAnnouncer.announce(
            announcement = swapAnnouncement(dates.first(), entry.status),
            partnerUid = pair.partnerUid()
        )
        notifyPartnerOfSwap(pair, dates.first(), entry.status, dates.size, answerByMillis)
    }

    /**
     * A best-effort push to the parent who did **not** perform this write, mirroring
     * `ChangeRequestRepositoryImpl.notifyCounterparty`: the owner's walkthrough asked for a
     * pop-up on the other phone (items 4/13), and the Cloud Function behind `notification_queue`
     * already fans these out. Delivery is a nicety — the document write above is the record.
     *
     * The payload carries no text at all ([swapPush]): the receiving device writes the sentence
     * from its own resources, in the reader's language (SEC-3). It used to be composed here, in
     * English, which is what made a push able to claim to be anything.
     *
     * An offer whose clock was written also carries its deadline ([PushPayload.ANSWER_BY]), so the
     * first notification can say when the answer is due (phase 3a); an older build ignores the key.
     */
    private suspend fun notifyPartnerOfSwap(
        pair: CustodyPair,
        date: String,
        status: DayOverrideStatus,
        dayCount: Int,
        answerByMillis: Long?
    ) {
        val partnerUid = pair.participants.firstOrNull { it != pair.myUid } ?: return
        fcmService.queueNotificationForUser(partnerUid, swapPush(date, status, dayCount).withAnswerBy(answerByMillis))
    }

    /**
     * Writes the clock [start] describes (phase 3a, spec §5.1): after the request's own write and
     * before its announcement, so the first push can say when the answer is due.
     *
     * @return The deadline the clock was written with, or null when there is none — none was asked
     *   for, or the write was refused — and the push then says nothing of one (ruling 8).
     */
    private suspend fun startClock(start: RequestStart?): Long? =
        start?.let { requestDeadlines.start(it)?.answerByMillis }

    /**
     * The pair this device currently belongs to — the family on screen — or null when nobody is
     * signed in or nobody is paired.
     */
    private suspend fun currentPair(): CustodyPair? = familyReader.current().pair

    /** [currentPair] together with every co-parent of the signed-in parent, read once. */
    private suspend fun currentFamilies(): SignedInFamilies = familyReader.current()

    /**
     * The pair as a stream, so the listener follows the account and the family on screen rather
     * than whichever pairing happened to exist when the first screen opened.
     */
    private fun observePair(): Flow<CustodyPair?> = familyReader.observe().map { it.pair }.distinctUntilChanged()

    /**
     * Runs a remote call, degrading a failure to null with a log.
     *
     * Room already holds the truth by the time this runs, so a refused or unreachable call
     * degrades to "local for now" rather than to an exception in the caller's coroutine.
     * Cancellation is rethrown — it is not a failure.
     */
    private suspend fun <T> guarded(operation: String, block: suspend () -> T): T? = try {
        block()
    } catch (e: CancellationException) {
        throw e
    } catch (
        @Suppress("TooGenericExceptionCaught") e: Exception
    ) {
        Log.w(
            TAG,
            "Custody $operation failed for the shared custody document. Room keeps the local " +
                "copy, which the mirror will not overwrite with the older document, and " +
                "re-sends on the next snapshot.",
            e
        )
        null
    }

    // ---- helpers ----------------------------------------------------------

    /**
     * The family on screen's active row ([ActiveCustodyRow.pick]), re-picked when the family on
     * screen, the set of co-parents or the rows change.
     */
    private fun activeRow(): Flow<CustodyModelEntity?> = combine(
        familyReader.observe().map { it.scope }.distinctUntilChanged(),
        custodyModelDao.observeActiveModels()
    ) { scope, rows -> ActiveCustodyRow.pick(rows, scope) }.distinctUntilChanged()

    /** [activeRow], read once, for [families] when the caller has already read it. */
    private suspend fun activeRowNow(families: SignedInFamilies? = null): CustodyModelEntity? =
        ActiveCustodyRow.pick(custodyModelDao.getActiveModelsSync(), (families ?: currentFamilies()).scope)

    /**
     * Turns the mirroring flow into one that never emits, so exactly one of the two branches
     * merged by [getActiveModel] — Room — decides what the caller sees.
     */
    private fun <T> Flow<*>.mirrorOnly(): Flow<T> = transform { }

    private fun nowIso(): String =
        LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)

    /** Now as an instant. What orders two phones' writes; see [CustodyTimestamp]. */
    private fun nowMillis(): Long = System.currentTimeMillis()

    /**
     * The shared document as it arrived, with the family it was read for.
     *
     * @property documentId The family's id, or null while this device follows no pair.
     * @property custody The document, or null while it does not exist.
     */
    private data class FamilyDocument(val documentId: String?, val custody: SharedCustody?)

    private companion object {
        const val TAG = "CustodyModelRepo"

        /**
         * The scope the production sharing runs in: process-lifetime, `SupervisorJob` so a
         * failure in one collector cannot cancel it.
         *
         * The [CoroutineExceptionHandler] is the backstop the sharing coroutine had none of.
         * `shareIn` launches a root coroutine here; a root coroutine's uncaught exception goes to
         * the handler if there is one and to Android's default uncaught handler if there is not —
         * which kills the app. [sharedUpstream]'s `retryWhen` now covers every stage and should
         * leave nothing to escape, but "should" is what the chat listener's author had too. If
         * one ever does, it must fail the sync, not the process.
         */
        fun defaultScope(): CoroutineScope = CoroutineScope(
            SupervisorJob() + Dispatchers.Default + CoroutineExceptionHandler { _, throwable ->
                Log.e(
                    TAG,
                    "The shared custody stream died with an uncaught exception. Custody no " +
                        "longer syncs for the rest of this process; Room keeps serving the " +
                        "local pattern. This should be unreachable — retryWhen covers the " +
                        "whole upstream — so treat it as a bug report, not as weather.",
                    throwable
                )
            }
        )

        /** Keeps the shared listener warm across a tab switch or a config change. */
        const val STOP_TIMEOUT_MS = 5_000L

        /** First retry delay; doubled per attempt up to [MAX_BACKOFF_SHIFT]. */
        const val RETRY_BASE_MS = 1_000L

        /** Caps the backoff at `RETRY_BASE_MS shl 5` — 32 seconds — rather than growing forever. */
        const val MAX_BACKOFF_SHIFT = 5L
    }
}

/** The co-parent of [this] pair, the parent a request made here waits on; null for a malformed pair. */
private fun CustodyPair.partnerUid(): String? = participants.firstOrNull { it != myUid }

/**
 * The clock a day-swap offer starts (phase 3a, spec §4.2, §5.1), or null when it starts none: no
 * [answerBy] was chosen, [entry] answers an offer rather than making one, it has no group id to name
 * its clock by, or the offer's first day is too near for a deadline.
 *
 * The cap is 00:00 of the first of [dates], and the deadline and the cap are worked out in the zone
 * of the phone that offers, the zone its form showed them in. The clock carries the days that were
 * written ([dates]), so a "No answer" can still say which after the offer is replaced (ruling 12).
 */
private fun swapClock(
    pair: CustodyPair,
    entry: DayOverride,
    dates: List<String>,
    answerBy: AnswerByPick?
): RequestStart? {
    val groupId = entry.groupId?.takeIf { entry.isPending }
    val responder = pair.partnerUid()
    if (answerBy == null || groupId == null || responder == null) return null
    val days = dates.mapNotNull { runCatching { LocalDate.parse(it) }.getOrNull() }
    val zone = ZoneId.systemDefault()
    val cap = RequestCap.forSwap(days, zone)
    val deadline = cap?.let { AnswerBy.resolve(answerBy, System.currentTimeMillis(), it, zone) } ?: return null
    return RequestStart(
        kind = RequestKind.SWAP,
        ref = groupId,
        familyId = pair.documentId,
        responderUid = responder,
        answerByMillis = deadline,
        capMillis = cap,
        firstDate = days.minOrNull(),
        dayCount = dates.size
    )
}

/**
 * The clock a custody proposal starts (phase 3a, spec §4.2, §5.1), or null when it starts none: no
 * [answerBy] was chosen, or the first change is too near for a deadline.
 *
 * Its reference is the proposal's `proposedAt`. The cap is 00:00 of the first day, from today, on
 * which [proposed]'s proposal differs from [agreed] in anything the calendar shows (ruling 10), in
 * the zone of the phone that proposes; a proposal that changes nothing ahead has none.
 */
private fun proposalClock(
    pair: CustodyPair,
    agreed: CustodyModel,
    proposed: SharedCustody,
    answerBy: AnswerByPick?
): RequestStart? {
    val proposal = proposed.proposal
    val responder = pair.partnerUid()
    if (answerBy == null || proposal == null || responder == null) return null
    val zone = ZoneId.systemDefault()
    val cap = RequestCap.forProposal(agreed, proposal.model, LocalDate.now(zone), zone)
    val deadline = AnswerBy.resolve(answerBy, System.currentTimeMillis(), cap, zone) ?: return null
    return RequestStart(
        kind = RequestKind.PROPOSAL,
        ref = proposal.proposedAt,
        familyId = pair.documentId,
        responderUid = responder,
        answerByMillis = deadline,
        capMillis = cap
    )
}

/**
 * [current] with [myUid]'s proposal withdrawn when it is still the one made at [proposedAt], or null —
 * nothing to write — when it is not: answered, withdrawn or replaced meanwhile, or the co-parent's.
 */
private fun withdrawnIfUnanswered(current: SharedCustody?, proposedAt: String, myUid: String): SharedCustody? {
    val proposal = current?.proposal ?: return null
    if (proposal.proposedAt != proposedAt || proposal.proposedBy != myUid) return null
    return CustodyProposalTransition.withdraw(current, myUid).getOrNull()
}

/**
 * The card a proposal write owes the proposer: [ActivityKind.CUSTODY_ACCEPTED] or
 * [ActivityKind.CUSTODY_DECLINED] when [next] records a decision [current] did not hold, and null for
 * a write that decided nothing — a withdrawal, which keeps the last decision as it was. What the
 * transition recorded *is* the announcement, as a swap's status is.
 */
private fun decisionCard(current: SharedCustody, next: SharedCustody): ActivityKind? =
    next.lastDecision?.takeIf { it != current.lastDecision }?.let { decision ->
        when (decision.outcome) {
            CustodyDecisionOutcome.ACCEPTED -> ActivityKind.CUSTODY_ACCEPTED
            CustodyDecisionOutcome.DECLINED -> ActivityKind.CUSTODY_DECLINED
        }
    }

/** The chat card about a swap of [date]: offered, agreed or turned down, as [status] says. */
private fun swapAnnouncement(date: String, status: DayOverrideStatus) = ActivityAnnouncement(
    kind = when (status) {
        DayOverrideStatus.PENDING -> ActivityKind.DAY_SWAP_OFFERED
        DayOverrideStatus.ACCEPTED -> ActivityKind.DAY_SWAP_ACCEPTED
        DayOverrideStatus.DECLINED -> ActivityKind.DAY_SWAP_DECLINED
    },
    entityType = ActivityEntityType.DAY_SWAP,
    entityId = date,
    title = date,
    whenIso = date
)

/**
 * The push about a swap of [date]: its type and its date, and for a run of [dayCount] days the
 * count — what the receiving device writes its sentence from, in the reader's language (SEC-3).
 */
private fun swapPush(date: String, status: DayOverrideStatus, dayCount: Int): Map<String, String> {
    val action = when (status) {
        DayOverrideStatus.PENDING -> "offered"
        DayOverrideStatus.ACCEPTED -> "accepted"
        DayOverrideStatus.DECLINED -> "declined"
    }
    // A run of days gets its own type carrying the count, rather than a date the reader
    // would have to expand into a range. The count is a string like every other payload
    // value: `notification_queue` documents hold strings, and the receiving device parses
    // it — a type it cannot parse composes nothing at all, which is the SEC-3 rule.
    return if (dayCount > 1) {
        mapOf(
            PushPayload.TYPE to "day_swap_group_$action",
            PushPayload.DATE to date,
            PushPayload.DAY_COUNT to dayCount.toString()
        )
    } else {
        mapOf(
            PushPayload.TYPE to "day_swap_$action",
            PushPayload.DATE to date
        )
    }
}

/** [this] push with the request's deadline (phase 3a) when its clock was written; as it is otherwise. */
private fun Map<String, String>.withAnswerBy(answerByMillis: Long?): Map<String, String> =
    answerByMillis?.let { this + (PushPayload.ANSWER_BY to it.toString()) } ?: this
