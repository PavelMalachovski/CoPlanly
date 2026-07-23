package com.coparently.app.presentation.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.coparently.app.domain.model.Event
import com.coparently.app.domain.repository.ChangeRequestRepository
import com.coparently.app.domain.repository.EventRepository
import com.coparently.app.domain.repository.UserRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Duration
import java.time.LocalDateTime
import javax.inject.Inject

/**
 * Kind of change surfaced on the home dashboard.
 */
enum class ActivityKind { EVENT_CREATED, EVENT_UPDATED, PICKUP_CONFIRMED, CHANGE_REQUESTED }

/**
 * One entry in the "recent changes the co-parent made" feed.
 *
 * @property id Stable key for the list
 * @property kind What happened
 * @property title Event title the change concerns
 * @property timestamp When it happened (drives ordering)
 * @property eventId Event to open when tapped
 * @property isChangeRequest Whether tapping should open the change-requests inbox instead of the event
 */
data class ActivityItem(
    val id: String,
    val kind: ActivityKind,
    val title: String,
    val timestamp: LocalDateTime,
    val eventId: String,
    val isChangeRequest: Boolean
)

/**
 * ViewModel for the home dashboard: the last few changes the *other* parent made,
 * derived from already-synced events and change requests (no separate activity log).
 * Pure deletions aren't surfaced — there is nothing left to open.
 */
@HiltViewModel
class HomeViewModel @Inject constructor(
    eventRepository: EventRepository,
    changeRequestRepository: ChangeRequestRepository,
    private val userRepository: UserRepository
) : ViewModel() {

    private val _partnerId = MutableStateFlow<String?>(null)
    private val _paired = MutableStateFlow(false)

    init {
        viewModelScope.launch {
            val user = userRepository.getCurrentUser()
            _partnerId.value = user?.partnerId?.takeIf { it.isNotEmpty() }
            _paired.value = _partnerId.value != null
        }
    }

    /** Whether the user has a paired co-parent (drives the empty state copy). */
    val paired: StateFlow<Boolean> = _paired.asStateFlow()

    /**
     * Up to [MAX_ITEMS] most recent changes made by the co-parent, newest first.
     */
    val recentChanges: StateFlow<List<ActivityItem>> = combine(
        eventRepository.getAllEvents(),
        changeRequestRepository.getAllChangeRequests(),
        _partnerId
    ) { events, changeRequests, partnerId ->
        if (partnerId == null) return@combine emptyList()

        val eventItems = events
            .filter { !it.isPrivate && it.lastModifiedBy == partnerId }
            .map { it.toActivityItem() }

        val requestItems = changeRequests
            .filter { it.requestedBy == partnerId }
            .map { request ->
                ActivityItem(
                    id = "cr_${request.id}",
                    kind = ActivityKind.CHANGE_REQUESTED,
                    title = request.eventTitle,
                    timestamp = request.createdAt,
                    eventId = request.eventId,
                    isChangeRequest = true
                )
            }

        (eventItems + requestItems)
            .sortedByDescending { it.timestamp }
            .take(MAX_ITEMS)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    private fun Event.toActivityItem(): ActivityItem {
        val kind = when {
            pickupConfirmedBy != null && pickupConfirmedAt != null &&
                Duration.between(pickupConfirmedAt, updatedAt).abs() <= NEAR_THRESHOLD ->
                ActivityKind.PICKUP_CONFIRMED
            Duration.between(createdAt, updatedAt).abs() <= NEAR_THRESHOLD ->
                ActivityKind.EVENT_CREATED
            else -> ActivityKind.EVENT_UPDATED
        }
        return ActivityItem(
            id = "ev_${id}_$updatedAt",
            kind = kind,
            title = title,
            timestamp = updatedAt,
            eventId = id,
            isChangeRequest = false
        )
    }

    private companion object {
        const val MAX_ITEMS = 5
        val NEAR_THRESHOLD: Duration = Duration.ofSeconds(2)
    }
}
