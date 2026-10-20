package com.coparently.app.presentation.parentingplan

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.coparently.app.data.chat.ChatPartner
import com.coparently.app.data.chat.ChatPartnerSource
import com.coparently.app.data.crashlytics.CrashlyticsManager
import com.coparently.app.data.repository.CustodyModelRepository
import com.coparently.app.data.repository.ParentingPlanPair
import com.coparently.app.data.repository.ParentingPlanRepository
import com.coparently.app.domain.custody.CustodyProposalTransition
import com.coparently.app.domain.family.FamilyKey
import com.coparently.app.domain.model.PairingState
import com.coparently.app.domain.parentingplan.ParentingPlanEntry
import com.coparently.app.domain.parentingplan.PlanScheduleLink
import com.coparently.app.domain.repository.PairingRepository
import com.coparently.app.domain.repository.UserRepository
import com.coparently.app.presentation.common.Parents
import com.coparently.app.presentation.common.ParentsSource
import com.coparently.app.presentation.common.observeSignedInUser
import com.coparently.app.presentation.common.projectedCoParentUid
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject

/** What the parenting-plan screen is showing. */
sealed interface ParentingPlanUiState {

    /** Before the pairing state has answered. */
    data object Loading : ParentingPlanUiState

    /**
     * No co-parent, so there is nothing to compare against.
     *
     * A plan is two halves and a diff. Letting one parent fill it in alone would mean storing it
     * under an id that is not a family's, and then moving every answer at pairing — a migration
     * with nothing to catch it going wrong, in exchange for a screen that cannot yet do the one
     * thing it is for.
     */
    data object NoCoParent : ParentingPlanUiState

    /**
     * Both halves, whichever of them exist.
     *
     * @property familyId The family these halves are of. A save starts from [plan] only while it is
     *   still the family the save goes to (see `ParentingPlanViewModel.edit`).
     * @property coParentProposalPending The co-parent's own custody proposal is waiting for this
     *   parent's answer, so "Propose as the schedule" (MON-21) says why it cannot send rather than
     *   offering to — [CustodyProposalTransition.pendingFromCoParent], the rule the seasonal
     *   section already applies.
     */
    data class Ready(
        val plan: ParentingPlanPair,
        val coParentUid: String,
        val familyId: String,
        val coParentProposalPending: Boolean = false
    ) : ParentingPlanUiState {

        /** Whether [questionId] offers "Propose as the schedule" right now (MON-21). */
        fun offersProposal(questionId: String): Boolean = PlanScheduleLink.offersProposal(
            questionId = questionId,
            yours = plan.yours,
            theirs = plan.theirs,
            paired = true,
            coParentProposalPending = coParentProposalPending
        )

        /**
         * Whether [questionId] would offer it but for the co-parent's pending proposal — the row
         * then says so, so the missing action is explained rather than simply absent.
         */
        fun proposalBlocked(questionId: String): Boolean =
            coParentProposalPending && PlanScheduleLink.offersProposal(
                questionId = questionId,
                yours = plan.yours,
                theirs = plan.theirs,
                paired = true,
                coParentProposalPending = false
            )
    }
}

/**
 * The parenting plan: this parent's answers, the co-parent's, and where they agree (MON-5).
 */
@HiltViewModel
class ParentingPlanViewModel @Inject constructor(
    private val repository: ParentingPlanRepository,
    private val userRepository: UserRepository,
    private val pairingRepository: PairingRepository,
    parentsSource: ParentsSource,
    private val crashlyticsManager: CrashlyticsManager,
    private val custodyModelRepository: CustodyModelRepository
) : ViewModel() {

    /** Names and colours for the two parents; the screen renders both halves side by side. */
    val parents: StateFlow<Parents> = parentsSource.observe()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(SUBSCRIPTION_TIMEOUT_MS), Parents())

    /**
     * The family whose plan this is, or null while there is nobody to plan with.
     *
     * Built from the pairing state rather than from [Parents.coParent], which is null for a pair
     * that predates slot assignment — gating on it would hide the feature from exactly the
     * long-standing pairs most likely to want it.
     *
     * **The family on screen**, by the rule the chat and the names follow
     * ([ChatPartnerSource.resolve]): the pairing state says whether there is a co-parent, the
     * family switcher's projection on the signed-in row says which. The pairing state's own
     * partner is the *first* co-parent whatever the switcher says, so a parent showing their second
     * family was shown the first family's plan beside the second family's custody proposal.
     */
    private val scope: Flow<PlanScope?> = combine(
        userRepository.observeCurrentUserId(),
        pairingRepository.observePairingState(),
        userRepository.observeSignedInUser().map { it?.projectedCoParentUid() }
    ) { uid, pairing, projected -> scopeFor(uid, pairing, projected) }.distinctUntilChanged()

    @OptIn(ExperimentalCoroutinesApi::class)
    val uiState: StateFlow<ParentingPlanUiState> = scope
        .flatMapLatest { planScope ->
            if (planScope == null) {
                flowOf(ParentingPlanUiState.NoCoParent)
            } else {
                combine(
                    repository.observe(planScope.familyId, planScope.myUid),
                    // Only to know whether the co-parent's proposal is waiting (MON-21); a null
                    // first so the plan renders before the custody document has answered. Asked
                    // by the plan's own family, so another family's proposal never decides it.
                    custodyModelRepository.observeSharedOf(planScope.familyId).onStart { emit(null) }
                ) { plan, shared ->
                    ParentingPlanUiState.Ready(
                        plan = plan,
                        coParentUid = planScope.partnerUid,
                        familyId = planScope.familyId,
                        coParentProposalPending = CustodyProposalTransition.pendingFromCoParent(
                            shared,
                            planScope.myUid
                        )
                    )
                }
            }
        }
        .catch { e ->
            report("observe parenting plan", e)
            emit(ParentingPlanUiState.NoCoParent)
        }
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(SUBSCRIPTION_TIMEOUT_MS),
            ParentingPlanUiState.Loading
        )

    /**
     * Records this parent's answer to [questionId].
     *
     * The family comes from a fresh read of the scope, and this parent's current half from the
     * screen's value when it has loaded, else from the repository — see [edit].
     */
    fun answer(questionId: String, answer: String) = edit { entry, now ->
        entry.withAnswer(questionId, answer, now)
    }

    /**
     * Ticks or unticks agreement with the co-parent's current wording for [questionId].
     *
     * @param theirAnswer What the co-parent's answer says right now, or null to untick. An
     *   agreement is to a wording, not to a question — see [ParentingPlanEntry.agreedTo].
     */
    fun agree(questionId: String, theirAnswer: String?) = edit { entry, now ->
        entry.withAgreement(questionId, theirAnswer, now)
    }

    private fun edit(change: (ParentingPlanEntry, Long) -> ParentingPlanEntry) {
        viewModelScope.launch {
            try {
                // The first family a fresh collection of [scope] builds is none: every collection
                // of the pairing state starts at Loading, whatever the screen has already drawn.
                // Taking that first value returned here on every save, before the pairing listener
                // had answered, so answers and agreements were dropped without a word. Wait for the
                // family instead; a phone that cannot learn it in time has nowhere to save to.
                val planScope = withTimeoutOrNull(SCOPE_WAIT_MS) { scope.filterNotNull().first() }
                if (planScope == null) {
                    report("save parenting plan answer", IllegalStateException("No family to save the plan to"))
                    return@launch
                }
                // The on-screen value when there is one; otherwise a fresh read, never an empty
                // entry. `uiState` is `WhileSubscribed` (CLAUDE.md item 17): if an answer were
                // saved before the plan had loaded, starting from `ParentingPlanEntry()` would
                // overwrite every other answer this parent had written with that one. And only
                // while the screen still shows this family: a switch between the tap and this
                // read would otherwise write one family's half, agreements quoting that
                // co-parent's words included, into the other family's plan (isolation audit F10).
                val current = (uiState.value as? ParentingPlanUiState.Ready)
                    ?.takeIf { it.familyId == planScope.familyId }?.plan?.yours
                    ?: repository.observe(planScope.familyId, planScope.myUid).first().yours
                repository.save(
                    familyId = planScope.familyId,
                    myUid = planScope.myUid,
                    entry = change(current, System.currentTimeMillis())
                )
            } catch (e: CancellationException) {
                throw e
            } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
                report("save parenting plan answer", e)
            }
        }
    }

    private fun report(operation: String, cause: Throwable) {
        Log.w(TAG, "Could not $operation", cause)
        crashlyticsManager.recordException(cause)
    }

    /** The family a plan belongs to, and which half of it is this parent's. */
    private data class PlanScope(val familyId: String, val myUid: String, val partnerUid: String)

    private fun scopeFor(uid: String?, pairing: PairingState, projected: String?): PlanScope? {
        val myUid = uid?.takeIf { it.isNotBlank() }
        val partnerUid = (ChatPartnerSource.resolve(pairing, projected) as? ChatPartner.Linked)?.partnerUid
        // One exit rather than three guard clauses: `FamilyKey.orNull` already answers null for
        // every case the guards would have caught, so the checks here exist only to smart-cast.
        return if (myUid != null && partnerUid != null) {
            FamilyKey.orNull(myUid, partnerUid)?.let { familyId ->
                PlanScope(familyId = familyId, myUid = myUid, partnerUid = partnerUid)
            }
        } else {
            null
        }
    }

    companion object {
        private const val TAG = "ParentingPlanVM"

        /** Matches the other shared sources, so a rotation does not tear the listener down. */
        private const val SUBSCRIPTION_TIMEOUT_MS = 5_000L

        /** How long a save waits for the pairing state to name the family before giving up. */
        private const val SCOPE_WAIT_MS = 10_000L
    }
}
