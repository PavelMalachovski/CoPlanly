package com.coparently.app.presentation.custody

import android.util.Log
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.coparently.app.R
import com.coparently.app.data.repository.CustodyModelRepository
import com.coparently.app.data.repository.PatternSubmission
import com.coparently.app.domain.changerequests.AnswerByPick
import com.coparently.app.domain.changerequests.RequestCap
import com.coparently.app.domain.custody.CustodyProposalTransition
import com.coparently.app.domain.custody.CustodyResolver
import com.coparently.app.domain.custody.HolidayFairness
import com.coparently.app.domain.custody.HolidayFairnessCalculator
import com.coparently.app.domain.custody.SeasonalLayer
import com.coparently.app.domain.custody.SharedCustody
import com.coparently.app.domain.custody.SharedCustodyRead
import com.coparently.app.domain.holidays.HolidayLocation
import com.coparently.app.domain.holidays.SchoolVacationSuggestions
import com.coparently.app.domain.holidays.VacationSuggestion
import com.coparently.app.domain.model.CustodyModel
import com.coparently.app.domain.parentingplan.PlanReference
import com.coparently.app.domain.parentingplan.PlanScheduleTarget
import com.coparently.app.domain.repository.ChildInfoRepository
import com.coparently.app.domain.repository.UserRepository
import com.coparently.app.presentation.common.Parents
import com.coparently.app.presentation.common.ParentsSource
import com.coparently.app.presentation.common.UiText
import com.coparently.app.presentation.parentingplan.PlanReferenceSource
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID
import javax.inject.Inject

/** Keeps the flows warm across a configuration change. */
private const val STOP_TIMEOUT_MS = 5_000L

/**
 * What the seasonal-schedule section shows (MON-14).
 *
 * @property hasBasePattern Whether there is a base pattern to layer on; without one the section
 *   says so rather than offering an editor that cannot save.
 * @property layers The agreed layers, in date order.
 * @property coParentProposalPending The co-parent has a schedule proposal waiting for this parent.
 *   A new change cannot be put while it waits — the repository would otherwise have nothing but
 *   a local save to fall back to — so the section asks for an answer first.
 * @property ownProposalPending This parent's own proposal is waiting for the co-parent.
 * @property suggestions Upcoming school vacations to fill a layer's dates from.
 * @property isSaving A change is being sent.
 * @property message The outcome of the last change, resolved by the screen; null when none.
 * @property planReference The agreed holiday answer from the parenting plan the screen was opened
 *   from (MON-21), quoted above the layer editor and cited on the proposal; null otherwise.
 */
data class SeasonalLayersUiState(
    val hasBasePattern: Boolean = false,
    val layers: List<SeasonalLayer> = emptyList(),
    val coParentProposalPending: Boolean = false,
    val ownProposalPending: Boolean = false,
    val suggestions: List<VacationSuggestion> = emptyList(),
    val isSaving: Boolean = false,
    val message: UiText? = null,
    val planReference: PlanReference? = null
)

/**
 * The fairness card's state (MON-20).
 *
 * @property year The year shown; [years] are the two it can switch between.
 * @property fairness The summary, or null while there is no schedule to summarise.
 */
data class FairnessUiState(
    val year: Int,
    val years: List<Int>,
    val fairness: HolidayFairness? = null
)

/**
 * Seasonal layers (MON-14) and the holiday-fairness summary (MON-20) on the custody screen.
 *
 * One ViewModel for both because they read the same three things — the agreed pattern, the swaps
 * and the parent's holiday calendar — and the fairness card's "Propose a change" opens the layer
 * editor. **Neither applies anything to the co-parent's calendar**: a layer change goes through
 * [CustodyModelRepository.submitSeasonalLayers], which is the same proposal road a base-pattern
 * change takes, and the fairness card only counts.
 */
@HiltViewModel
class SeasonalScheduleViewModel @Inject constructor(
    private val custodyModelRepository: CustodyModelRepository,
    private val userRepository: UserRepository,
    childInfoRepository: ChildInfoRepository,
    parentsSource: ParentsSource,
    savedStateHandle: SavedStateHandle,
    private val planReferenceSource: PlanReferenceSource
) : ViewModel() {

    /** Both parents, for naming slots in the section and the card. */
    val parents: StateFlow<Parents> = parentsSource.observe()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), Parents())

    private val today: LocalDate = LocalDate.now()

    private val transient = MutableStateFlow(SeasonalLayersUiState())
    private val selectedYear = MutableStateFlow(today.year)

    @OptIn(ExperimentalCoroutinesApi::class)
    private val holidayLocation: Flow<HolidayLocation> = userRepository.observeCurrentUserId()
        .flatMapLatest { uid -> if (uid == null) flowOf(null) else userRepository.observeUserById(uid) }
        .map { HolidayLocation.of(it?.countryCode, it?.regionCode) }
        .onStart { emit(HolidayLocation.Default) }

    private val shared: Flow<SharedCustody?> = custodyModelRepository.observeShared().onStart { emit(null) }

    /**
     * The agreed schedule as Room holds it, for [answerByCap]'s preview, which is read outside any
     * collection: collected for the ViewModel's life, so its value is never the starting null of a
     * stream nobody is collecting (CLAUDE.md item 17's reason).
     */
    private val activeModel: StateFlow<CustodyModel?> = custodyModelRepository.getActiveModel()
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    /**
     * Whether a layer change will be a proposal the co-parent answers (phase 3a, spec §4.2): there is
     * a co-parent, and an agreed schedule to change. The layer editor shows its deadline block only
     * then.
     */
    val proposes: StateFlow<Boolean> = combine(parents, activeModel) { parents, model ->
        parents.isPaired && model != null
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), false)

    /** The section's state. */
    val layersState: StateFlow<SeasonalLayersUiState> = combine(
        custodyModelRepository.getActiveModel(),
        shared,
        holidayLocation,
        userRepository.observeCurrentUserId(),
        transient
    ) { model, remote, location, uid, local ->
        val proposer = remote?.proposal?.proposedBy
        local.copy(
            hasBasePattern = model != null,
            layers = model?.seasonalLayers.orEmpty(),
            coParentProposalPending = CustodyProposalTransition.pendingFromCoParent(remote, uid),
            ownProposalPending = proposer != null && proposer == uid,
            suggestions = SchoolVacationSuggestions.upcoming(location.provider, today)
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), SeasonalLayersUiState())

    /** The fairness card's state. */
    val fairnessState: StateFlow<FairnessUiState> = combine(
        custodyModelRepository.getActiveModel(),
        custodyModelRepository.observeDayOverrides().onStart { emit(emptyMap()) },
        holidayLocation,
        childInfoRepository.getAllChildInfo().onStart { emit(emptyList()) },
        selectedYear
    ) { model, overrides, location, children, year ->
        FairnessUiState(
            year = year,
            years = years(),
            fairness = model?.let {
                HolidayFairnessCalculator.of(
                    year = year,
                    custodyFor = CustodyResolver.resolver(it, overrides) { null },
                    provider = location.provider,
                    birthdays = children.mapNotNull { child ->
                        child.dateOfBirth?.let { born -> child.childName to born.toLocalDate() }
                    }
                )
            }
        )
    }.flowOn(Dispatchers.Default)
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
            FairnessUiState(today.year, years())
        )

    init {
        savedStateHandle.get<String>(ARG_PLAN_QUESTION)?.takeIf { it.isNotBlank() }?.let { loadPlanReference(it) }
    }

    /**
     * Loads the agreed parenting-plan answer the screen was opened from (MON-21), when it is a
     * holiday answer — the base-pattern answer belongs to the form above this section.
     */
    private fun loadPlanReference(questionId: String) {
        viewModelScope.launch {
            val reference = try {
                planReferenceSource.referenceFor(questionId)
            } catch (e: CancellationException) {
                throw e
            } catch (
                @Suppress("TooGenericExceptionCaught") e: Exception
            ) {
                Log.w(TAG, "Could not read the parenting plan answer", e)
                null
            }
            transient.update { state ->
                state.copy(planReference = reference?.takeIf { it.target == PlanScheduleTarget.SEASONAL_LAYER })
            }
        }
    }

    private fun years(): List<Int> = listOf(today.year, today.year + 1)

    /** Shows [year], one of [FairnessUiState.years]. */
    fun selectYear(year: Int) {
        if (year in years()) selectedYear.value = year
    }

    /**
     * Adds a layer, or replaces the one with [editingId], and submits the result. An invalid
     * draft changes nothing; the editor refuses to confirm one.
     *
     * @param citePlan True when the editor was opened from an agreed parenting-plan answer
     *   (MON-21): the proposal then cites it. The layer is still exactly the parent's draft.
     * @param answerBy The deadline the parent chose for the co-parent's answer (phase 3a), or null
     *   when the editor showed no deadline block; it applies only when the change is a proposal.
     */
    fun saveLayer(
        draft: SeasonalLayerDraft,
        editingId: String?,
        citePlan: Boolean = false,
        answerBy: AnswerByPick? = null
    ) {
        // The ViewModel's own state, not a WhileSubscribed stream (CLAUDE.md item 17).
        val citation = transient.value.planReference?.citationWire?.takeIf { citePlan }
        change(citation, answerBy) { current -> layersWith(current, draft, editingId, editingId ?: newId()) }
    }

    /**
     * The cap of the proposal saving [draft] would make (phase 3a, spec §4.2): 00:00 of the first
     * day, from today, that the layer changes — or null when it changes nothing ahead, when there is
     * no base pattern, and while the draft is not a layer yet. For the editor's deadline block; the
     * repository works it out again when it sends.
     */
    fun answerByCap(draft: SeasonalLayerDraft, editingId: String?): Long? {
        val active = activeModel.value ?: return null
        val layers = layersWith(active.seasonalLayers, draft, editingId, editingId ?: PREVIEW_LAYER_ID) ?: return null
        val zone = ZoneId.systemDefault()
        return RequestCap.forProposal(active, active.copy(seasonalLayers = layers), LocalDate.now(zone), zone)
    }

    /**
     * Removes the layer with [id] and submits the result. Its confirmation has no deadline block,
     * so a proposal it makes asks for an answer by the default deadline (ruling 9).
     */
    fun deleteLayer(id: String) = change(planCitation = null, answerBy = AnswerByPick.DEFAULT) { current ->
        current.filterNot { it.id == id }.takeIf { it.size != current.size }
    }

    /**
     * [current] with [draft] added as a layer under [id], or replacing the one with [editingId];
     * null when the draft is not a layer.
     */
    private fun layersWith(
        current: List<SeasonalLayer>,
        draft: SeasonalLayerDraft,
        editingId: String?,
        id: String
    ): List<SeasonalLayer>? {
        val existing = current.firstOrNull { it.id == editingId }
        return draft.toLayer(id, existing)?.let { layer -> current.filterNot { it.id == layer.id } + layer }
    }

    /** Clears the last outcome once the screen has shown it. */
    fun clearMessage() {
        transient.update { it.copy(message = null) }
    }

    /**
     * Applies [edit] to the layers **as Room holds them now** and submits the result.
     *
     * Read fresh rather than from [layersState]: a save path never trusts a `WhileSubscribed`
     * flow's `.value` (CLAUDE.md item 17). [edit] returning null means nothing changed.
     */
    private fun change(
        planCitation: String?,
        answerBy: AnswerByPick?,
        edit: (List<SeasonalLayer>) -> List<SeasonalLayer>?
    ) {
        transient.update { it.copy(isSaving = true) }
        viewModelScope.launch {
            val message = try {
                submit(planCitation, answerBy, edit)
            } catch (e: CancellationException) {
                throw e
            } catch (
                @Suppress("TooGenericExceptionCaught") e: Exception
            ) {
                Log.w(TAG, "Submitting the seasonal layers failed", e)
                UiText.Res(R.string.seasonal_save_failed)
            }
            transient.update { it.copy(isSaving = false, message = message) }
        }
    }

    private suspend fun submit(
        planCitation: String?,
        answerBy: AnswerByPick?,
        edit: (List<SeasonalLayer>) -> List<SeasonalLayer>?
    ): UiText? {
        val active = custodyModelRepository.getActiveModelSync()
            ?: return UiText.Res(R.string.seasonal_needs_base_pattern)
        val next = edit(active.seasonalLayers)
        return when {
            next == null -> null
            coParentProposalWaiting() -> UiText.Res(R.string.seasonal_answer_pending_first)
            else -> when (custodyModelRepository.submitSeasonalLayers(next, planCitation, answerBy)) {
                PatternSubmission.PROPOSED -> UiText.Res(R.string.seasonal_sent_for_approval)
                PatternSubmission.ACTIVATED -> UiText.Res(R.string.seasonal_saved)
                // The repository's own refusals: the check above can race a proposal that lands
                // in between, and a document that cannot be read or written sends nothing.
                PatternSubmission.COPARENT_PROPOSAL_WAITING -> UiText.Res(R.string.seasonal_answer_pending_first)
                PatternSubmission.NOT_SENT -> UiText.Res(R.string.seasonal_save_failed)
                null -> UiText.Res(R.string.seasonal_needs_base_pattern)
            }
        }
    }

    /**
     * Whether the co-parent's proposal is waiting for this parent. Refused rather than sent: the
     * repository cannot put a second proposal over theirs and refuses one too
     * ([PatternSubmission.COPARENT_PROPOSAL_WAITING]); asking first saves the round trip.
     */
    private suspend fun coParentProposalWaiting(): Boolean {
        val read = custodyModelRepository.readShared() as? SharedCustodyRead.Found ?: return false
        return CustodyProposalTransition.pendingFromCoParent(read.custody, userRepository.getCurrentUserId())
    }

    private fun newId(): String = UUID.randomUUID().toString()

    private companion object {
        const val TAG = "SeasonalScheduleVM"

        /** The id a draft layer carries while it is only compared, before a save mints its own. */
        const val PREVIEW_LAYER_ID = "preview"

        /** The navigation argument naming the plan question the screen was opened from (MON-21). */
        const val ARG_PLAN_QUESTION = "planQuestion"
    }
}
