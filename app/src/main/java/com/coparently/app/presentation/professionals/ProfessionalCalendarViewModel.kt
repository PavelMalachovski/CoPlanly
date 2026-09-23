package com.coparently.app.presentation.professionals

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.coparently.app.domain.professionals.ProfessionalGrant
import com.coparently.app.domain.professionals.ProfessionalGrantPolicy
import com.coparently.app.domain.repository.ProfessionalRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import java.time.LocalDate
import javax.inject.Inject

/** What the professional's calendar is showing. */
sealed interface ProfessionalCalendarUiState {

    /** Before the grant has answered. */
    data object Loading : ProfessionalCalendarUiState

    /**
     * No active grant: not yet consented by both parents, expired, or revoked. Said rather than
     * rendered as an empty calendar, which would read as "this family has nothing planned".
     */
    data object Unavailable : ProfessionalCalendarUiState

    /** The window of days, and the grant that names the parents. */
    data class Ready(
        val grant: ProfessionalGrant,
        val days: List<ProfessionalDay>
    ) : ProfessionalCalendarUiState
}

/**
 * A professional's read-only view of one family's calendar (MON-18): a window of days, each with
 * whose day it is and the family's shared events. It writes nothing — there is no call here that
 * could, and the rules refuse every professional write anyway.
 *
 * Deliberately not the parents' `CalendarScreen`: that one is built on Room, which a professional's
 * phone does not hold for somebody else's family, and on edit affordances a professional must not
 * be shown.
 */
@HiltViewModel
class ProfessionalCalendarViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repository: ProfessionalRepository
) : ViewModel() {

    private val grantId: String = savedStateHandle.get<String>(ARG_GRANT_ID).orEmpty()

    private val windowStart = MutableStateFlow(LocalDate.now())

    /** The first day shown; the screen's header reads it. */
    val start: StateFlow<LocalDate> = windowStart

    private val grant: Flow<ProfessionalGrant?> = repository.observeMyGrants()
        .map { grants -> grants.firstOrNull { it.id == grantId } }
        .map { it?.takeIf { g -> ProfessionalGrantPolicy.isActive(g, System.currentTimeMillis()) } }
        .distinctUntilChanged()

    /** The window, or why there is none. */
    @OptIn(ExperimentalCoroutinesApi::class)
    val uiState: StateFlow<ProfessionalCalendarUiState> = combine(grant, windowStart) { g, start -> g to start }
        .flatMapLatest { (g, start) ->
            if (g == null) {
                flowOf(ProfessionalCalendarUiState.Unavailable)
            } else {
                val end = start.plusDays(WINDOW_DAYS - 1)
                combine(
                    repository.observeFamilyEvents(g, start, end),
                    repository.observeCustody(g)
                ) { events, custody ->
                    ProfessionalCalendarUiState.Ready(
                        grant = g,
                        days = ProfessionalAgenda.days(start, WINDOW_DAYS.toInt(), events, custody)
                    )
                }
            }
        }
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
            ProfessionalCalendarUiState.Loading
        )

    /** Moves the window one step earlier. */
    fun previous() {
        windowStart.value = windowStart.value.minusDays(WINDOW_DAYS)
    }

    /** Moves the window one step later. */
    fun next() {
        windowStart.value = windowStart.value.plusDays(WINDOW_DAYS)
    }

    companion object {
        /** The navigation argument naming the grant. */
        const val ARG_GRANT_ID = "grantId"

        /** Four weeks at a time — enough to see a two-week pattern repeat. */
        const val WINDOW_DAYS: Long = 28L

        private const val STOP_TIMEOUT_MS = 5_000L
    }
}
