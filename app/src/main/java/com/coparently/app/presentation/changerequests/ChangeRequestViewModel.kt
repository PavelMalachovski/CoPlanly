package com.coparently.app.presentation.changerequests

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.coparently.app.R
import com.coparently.app.data.repository.CustodyModelRepository
import com.coparently.app.domain.custody.CustodyPatternDiff
import com.coparently.app.domain.custody.CustodyProposal
import com.coparently.app.domain.custody.CustodyResolver
import com.coparently.app.domain.custody.DayOverrideTransition
import com.coparently.app.domain.custody.DaySwapGroup
import com.coparently.app.domain.events.EventAcceptanceTransition
import com.coparently.app.domain.model.ChangeRequestStatus
import com.coparently.app.domain.parentingplan.CitationStatus
import com.coparently.app.domain.repository.ChangeRequestRepository
import com.coparently.app.domain.repository.EventRepository
import com.coparently.app.domain.repository.UserRepository
import com.coparently.app.domain.usecase.EventUseCases
import com.coparently.app.presentation.calendar.MiniMonthModel
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
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.LocalDateTime
import javax.inject.Inject

private const val TAG = "ChangeRequestViewModel"

/**
 * ViewModel for the requests inbox: the family on screen's requests in three sections
 * ([requests]), the month of each event waiting for an answer ([miniMonths]), and the answers —
 * to change requests, day swaps, pending events and the custody proposal.
 *
 * They live together because the inbox is one screen and one back-stack entry — a parent
 * answering "what is waiting for me" should not have to look in two places. They stay separate
 * *types* all the way down, though: a change request points at an event and moves its times, a
 * swap points at a date and moves nothing but who has it. See `DayOverride` for why folding one
 * into the other was rejected.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@Suppress("LongParameterList") // one inbox, two request kinds, and the repositories behind each
@HiltViewModel
class ChangeRequestViewModel @Inject constructor(
    private val changeRequestRepository: ChangeRequestRepository,
    private val eventRepository: EventRepository,
    private val eventUseCases: EventUseCases,
    private val userRepository: UserRepository,
    private val custodyModelRepository: CustodyModelRepository,
    parentsSource: ParentsSource,
    private val planReferenceSource: PlanReferenceSource,
    requestsSource: RequestsSource
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
    fun decideSwapGroup(group: DaySwapGroup, accept: Boolean) {
        viewModelScope.launch {
            val uid = _currentUserId.value.takeIf { it.isNotEmpty() }
                ?: userRepository.getCurrentUserId()
                ?: return@launch
            val now = LocalDateTime.now().toString()
            if (!group.awaitsAnswerFrom(uid)) {
                _errorMessage.value = UiText.Res(R.string.change_request_error_offer_answered)
                return@launch
            }
            custodyModelRepository.applyDayOverridesForDates(group.dates) { current, date ->
                if (DayOverrideTransition.awaitsAnswerFrom(current, date, uid)) {
                    DayOverrideTransition.decideGroup(current, listOf(date), uid, now, accept)
                } else {
                    // Already decided, or this parent's own offer: leave it exactly as it is.
                    // `decideGroup` would refuse a one-element list it cannot answer and abort
                    // the run at that day, which is what stopped a part-answered group from ever
                    // being finished.
                    Result.success(current)
                }
            }.onFailure { e ->
                Log.w(TAG, "Answering a day swap failed", e)
                _errorMessage.value = UiText.Res(R.string.change_request_error_swap_failed)
            }
        }
    }

    /**
     * The custody-pattern proposal this parent must answer, or null when there is none.
     *
     * Only the co-parent's proposal surfaces here — a parent never decides their own (the
     * transition and `firestore.rules` both refuse it). The proposer sees theirs as the
     * calendar's preview overlay and a "waiting" banner instead.
     */
    val pendingProposal: StateFlow<CustodyProposal?> = combine(
        custodyModelRepository.observeShared(),
        _currentUserId
    ) { shared, uid ->
        shared?.proposal?.takeIf { uid.isNotEmpty() && it.proposedBy != uid }
    }.stateIn(
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
            custodyModelRepository.acceptProposal().onFailure { e ->
                Log.w(TAG, "Accepting a custody proposal failed", e)
                _errorMessage.value = UiText.Res(R.string.change_request_error_proposal_accept_failed)
            }
        }
    }

    /** Declines the co-parent's pending custody proposal; the agreed pattern is untouched. */
    fun declineProposal() {
        viewModelScope.launch {
            custodyModelRepository.declineProposal().onFailure { e ->
                Log.w(TAG, "Declining a custody proposal failed", e)
                _errorMessage.value = UiText.Res(R.string.change_request_error_proposal_decline_failed)
            }
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

    fun clearError() {
        _errorMessage.value = null
    }
}
