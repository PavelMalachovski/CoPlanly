package com.coparently.app.presentation.changerequests

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.coparently.app.R
import com.coparently.app.domain.activity.ActivityAnnouncement
import com.coparently.app.domain.activity.ActivityAnnouncer
import com.coparently.app.domain.activity.ActivityEntityType
import com.coparently.app.domain.activity.ActivityKind
import com.coparently.app.domain.changerequests.AnswerByPick
import com.coparently.app.domain.family.FamilyAudience
import com.coparently.app.domain.family.FamilyKey
import com.coparently.app.domain.model.ChangeRequest
import com.coparently.app.domain.model.Event
import com.coparently.app.domain.model.User
import com.coparently.app.domain.repository.ChangeRequestRepository
import com.coparently.app.domain.repository.EventRepository
import com.coparently.app.domain.repository.UserRepository
import com.coparently.app.presentation.common.Parents
import com.coparently.app.presentation.common.ParentsSource
import com.coparently.app.presentation.common.UiText
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
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

    /**
     * The request is **recorded**, which is not the same as delivered (CQ-20).
     *
     * It used to be called `Sent`. `ChangeRequestRepositoryImpl.publish` catches every failure
     * and returns, leaving `syncedToFirestore = false` for the outbox to retry, so this state is
     * reached whether or not the write reached Firestore. The screen pops on it either way — the
     * honest report of what happened is the queued chip on the request's card, not this name.
     */
    data object Saved : RequestChangeUiState

    /** The form cannot go on; [message] is resolved by the screen (CQ-14). */
    data class Error(val message: UiText) : RequestChangeUiState
}

/**
 * ViewModel for proposing a new time for an existing event to the other parent.
 */
@HiltViewModel
class RequestChangeViewModel @Inject constructor(
    private val eventRepository: EventRepository,
    private val changeRequestRepository: ChangeRequestRepository,
    private val userRepository: UserRepository,
    private val activityAnnouncer: ActivityAnnouncer,
    parentsSource: ParentsSource
) : ViewModel() {

    /** Both parents, for naming the co-parent beside the deadline the form offers. */
    val parents: StateFlow<Parents> = parentsSource.observe()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(PARENTS_STOP_TIMEOUT_MS), Parents())

    private val _uiState = MutableStateFlow<RequestChangeUiState>(RequestChangeUiState.Loading)
    val uiState: StateFlow<RequestChangeUiState> = _uiState.asStateFlow()

    /**
     * Loads [eventId] once. The screen calls this again after a rotation, and a second load would
     * turn a request being sent back into an editable form with Send enabled.
     */
    fun loadEvent(eventId: String) {
        if (_uiState.value !is RequestChangeUiState.Loading) return
        viewModelScope.launch {
            val event = eventRepository.getEventById(eventId)
            _uiState.value = if (event != null) {
                RequestChangeUiState.Ready(event)
            } else {
                RequestChangeUiState.Error(UiText.Res(R.string.event_form_not_found))
            }
        }
    }

    /**
     * Creates and sends the change request to the paired co-parent, and announces it.
     *
     * The announcement no longer depends on where the request was started from. It used to be
     * posted only when a `conversationId` was passed — that is, only when the request came from
     * the chat screen — so a change proposed from the calendar reached the thread silently, or
     * rather did not reach it at all. `ActivityAnnouncer` resolves the pair's thread itself, from
     * the two uids, so there is nothing left for a caller to supply or to forget.
     *
     * The card goes to the thread of the family the request was sent in, and is posted even if the
     * form is left once the request is written; see the comments at the announcement.
     *
     * @param answerBy The deadline the parent chose for the co-parent's answer (phase 3a), or null
     *   for none. The pick goes to the repository as it was chosen: what a deadline is depends on
     *   the moment the request is sent and on the request's cap, which the repository works out.
     */
    fun submit(
        event: Event,
        proposedStart: LocalDateTime,
        proposedEnd: LocalDateTime?,
        note: String?,
        answerBy: AnswerByPick? = null
    ) {
        if (_uiState.value is RequestChangeUiState.Sending) return

        viewModelScope.launch {
            val user = userRepository.getCurrentUser()
            if (user == null) {
                _uiState.value = RequestChangeUiState.Error(UiText.Res(R.string.change_request_error_signed_out))
                return@launch
            }
            if (user.partnerId.isNullOrEmpty()) {
                _uiState.value =
                    RequestChangeUiState.Error(UiText.Res(R.string.change_request_error_not_paired))
                return@launch
            }
            // The co-parent of the event's own family, never the family on screen: an event of an
            // ended family, of no family, or of the other family must not reach this co-parent
            // (isolation audit, October 2026 — the chat's event picker offers all three).
            val partnerId = coParentOf(event, user)
            if (partnerId == null) {
                _uiState.value =
                    RequestChangeUiState.Error(UiText.Res(R.string.change_request_error_not_in_family))
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
                    ),
                    answerBy = answerBy
                )
                // The card is now a payload the *reader* renders in their own language, posted
                // by the one announcer every other change goes through. It used to be a sentence
                // this method built by concatenating hardcoded English — on a Russian phone, an
                // English card.
                //
                // `entityId` is the **event's** id, not the request's, and that is deliberate:
                // `ChangeRequestHighlight.forEvent` resolves a tap to the newest request the
                // reader can still act on, and one event collects several requests over its life.
                // Linking to a particular request would point at whichever one happened to be
                // newest when the card was posted.
                //
                // **To the thread of the family the request was sent in**: [partnerId], whom it is
                // addressed to, and not the family on screen when this runs. The request's clock waits
                // for the server before this point (phase 3a, up to ten seconds), a push tap can switch
                // the family meanwhile, and the announcer would otherwise read the user's row and post
                // this event's title and proposed time into the other family's thread.
                //
                // **Not cancellable.** Once `createChangeRequest` returns the server has the request,
                // or the outbox holds it, and the up arrow stays enabled while it is sent. A scope
                // cancelled by leaving the form must not cost the co-parent's thread the card of a
                // request that reached them. Before the request is written nothing is shielded: a form
                // left then cancels `createChangeRequest` as ever and never gets here.
                withContext(NonCancellable) {
                    activityAnnouncer.announce(
                        announcement = ActivityAnnouncement(
                            kind = ActivityKind.CHANGE_REQUESTED,
                            entityType = ActivityEntityType.CHANGE_REQUEST,
                            entityId = event.id,
                            title = event.title,
                            whenIso = proposedStart.toString()
                        ),
                        senderName = user.name,
                        partnerUid = partnerId
                    )
                }
                _uiState.value = RequestChangeUiState.Saved
            } catch (
                // Firestore/network failures surface as a form error, not a crash
                @Suppress("TooGenericExceptionCaught") e: Exception
            ) {
                // The exception's own text is English and technical: it goes to the log.
                Log.w("RequestChangeViewModel", "Creating a change request failed", e)
                _uiState.value =
                    RequestChangeUiState.Error(UiText.Res(R.string.change_request_error_send_failed))
            }
        }
    }

    /**
     * The co-parent of [event]'s own family while that pairing is live, or null when it has none
     * this parent could ask. A co-parent's event of no family is the family with them's
     * (`FamilyView.placeOf`).
     */
    private fun coParentOf(event: Event, user: User): String? {
        val family = event.familyId?.takeIf { it.isNotBlank() }
            ?: event.createdByFirebaseUid?.takeIf { it.isNotBlank() && it != user.id }
                ?.let { FamilyKey.orNull(user.id, it) }
        return FamilyAudience.partnerFor(family, user.id, user.partnerIds, user.partnerId)
    }

    private companion object {
        const val PARENTS_STOP_TIMEOUT_MS = 5_000L
    }
}
