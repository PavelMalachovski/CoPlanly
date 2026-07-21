package com.coparently.app.presentation.changerequests

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.coparently.app.domain.model.ChangeRequest
import com.coparently.app.domain.model.Event
import com.coparently.app.domain.repository.ChangeRequestRepository
import com.coparently.app.domain.repository.EventRepository
import com.coparently.app.domain.repository.UserRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.LocalDateTime
import java.util.UUID
import javax.inject.Inject

/**
 * State of the "request a change" form.
 */
sealed interface RequestChangeUiState {
    data object Loading : RequestChangeUiState

    /** Event loaded; the form can be shown. */
    data class Ready(val event: Event) : RequestChangeUiState

    /** Request is being written/synced; keeps the event so the form stays visible. */
    data class Sending(val event: Event) : RequestChangeUiState
    data object Sent : RequestChangeUiState
    data class Error(val message: String) : RequestChangeUiState
}

/**
 * ViewModel for proposing a new time for an existing event to the other parent.
 */
@HiltViewModel
class RequestChangeViewModel @Inject constructor(
    private val eventRepository: EventRepository,
    private val changeRequestRepository: ChangeRequestRepository,
    private val userRepository: UserRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow<RequestChangeUiState>(RequestChangeUiState.Loading)
    val uiState: StateFlow<RequestChangeUiState> = _uiState.asStateFlow()

    fun loadEvent(eventId: String) {
        viewModelScope.launch {
            val event = eventRepository.getEventById(eventId)
            _uiState.value = if (event != null) {
                RequestChangeUiState.Ready(event)
            } else {
                RequestChangeUiState.Error("Event not found")
            }
        }
    }

    /**
     * Creates and sends the change request to the paired co-parent.
     */
    fun submit(
        event: Event,
        proposedStart: LocalDateTime,
        proposedEnd: LocalDateTime?,
        note: String?
    ) {
        if (_uiState.value is RequestChangeUiState.Sending) return

        viewModelScope.launch {
            val user = userRepository.getCurrentUser()
            if (user == null) {
                _uiState.value = RequestChangeUiState.Error("Not signed in")
                return@launch
            }
            val partnerId = user.partnerId
            if (partnerId.isNullOrEmpty()) {
                _uiState.value =
                    RequestChangeUiState.Error("Pair with your co-parent first to send change requests")
                return@launch
            }

            _uiState.value = RequestChangeUiState.Sending(event)
            try {
                changeRequestRepository.createChangeRequest(
                    ChangeRequest(
                        id = UUID.randomUUID().toString(),
                        eventId = event.id,
                        eventTitle = event.title,
                        requestedBy = user.id,
                        requestedTo = partnerId,
                        currentStartDateTime = event.startDateTime,
                        currentEndDateTime = event.endDateTime,
                        proposedStartDateTime = proposedStart,
                        proposedEndDateTime = proposedEnd,
                        note = note?.takeIf { it.isNotBlank() },
                        createdAt = LocalDateTime.now()
                    )
                )
                _uiState.value = RequestChangeUiState.Sent
            } catch (
                // Firestore/network failures surface as a form error, not a crash
                @Suppress("TooGenericExceptionCaught") e: Exception
            ) {
                _uiState.value =
                    RequestChangeUiState.Error(e.message ?: "Failed to send the change request")
            }
        }
    }
}
