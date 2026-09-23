package com.coparently.app.presentation.professionals

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.coparently.app.domain.parentingplan.ParentingPlanEntry
import com.coparently.app.domain.professionals.ProfessionalGrant
import com.coparently.app.domain.professionals.ProfessionalGrantPolicy
import com.coparently.app.domain.repository.ProfessionalRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

/** What the professional's parenting-plan view is showing. */
sealed interface ProfessionalPlanUiState {

    /** Before the grant has answered. */
    data object Loading : ProfessionalPlanUiState

    /** No active grant — not yet consented, expired or revoked. */
    data object Unavailable : ProfessionalPlanUiState

    /**
     * Both halves, side by side, in the family's parent order.
     *
     * @property grant Names the two parents.
     * @property first The half of `grant.familyParents[0]`, or an empty entry.
     * @property second The half of `grant.familyParents[1]`, or null while they have written nothing.
     */
    data class Ready(
        val grant: ProfessionalGrant,
        val first: ParentingPlanEntry,
        val second: ParentingPlanEntry?
    ) : ProfessionalPlanUiState
}

/**
 * The parenting plan as a mediator reads it (MON-18): each parent's answer, and where they agree.
 *
 * Read-only by construction — it holds no save path — and neither half is privileged: the
 * professional is nobody's "you", so the two are shown in the family's stored order.
 */
@HiltViewModel
class ProfessionalPlanViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repository: ProfessionalRepository
) : ViewModel() {

    private val grantId: String = savedStateHandle.get<String>(ARG_GRANT_ID).orEmpty()

    /** Both halves, or why there are none. */
    @OptIn(ExperimentalCoroutinesApi::class)
    val uiState: StateFlow<ProfessionalPlanUiState> = repository.observeMyGrants()
        .map { grants -> grants.firstOrNull { it.id == grantId } }
        .map { it?.takeIf { g -> ProfessionalGrantPolicy.isActive(g, System.currentTimeMillis()) } }
        .distinctUntilChanged()
        .flatMapLatest { grant ->
            if (grant == null) {
                flowOf(ProfessionalPlanUiState.Unavailable)
            } else {
                repository.observePlan(grant).map { halves ->
                    ProfessionalPlanUiState.Ready(
                        grant = grant,
                        first = halves[grant.familyParents.first()] ?: ParentingPlanEntry(),
                        second = halves[grant.familyParents.last()]
                    )
                }
            }
        }
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
            ProfessionalPlanUiState.Loading
        )

    companion object {
        /** The navigation argument naming the grant. */
        const val ARG_GRANT_ID = "grantId"

        private const val STOP_TIMEOUT_MS = 5_000L
    }
}
