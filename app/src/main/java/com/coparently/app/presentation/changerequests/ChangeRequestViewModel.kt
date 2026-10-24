package com.coparently.app.presentation.changerequests

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.coparently.app.R
import com.coparently.app.data.repository.CustodyModelRepository
import com.coparently.app.data.repository.FamilySettingsRepository
import com.coparently.app.data.requests.RequestClockActions
import com.coparently.app.domain.changerequests.AnswerByPick
import com.coparently.app.domain.changerequests.RequestDeadlineId
import com.coparently.app.domain.custody.ContactWindow
import com.coparently.app.domain.custody.CustodyPatternDiff
import com.coparently.app.domain.custody.CustodyProposal
import com.coparently.app.domain.custody.CustodyResolver
import com.coparently.app.domain.custody.DayOverride
import com.coparently.app.domain.custody.DayOverrideTransition
import com.coparently.app.domain.custody.DaySwapGroup
import com.coparently.app.domain.events.EventAcceptanceTransition
import com.coparently.app.domain.expenses.SplitRatio
import com.coparently.app.domain.expenses.SplitRatioProposal
import com.coparently.app.domain.model.ChangeRequestStatus
import com.coparently.app.domain.parentingplan.CitationStatus
import com.coparently.app.domain.repository.ChangeRequestRepository
import com.coparently.app.domain.repository.EventRepository
import com.coparently.app.domain.repository.UserRepository
import com.coparently.app.domain.usecase.EventUseCases
import com.coparently.app.presentation.calendar.MiniMonthModel
import com.coparently.app.presentation.calendar.swapMiniMonths
import com.coparently.app.presentation.common.Parents
import com.coparently.app.presentation.common.ParentsSource
import com.coparently.app.presentation.common.UiText
import com.coparently.app.presentation.parentingplan.PlanReferenceSource
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.UUID
import javax.inject.Inject

private const val TAG = "ChangeRequestViewModel"

/**
 * How long marking the pending proposal seen — or finding the family a split answer goes to — waits
 * for the inbox to answer before giving up.
 */
private const val INBOX_WAIT_MS = 10_000L

/**
 * ViewModel for the requests inbox: the family on screen's requests in three sections
 * ([requests]), the month of each event waiting for an answer ([miniMonths]), and the answers —
 * to change requests, day swaps, pending events, the custody proposal and a proposed expense split —
 * and the asker's withdrawal of each.
 *
 * They live together because the inbox is one screen and one back-stack entry — a parent
 * answering "what is waiting for me" should not have to look in two places. They stay separate
 * *types* all the way down, though: a change request points at an event and moves its times, a
 * swap points at a date and moves nothing but who has it. See `DayOverride` for why folding one
 * into the other was rejected.
 */
@OptIn(ExperimentalCoroutinesApi::class)
// One inbox, its five request kinds, the repositories behind each and the clocks' actions (phase 3a);
// each kind brings its own answers, and splitting the inbox's answers across ViewModels would put one
// screen's decisions in two places.
@Suppress("LongParameterList", "TooManyFunctions")
@HiltViewModel
class ChangeRequestViewModel @Inject constructor(
    private val changeRequestRepository: ChangeRequestRepository,
    private val eventRepository: EventRepository,
    private val eventUseCases: EventUseCases,
    private val userRepository: UserRepository,
    private val custodyModelRepository: CustodyModelRepository,
    private val parentsSource: ParentsSource,
    private val planReferenceSource: PlanReferenceSource,
    private val requestClockActions: RequestClockActions,
    private val requestsSource: RequestsSource,
    private val familySettingsRepository: FamilySettingsRepository
) : ViewModel() {

    /**
     * Signed-in parent and paired co-parent, for naming whoever offered a swap.
     *
     * From `ParentsSource` — the single place that joins the signed-in user's Room row to the
     * co-parent's `PartnerSummary`. Never from `getAllUsers()`, which only ever holds a row for
     * the signed-in user and, on a device where more than one account has signed in over time,
     * holds rows for accounts paired with nobody.
     */
    val parents: StateFlow<Parents> = parentsSource.observe()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), Parents())

    private val _currentUserId = MutableStateFlow("")
    val currentUserId: StateFlow<String> = _currentUserId.asStateFlow()

    /**
     * Why the last answer did not go through, or null. A [UiText] the screen resolves (CQ-14):
     * this used to be English, and usually the refusing exception's own message.
     */
    private val _errorMessage = MutableStateFlow<UiText?>(null)
    val errorMessage: StateFlow<UiText?> = _errorMessage.asStateFlow()

    /**
     * The family on screen's requests (phase 2): what the inbox draws in its three sections, and
     * what the calendar's banner counts — one source, so the two cannot disagree with each other or
     * with Home's Requests row.
     */
    val requests: StateFlow<RequestsState> = requestsSource.observe()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), RequestsState.Loading)

    /**
     * The mini month of every event waiting for an answer, either way, by event id: the event's
     * month, drawn with the family's custody and the family's visible events (spec §5.3).
     *
     * The custody is the family's own document ([RequestsState.Ready.custody]), never Room's one
     * active model, which still holds the previous family's schedule while the family on screen
     * has none: a family without a schedule gets mini months without custody days (phase 2 review).
     *
     * Computed off the main thread — every waiting event expands the family's recurring events
     * over its own month's grid — but collected on the ViewModel's own: only the computation
     * hops to `Dispatchers.Default`. A `flowOn` producer there kept [requests] subscribed from a
     * background thread, and when the sharing stopped, that producer finished on its own thread
     * and resumed this scope after its dispatcher had gone — in the unit tests, after `resetMain`,
     * which surfaced as an uncaught exception in whichever test ran next.
     */
    val miniMonths: StateFlow<Map<String, MiniMonthModel>> = requests
        .mapLatest { state ->
            val ready = state as? RequestsState.Ready ?: return@mapLatest emptyMap()
            withContext(Dispatchers.Default) {
                val custody = ready.custody
                val custodyFor = CustodyResolver.resolver(
                    custody?.model,
                    custody?.dayOverrides.orEmpty(),
                    legacy = { null }
                )
                (ready.inbox.waitingOnMe.events + ready.inbox.waitingOnThem.events).associate { event ->
                    event.id to MiniMonthModel.forAwaitingEvent(event, ready.events, custodyFor)
                }
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    /**
     * The mini months of every day swap waiting for an answer, either way, by group key (21 October
     * 2026, Zuzana's test): the agreed custody, each offered day in the wash of the parent it would
     * go to ([swapMiniMonths]). Computed off the main thread like [miniMonths], from the same source.
     */
    val swapMonths: StateFlow<Map<String, List<MiniMonthModel>>> = requests
        .mapLatest { state ->
            val ready = state as? RequestsState.Ready ?: return@mapLatest emptyMap()
            withContext(Dispatchers.Default) {
                val custody = ready.custody
                val agreed = CustodyResolver.resolver(
                    custody?.model,
                    custody?.dayOverrides.orEmpty(),
                    legacy = { null }
                )
                (ready.inbox.waitingOnMe.swaps + ready.inbox.waitingOnThem.swaps).associate { group ->
                    val days = group.swaps.associate { it.date to it.override.toParent }
                    group.key to swapMiniMonths(days, ready.events, agreed)
                }
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    init {
        viewModelScope.launch {
            userRepository.getCurrentUser()?.let { user ->
                _currentUserId.value = user.id
            }
        }
        // Mirrors remote requests into Room while this ViewModel is alive.
        viewModelScope.launch {
            changeRequestRepository.observeRemote()
        }
    }

    /**
     * Accepts an incoming request: moves the event to the proposed time
     * (via the update use case so reminders are rescheduled) and marks the
     * request accepted, notifying the requester.
     */
    fun accept(requestId: String) {
        viewModelScope.launch {
            val request = changeRequestRepository.getChangeRequestById(requestId) ?: return@launch
            val event = resolveEvent(request.eventId)
            if (event == null) {
                _errorMessage.value = UiText.Res(R.string.change_request_error_event_missing)
                return@launch
            }
            val result = eventUseCases.updateEvent(
                event.copy(
                    startDateTime = request.proposedStartDateTime,
                    endDateTime = request.proposedEndDateTime,
                    updatedAt = LocalDateTime.now(),
                    lastModifiedBy = _currentUserId.value.takeIf { it.isNotEmpty() }
                        ?: event.lastModifiedBy
                )
            )
            result.fold(
                onSuccess = {
                    changeRequestRepository.updateStatus(requestId, ChangeRequestStatus.ACCEPTED)
                },
                onFailure = { e ->
                    Log.w(TAG, "Applying change request failed", e)
                    _errorMessage.value = UiText.Res(R.string.change_request_error_apply_failed)
                }
            )
        }
    }

    /**
     * The event a request is about, fetched from the server when this device has not synced it.
     *
     * `change_requests` is mirrored in realtime and `events` is not, so a proposal routinely
     * arrives before the event it refers to. Accepting then failed with "the event for this
     * request no longer exists" — the message the reporter read as "I have not synced with her"
     * — until the next fifteen-minute worker tick happened to run.
     *
     * @param eventId The event the request names.
     */
    private suspend fun resolveEvent(eventId: String) =
        eventRepository.getEventById(eventId) ?: eventRepository.fetchRemoteEvent(eventId)

    /** Declines an incoming request; the event stays unchanged. */
    fun decline(requestId: String) {
        viewModelScope.launch {
            changeRequestRepository.updateStatus(requestId, ChangeRequestStatus.DECLINED)
        }
    }

    /** Withdraws an outgoing pending request. */
    fun cancel(requestId: String) {
        viewModelScope.launch {
            changeRequestRepository.updateStatus(requestId, ChangeRequestStatus.CANCELLED)
        }
    }

    /**
     * Answers a whole offer at once — every day of it, one way.
     *
     * All or nothing, which is what a single card asking about "5 days" honestly means. A partial
     * answer would need a per-day list inside the card, and the owner chose the simpler
     * agreement: one notification, one decision.
     *
     * **The decision is taken against the document, not against the list this screen collected.**
     * Both parents write to one document; a held snapshot would silently drop whatever the other
     * one did in between, and the mirror this screen reads can only be behind. So the skip that
     * lets a part-answered group finish lives in the transform, where `writeSwap` has just read
     * the live map — not in a filter over `group.swaps`, which was the first shape of this fix
     * and is stale exactly when it is needed, on the retry after a partial run.
     *
     * `group.dates` is still filtered once up front, but only to decide whether there is anything
     * to say — never to decide what to write.
     *
     * A refusal is surfaced, not swallowed. `DayOverrideTransition` refuses a parent deciding
     * their own offer and `firestore.rules` refuses it again; either way the parent has to be
     * told, because a button that looks like it worked is worse than one that says it did not.
     *
     * @param group The offer being answered.
     * @param accept True to take the days, false to turn them down.
     */
    fun decideSwapGroup(group: DaySwapGroup, accept: Boolean) =
        decideSwapDays(group, group.dates.associateWith { accept })

    /**
     * Answers an offer day by day (21 October 2026, Zuzana's test): the days in [answers] that are
     * true are taken, the false ones turned down, and a day left out stays waiting. Two runs — the
     * accepted days, then the declined — so the offering parent gets one card per answer, as an
     * offer answered one way gets one. Each run is decided against the document as
     * [decideSwapGroup] describes.
     *
     * @param answers ISO date to the answer for it.
     */
    fun decideSwapDays(group: DaySwapGroup, answers: Map<String, Boolean>) {
        viewModelScope.launch {
            val uid = _currentUserId.value.takeIf { it.isNotEmpty() }
                ?: userRepository.getCurrentUserId()
                ?: return@launch
            val now = LocalDateTime.now().toString()
            if (!group.awaitsAnswerFrom(uid)) {
                _errorMessage.value = UiText.Res(R.string.change_request_error_offer_answered)
                return@launch
            }
            if (!stillShowsItsFamily()) return@launch
            for (accept in listOf(true, false)) {
                val dates = group.dates.filter { answers[it] == accept }
                if (dates.isNotEmpty() && !answerSwapDays(dates, uid, now, accept)) return@launch
            }
        }
    }

    /** One run of [decideSwapDays]: [dates] answered one way. False when the run was refused. */
    private suspend fun answerSwapDays(dates: List<String>, uid: String, now: String, accept: Boolean): Boolean =
        custodyModelRepository.applyDayOverridesForDates(
            dates = dates,
            transform = { current, date ->
                if (DayOverrideTransition.awaitsAnswerFrom(current, date, uid)) {
                    DayOverrideTransition.decideGroup(current, listOf(date), uid, now, accept)
                } else {
                    // Already decided, or this parent's own offer: leave it exactly as it is.
                    // `decideGroup` would refuse a one-element list it cannot answer and abort
                    // the run at that day, which is what stopped a part-answered group from ever
                    // being finished.
                    Result.success(current)
                }
            }
        ).onFailure { e ->
            Log.w(TAG, "Answering a day swap failed", e)
            _errorMessage.value = UiText.Res(R.string.change_request_error_swap_failed)
        }.isSuccess

    /**
     * Takes back this parent's own day-swap offer before the co-parent answers it (October 2026):
     * every day of it still waiting becomes withdrawn, and the co-parent gets one card in their
     * thread. A day whose earlier offer was agreed stays where that agreement put it.
     *
     * Decided against the document each write has just read, as [decideSwapGroup] is: a day the
     * co-parent answered meanwhile is left as they answered it, and the run goes on to the rest. A
     * refusal is said, never swallowed.
     *
     * @param group The offer being withdrawn.
     */
    fun withdrawSwapGroup(group: DaySwapGroup) {
        viewModelScope.launch {
            val uid = _currentUserId.value.takeIf { it.isNotEmpty() }
                ?: userRepository.getCurrentUserId()
                ?: return@launch
            if (!group.withdrawableBy(uid)) {
                _errorMessage.value = UiText.Res(R.string.change_request_error_offer_answered)
                return@launch
            }
            if (!stillShowsItsFamily()) return@launch
            val now = LocalDateTime.now().toString()
            custodyModelRepository.applyDayOverridesForDates(
                dates = group.dates,
                transform = { current, date ->
                    if (DayOverrideTransition.mayWithdraw(current, date, uid)) {
                        DayOverrideTransition.withdraw(current, date, uid, now)
                    } else {
                        // Answered meanwhile, or never this parent's: left exactly as it is.
                        Result.success(current)
                    }
                }
            ).onFailure { e ->
                Log.w(TAG, "Withdrawing a day swap failed", e)
                _errorMessage.value = UiText.Res(R.string.change_request_error_withdraw_failed)
            }
        }
    }

    /**
     * Asks the co-parent to change days the two of them already agreed to swap (tester feedback,
     * October 2026: "I agreed Lena would be with Igor, then I fell ill — let me suggest a change,
     * and keep the agreement documented").
     *
     * Each day is offered to the parent the agreement did not give it to, as the calendar's swap
     * sheet offers a day to its complement. The agreed answer is not lost: the new offer carries it
     * in [com.coparently.app.domain.custody.DayOverride.earlier], History keeps showing it, and the
     * day stays where it was agreed until the co-parent accepts — a decline leaves it there.
     *
     * The slot each day moves to is read from the document the write has just read, never from the
     * row this screen drew: a day whose agreement changed in between is offered from what it is
     * now, and a day with no agreement left is not offered at all.
     *
     * @param dates The agreed swap's days still ahead.
     * @param note Optional words for the co-parent.
     * @param answerBy The deadline the parent picked for the answer.
     */
    fun proposeSwapChange(dates: List<LocalDate>, note: String?, answerBy: AnswerByPick?) {
        viewModelScope.launch {
            val uid = _currentUserId.value.takeIf { it.isNotEmpty() }
                ?: userRepository.getCurrentUserId()
                ?: return@launch
            val days = dates.distinct().sorted().map { it.toString() }
            if (days.isEmpty() || !stillShowsItsFamily()) return@launch
            val atIso = LocalDateTime.now().toString()
            val groupId = UUID.randomUUID().toString()
            val reoffer = { current: Map<String, DayOverride>, date: String ->
                val agreed = current[date]?.agreedToParent
                if (agreed == null) {
                    Result.success(current)
                } else {
                    DayOverrideTransition.offerAll(current, mapOf(date to otherSlot(agreed)), uid, atIso, groupId, note)
                }
            }
            val result = if (days.size == 1) {
                // One day keeps the single write and single card an older co-parent build reads,
                // as the calendar's sheet does.
                custodyModelRepository.applyDayOverrides(
                    date = days.first(),
                    transform = { current -> reoffer(current, days.first()) },
                    answerBy = answerBy
                )
            } else {
                custodyModelRepository.applyDayOverridesForDates(days, reoffer, answerBy)
            }
            result.onFailure { e ->
                Log.w(TAG, "Asking to change an agreed swap failed", e)
                _errorMessage.value = UiText.Res(R.string.change_request_error_swap_failed)
            }
        }
    }

    /**
     * Whether the family the inbox drew is still the family on screen, saying so when it is not.
     *
     * A custody answer — a swap, a proposal — is written to the family on screen at the moment it
     * is sent (`CustodyModelRepository` reads the family once, as it writes). A push tap between
     * the inbox drawing one family's offer and the parent's tap would otherwise answer the other
     * family's, a decision the parent never saw (isolation audit, October 2026), so the answer is
     * refused and nothing is written. [requests] is read for what the screen drew, which is its
     * point; while it has drawn nothing yet, or the phone's row names no family, nothing is refused.
     */
    private suspend fun stillShowsItsFamily(): Boolean {
        val shown = (requests.value as? RequestsState.Ready)?.scope?.familyId
        val onScreen = parentsSource.familyOnScreen()
        val same = shown == null || onScreen == null || shown == onScreen
        if (!same) _errorMessage.value = UiText.Res(R.string.change_request_error_family_changed)
        return same
    }

    private fun otherSlot(slot: String): String =
        if (slot == ContactWindow.SLOT_ONE) ContactWindow.SLOT_TWO else ContactWindow.SLOT_ONE

    /**
     * The custody-pattern proposal this parent must answer now, or null when there is none.
     *
     * Only the co-parent's proposal surfaces here — a parent never decides their own (the
     * transition and `firestore.rules` both refuse it). The proposer sees theirs as the
     * calendar's preview overlay and a "waiting" banner instead.
     *
     * It is the inbox's own "Waiting for your answer" proposal (phase 3a), so Home's pop-up and the
     * calendar's Review banner follow the proposal's clock: past its deadline the next move is the
     * proposer's, and nothing asks this parent to answer until a last chance is given.
     */
    val pendingProposal: StateFlow<CustodyProposal?> = requests.map { state ->
        (state as? RequestsState.Ready)?.inbox?.waitingOnMe?.proposal
    }.distinctUntilChanged().stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = null
    )

    /**
     * What the card says about where the pending proposal came from (MON-21).
     *
     * [CitationStatus.None] for a proposal that cites nothing — every proposal from an older build
     * — and for one whose citation this build cannot read. Otherwise live against this phone's
     * copy of the plan, so an edit to the cited answer while the card is open turns "from the
     * parenting plan" into "the answer has changed since". A plan that cannot be read is
     * [CitationStatus.None] too: the card then reads as it always did, never as an error.
     */
    val pendingProposalCitation: StateFlow<CitationStatus> = combine(
        custodyModelRepository.observeShared(),
        _currentUserId
    ) { shared, uid ->
        uid to shared?.proposal?.takeIf { uid.isNotEmpty() && it.proposedBy != uid }
    }.distinctUntilChanged().flatMapLatest { (uid, proposal) ->
        if (proposal == null) {
            flowOf(CitationStatus.None)
        } else {
            planReferenceSource.observeCitation(proposal.planCitationWire, uid, proposal.proposedBy)
        }
    }.catch { e ->
        Log.w(TAG, "Could not read the parenting plan a proposal cites", e)
        emit(CitationStatus.None)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = CitationStatus.None
    )

    /**
     * What the pending proposal would actually change, or null when there is nothing to answer.
     *
     * A second flow rather than a wider [pendingProposal] type: three screens read that one and
     * only two of them want the diff. Both come off the same `observeShared()` document, which
     * carries the agreed pattern and the proposed one side by side — the diff needs no history
     * and no extra read.
     *
     * Anchored on today, so the sentence describes the fortnight the parent is looking at.
     */
    val pendingProposalDiff: StateFlow<CustodyPatternDiff?> = combine(
        custodyModelRepository.observeShared(),
        _currentUserId
    ) { shared, uid ->
        val proposal = shared?.proposal?.takeIf { uid.isNotEmpty() && it.proposedBy != uid }
            ?: return@combine null
        CustodyPatternDiff.of(
            agreed = shared.model,
            proposed = proposal.model,
            from = LocalDate.now()
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = null
    )

    /** Accepts the co-parent's pending custody proposal; it becomes the agreed pattern. */
    fun acceptProposal() {
        viewModelScope.launch {
            if (!stillShowsItsFamily()) return@launch
            custodyModelRepository.acceptProposal()
                .onFailure { e ->
                    Log.w(TAG, "Accepting a custody proposal failed", e)
                    _errorMessage.value = UiText.Res(R.string.change_request_error_proposal_accept_failed)
                }
        }
    }

    /** Declines the co-parent's pending custody proposal; the agreed pattern is untouched. */
    fun declineProposal() {
        viewModelScope.launch {
            if (!stillShowsItsFamily()) return@launch
            custodyModelRepository.declineProposal()
                .onFailure { e ->
                    Log.w(TAG, "Declining a custody proposal failed", e)
                    _errorMessage.value = UiText.Res(R.string.change_request_error_proposal_decline_failed)
                }
        }
    }

    /**
     * Takes this parent's own pending custody proposal back (October 2026); the agreed pattern never
     * moved. The co-parent gets a card in their thread. Written to the family on screen, so one drawn
     * for a family no longer on screen is refused ([stillShowsItsFamily]).
     */
    fun withdrawProposal() {
        viewModelScope.launch {
            if (!stillShowsItsFamily()) return@launch
            custodyModelRepository.withdrawProposal().onFailure { e ->
                Log.w(TAG, "Withdrawing a custody proposal failed", e)
                _errorMessage.value = UiText.Res(R.string.change_request_error_withdraw_failed)
            }
        }
    }

    /**
     * The co-parent's proposed expense split this parent must answer now, or null when there is none.
     *
     * The inbox's own "Waiting for your answer" split, like [pendingProposal]: Home's pop-up follows
     * its clock, so past its deadline nothing asks this parent to answer until a last chance is given.
     * This parent's own proposal is never here — nobody decides their own.
     */
    val pendingSplit: StateFlow<SplitRatioProposal?> = requests.map { state ->
        (state as? RequestsState.Ready)?.inbox?.waitingOnMe?.split
    }.distinctUntilChanged().stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = null
    )

    /**
     * The split the family on screen has agreed, from the same document as [pendingSplit] — what a
     * proposal would replace. Null while the family has no split document or it has not arrived.
     */
    val agreedSplit: StateFlow<SplitRatio?> = requests.map { state ->
        (state as? RequestsState.Ready)?.settings?.ratio
    }.distinctUntilChanged().stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = null
    )

    /** Agrees to the co-parent's proposed split; it prices the expenses recorded from then on. */
    fun acceptSplit() = answerSplit("Accepting", familySettingsRepository::acceptProposal)

    /** Turns the co-parent's proposed split down; the agreed split stays. */
    fun declineSplit() = answerSplit("Declining", familySettingsRepository::declineProposal)

    /** Takes this parent's own proposed split back; the agreed split never moved. */
    fun withdrawSplit() = answerSplit("Withdrawing", familySettingsRepository::withdrawProposal)

    /**
     * Writes an answer to the split proposal of the family the inbox drew.
     *
     * The family is the inbox's, never the switcher's at the moment of the write: an answer goes to
     * the family whose proposal was on screen when it was tapped (isolation audit, October 2026), and
     * one drawn for a family no longer on screen is refused ([stillShowsItsFamily]). A failure — the
     * transition refuses a parent answering their own proposal, or one already answered — is said,
     * never swallowed.
     *
     * @param what How the log names the answer.
     * @param write The repository call, given the family's id.
     */
    private fun answerSplit(what: String, write: suspend (familyId: String) -> Result<Unit>) {
        viewModelScope.launch {
            if (!stillShowsItsFamily()) return@launch
            val result = inboxFamilyId()?.let { write(it) }
                ?: Result.failure(IllegalStateException("No family's split is on screen"))
            result.onFailure { e ->
                Log.w(TAG, "$what an expense split failed", e)
                _errorMessage.value = UiText.Res(R.string.expenses_split_answer_failed)
            }
        }
    }

    /**
     * The family the inbox drew — [requests] while something collects it, which is what an answer
     * tapped on screen means — or, when nothing has drawn it, a fresh look at the inbox: [requests]'
     * value is not kept up to date while nothing collects it (CLAUDE.md item 17).
     */
    private suspend fun inboxFamilyId(): String? =
        inboxNow()?.scope?.familyId

    /** The inbox as drawn, or a fresh look at it when nothing collects [requests]. */
    private suspend fun inboxNow(): RequestsState.Ready? =
        (requests.value as? RequestsState.Ready)
            ?: withTimeoutOrNull(INBOX_WAIT_MS) {
                requestsSource.observe().filterIsInstance<RequestsState.Ready>().first()
            }

    /**
     * Records that this parent opened the co-parent's proposed split — Home's pop-up shows it. As
     * [markProposalSeen], from a fresh look at the inbox; it waits for the split itself, because the
     * family's split document arrives after the inbox's first answer, which is placed without it.
     */
    fun markSplitSeen() {
        viewModelScope.launch {
            val ready = withTimeoutOrNull(INBOX_WAIT_MS) {
                requestsSource.observe()
                    .filterIsInstance<RequestsState.Ready>()
                    .first { it.inbox.waitingOnMe.split != null }
            } ?: return@launch
            val split = ready.inbox.waitingOnMe.split ?: return@launch
            requestClockActions.markSeen(RequestDeadlineId.forSplit(split, ready.scope.familyId))
        }
    }

    /** Takes up an event the co-parent created for this parent. It then enters both calendars. */
    fun acceptEvent(eventId: String) = decideEvent(eventId, accept = true)

    /**
     * Turns one down. It stays `DECLINED` rather than being deleted: the creator needs to know
     * the answer was no, and an event that silently vanished would read as a bug.
     */
    fun declineEvent(eventId: String) = decideEvent(eventId, accept = false)

    /**
     * Takes back an event this parent created for the co-parent's days while it still waits for
     * their answer (October 2026, owner decision): the event is deleted, as the calendar deletes
     * one, and the co-parent's thread gets the "Event removed" card.
     *
     * Re-read first, like [decideEvent]: one the co-parent answered meanwhile is not deleted from
     * here — it is in both calendars now, or declined, and the calendar is where it is changed.
     */
    fun withdrawEvent(eventId: String) {
        viewModelScope.launch {
            val fresh = resolveEvent(eventId)
            if (fresh == null) {
                _errorMessage.value = UiText.Res(R.string.change_request_error_event_missing)
                return@launch
            }
            val uid = _currentUserId.value.takeIf { it.isNotEmpty() }
                ?: userRepository.getCurrentUserId()
                ?: return@launch
            if (fresh.createdByFirebaseUid != uid || !fresh.acceptance.isPending) {
                _errorMessage.value = UiText.Res(R.string.change_request_error_offer_answered)
                return@launch
            }
            eventUseCases.deleteEvent(fresh).onFailure { e ->
                Log.w(TAG, "Withdrawing an event failed", e)
                _errorMessage.value = UiText.Res(R.string.change_request_error_withdraw_failed)
            }
        }
    }

    /**
     * Applies a decision through [EventAcceptanceTransition] and saves it.
     *
     * The event is re-read immediately before deciding rather than taken from the list this
     * screen last collected: the co-parent may have edited or withdrawn it in between, and a held
     * snapshot would write that edit back out along with the answer.
     *
     * A refusal is surfaced, not swallowed — the transition refuses a creator deciding their own
     * event and a decision on an already-answered one, and both are things the parent has to see
     * rather than watch a button do nothing.
     */
    private fun decideEvent(eventId: String, accept: Boolean) {
        viewModelScope.launch {
            // A bare `?: return@launch` here made the button do nothing at all when the event
            // had not been synced yet — silent, which is worse than the error message the sibling
            // path at least printed.
            val fresh = resolveEvent(eventId)
            if (fresh == null) {
                _errorMessage.value = UiText.Res(R.string.change_request_error_event_missing)
                return@launch
            }
            val uid = _currentUserId.value.takeIf { it.isNotEmpty() }
                ?: userRepository.getCurrentUserId()
                ?: return@launch
            val now = LocalDateTime.now()
            val result = if (accept) {
                EventAcceptanceTransition.accept(fresh, uid, now)
            } else {
                EventAcceptanceTransition.decline(fresh, uid, now)
            }
            result.fold(
                onSuccess = { eventRepository.updateEvent(it) },
                onFailure = { e ->
                    Log.w(TAG, "Answering an event failed", e)
                    _errorMessage.value = UiText.Res(R.string.change_request_error_answer_failed)
                }
            )
        }
    }

    /**
     * Records that this parent opened the request clock [clockId] times (spec §4.5). A request with
     * no clock ([clockId] null) has nothing to record. Whether it is theirs to mark, and whether it
     * was marked already, the clock decides when it is read again ([RequestClockActions.markSeen]).
     */
    fun markSeen(clockId: String?) {
        clockId ?: return
        viewModelScope.launch { requestClockActions.markSeen(clockId) }
    }

    /**
     * Records that this parent opened the co-parent's pending custody proposal — Home's pop-up shows
     * it. Its clock's id needs the family, read here from a fresh look at the inbox rather than from
     * [requests]' value, which is not kept up to date while nothing collects it (CLAUDE.md item 17).
     */
    fun markProposalSeen() {
        viewModelScope.launch {
            val ready = withTimeoutOrNull(INBOX_WAIT_MS) {
                requestsSource.observe().filterIsInstance<RequestsState.Ready>().first()
            } ?: return@launch
            val proposal = ready.inbox.waitingOnMe.proposal ?: return@launch
            requestClockActions.markSeen(RequestDeadlineId.forProposal(proposal, ready.scope.familyId))
        }
    }

    private val _lastChanceInFlight = MutableStateFlow<Set<String>>(emptySet())

    /**
     * The clocks whose last chance is being given right now, by id: their card's button is disabled
     * until the read and the write behind it have answered.
     */
    val lastChanceInFlight: StateFlow<Set<String>> = _lastChanceInFlight.asStateFlow()

    /**
     * Gives the co-parent one last chance to answer the request clock [clockId] times (spec §4.4).
     * A refusal — the request was answered meanwhile, the server could not be reached, or this
     * phone's clock runs fast enough for the rules to refuse it — is said once, never retried.
     *
     * A second tap while the first is under way is ignored: it would be refused, because the first
     * has given the one last chance, and the parent would be told it failed when it did not.
     */
    fun giveLastChance(clockId: String) {
        if (clockId in _lastChanceInFlight.value) return
        _lastChanceInFlight.update { it + clockId }
        viewModelScope.launch {
            try {
                if (!requestClockActions.giveLastChance(clockId)) {
                    _errorMessage.value = UiText.Res(R.string.request_last_chance_failed)
                }
            } finally {
                _lastChanceInFlight.update { it - clockId }
            }
        }
    }

    /** Forgets the last error once the screen has shown it. */
    fun clearError() {
        _errorMessage.value = null
    }
}
