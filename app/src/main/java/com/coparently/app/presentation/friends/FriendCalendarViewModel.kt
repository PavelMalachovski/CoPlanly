package com.coparently.app.presentation.friends

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.coparently.app.domain.friends.CalendarFriendGrant
import com.coparently.app.domain.friends.FriendCalendarRead
import com.coparently.app.domain.repository.FriendRepository
import com.coparently.app.presentation.professionals.ProfessionalAgenda
import com.coparently.app.presentation.professionals.ProfessionalDay
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.scan
import kotlinx.coroutines.flow.stateIn
import java.time.LocalDate
import javax.inject.Inject

/** What a family friend's calendar is showing. */
sealed interface FriendCalendarUiState {

    /** Before the first read has answered. */
    data object Loading : FriendCalendarUiState

    /**
     * The access ended — revoked by a parent, or lapsed. Said rather than drawn as the last events
     * the screen saw, which would go on presenting a calendar the friend may no longer read.
     */
    data object AccessEnded : FriendCalendarUiState

    /** The read failed for any other reason; the screen offers Retry. */
    data object Failed : FriendCalendarUiState

    /**
     * Four weeks of the family's agreed events.
     *
     * @property grant This family's grant, for the parents' names and the friend's own uid; null
     *   while the grant listener has not answered yet, which right after a redeem it may not have.
     * @property days The window, one entry per day, with no custody: a friend cannot read the
     *   schedule, so no day says whose it is.
     */
    data class Ready(
        val grant: CalendarFriendGrant?,
        val days: List<ProfessionalDay>
    ) : FriendCalendarUiState
}

/**
 * A family friend's read-only calendar of one family (phase 2, spec §4.4): four weeks at a time
 * of the family's shared, agreed events, read from Firestore through the query the rules admit a
 * friend and never kept in Room. It writes nothing.
 *
 * Built on the professional's pieces ([ProfessionalAgenda], [ProfessionalDay]) and for the same
 * reason the professional's is not the parents' grid: the grid is built on Room and on edit
 * affordances a reader outside the pair must not be shown.
 */
@HiltViewModel
class FriendCalendarViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val friendRepository: FriendRepository
) : ViewModel() {

    private val familyId: String = savedStateHandle.get<String>(ARG_FAMILY_ID).orEmpty()

    private val windowStart = MutableStateFlow(LocalDate.now())

    /** Bumped by [retry]; each value re-subscribes the read. */
    private val attempt = MutableStateFlow(0)

    /**
     * This family's grant as the friend's own grant listener reports it, remembering whether it
     * was ever there: a grant that has not arrived yet is not one that ended.
     */
    private val presence: Flow<GrantPresence> = friendRepository.observeMyGrants()
        .map { grants -> grants.firstOrNull { it.familyId == familyId } }
        .scan<CalendarFriendGrant?, GrantPresence>(GrantPresence.NotYetSeen) { previous, grant ->
            when {
                grant != null -> GrantPresence.Present(grant)
                previous == GrantPresence.NotYetSeen -> GrantPresence.NotYetSeen
                else -> GrantPresence.Left
            }
        }
        .distinctUntilChanged()

    /** The read of the window, tagged with the window's first day. */
    @OptIn(ExperimentalCoroutinesApi::class)
    private val read: Flow<Pair<LocalDate, FriendCalendarRead>> = combine(windowStart, attempt) { start, _ -> start }
        .flatMapLatest { start ->
            friendRepository.observeFamilyEvents(familyId, start, start.plusDays(WINDOW_DAYS - 1L))
                .map { start to it }
        }

    /** The window, or why there is none. */
    val uiState: StateFlow<FriendCalendarUiState> = combine(presence, read) { grant, (start, answer) ->
        when {
            // Firestore does not promise to end a live listener when another document revokes
            // the read, so the grant leaving the friend's own list ends the access on its own.
            grant == GrantPresence.Left -> FriendCalendarUiState.AccessEnded
            answer == FriendCalendarRead.AccessEnded -> FriendCalendarUiState.AccessEnded
            answer == FriendCalendarRead.Failed -> FriendCalendarUiState.Failed
            else -> FriendCalendarUiState.Ready(
                grant = (grant as? GrantPresence.Present)?.grant,
                days = ProfessionalAgenda.days(
                    start,
                    WINDOW_DAYS,
                    (answer as FriendCalendarRead.Events).events,
                    custody = null
                )
            )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), FriendCalendarUiState.Loading)

    /** Moves the window four weeks earlier. */
    fun previous() {
        windowStart.value = windowStart.value.minusDays(WINDOW_DAYS.toLong())
    }

    /** Moves the window four weeks later. */
    fun next() {
        windowStart.value = windowStart.value.plusDays(WINDOW_DAYS.toLong())
    }

    /** Reads the window again after a failure. */
    fun retry() {
        attempt.value += 1
    }

    /** Whether this family's grant is in the friend's own list, and whether it ever was. */
    private sealed interface GrantPresence {
        data object NotYetSeen : GrantPresence
        data class Present(val grant: CalendarFriendGrant) : GrantPresence
        data object Left : GrantPresence
    }

    companion object {
        /** The navigation argument naming the family. */
        const val ARG_FAMILY_ID = "familyId"

        /** Four weeks at a time, as the professional's calendar pages. */
        const val WINDOW_DAYS = 28

        private const val STOP_TIMEOUT_MS = 5_000L
    }
}
