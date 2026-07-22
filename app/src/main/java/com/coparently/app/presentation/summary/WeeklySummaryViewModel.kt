package com.coparently.app.presentation.summary

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.coparently.app.domain.model.ChangeRequest
import com.coparently.app.domain.model.ChangeRequestStatus
import com.coparently.app.domain.model.Event
import com.coparently.app.domain.repository.ChangeRequestRepository
import com.coparently.app.domain.repository.UserRepository
import com.coparently.app.domain.usecase.EventUseCases
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

/**
 * ViewModel for the weekly summary dashboard: mutual (non-private) activities
 * for the next seven days plus pending change requests.
 */
@HiltViewModel
class WeeklySummaryViewModel @Inject constructor(
    eventUseCases: EventUseCases,
    changeRequestRepository: ChangeRequestRepository,
    userRepository: UserRepository
) : ViewModel() {

    /** First day of the summarized window (today). */
    val weekStart: LocalDate = LocalDate.now()

    /** Last day of the summarized window (inclusive). */
    val weekEnd: LocalDate = weekStart.plusDays(DAYS_IN_SUMMARY - 1L)

    private val _currentUserId = MutableStateFlow("")
    val currentUserId: StateFlow<String> = _currentUserId.asStateFlow()

    init {
        viewModelScope.launch {
            userRepository.getCurrentUser()?.let { user ->
                _currentUserId.value = user.id
            }
        }
    }

    /**
     * Non-private events of the coming week (recurring ones already expanded),
     * grouped by day and sorted by start time. Private events are excluded —
     * this dashboard shows what both parents can see.
     */
    val eventsByDay: StateFlow<Map<LocalDate, List<Event>>> = eventUseCases.getEvents
        .getByDateRange(weekStart.atStartOfDay(), weekEnd.atTime(23, 59, 59))
        .map { events ->
            events
                .filter { !it.isPrivate }
                .sortedBy { it.startDateTime }
                .groupBy { it.startDateTime.toLocalDate() }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyMap()
        )

    /** Change requests still waiting for a response, newest first. */
    val pendingChangeRequests: StateFlow<List<ChangeRequest>> =
        changeRequestRepository.getAllChangeRequests()
            .map { requests -> requests.filter { it.status == ChangeRequestStatus.PENDING } }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5000),
                initialValue = emptyList()
            )

    private companion object {
        const val DAYS_IN_SUMMARY = 7
    }
}
