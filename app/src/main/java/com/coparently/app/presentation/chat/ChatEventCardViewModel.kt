package com.coparently.app.presentation.chat

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.coparently.app.domain.changerequests.AwaitingAnswers
import com.coparently.app.domain.model.Event
import com.coparently.app.domain.repository.EventRepository
import com.coparently.app.domain.repository.UserRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * What a tap on a chat card about an event found ([ChatCardTarget.Event]).
 *
 * Three answers, because the event a card names can be in three states for the parent reading it,
 * and each has its own place to open.
 */
sealed interface EventCardOutcome {

    /**
     * The event is here and is this parent's to look at: its preview opens over the thread, with
     * the editor one tap further (design item 5). A recurring event is its series, dated to the
     * first occurrence, as on Home: the card is about the series, not about one of its days.
     */
    data class Preview(val event: Event) : EventCardOutcome

    /**
     * The co-parent created it on this parent's day and it still waits for their answer. Every
     * calendar view hides it until then (`CalendarVisibility`), so it opens where it is answered:
     * the inbox, which lists it under "Events waiting on you".
     */
    data object AwaitingMe : EventCardOutcome

    /**
     * Deleted — on this phone or on the server — or not readable by this parent. There is nothing
     * to open, and the parent is told so in words about an event, not about a change request.
     */
    data object Missing : EventCardOutcome

    companion object {

        /**
         * The outcome for what a lookup by id found.
         *
         * Waiting on this parent is decided by [AwaitingAnswers.eventAwaitsAnswerFrom], the rule
         * the inbox's count is kept by. Everything else that exists is previewed — an accepted
         * event, one's own event still waiting on the co-parent, a declined one. The calendar
         * hides the last two and the inbox lists neither, so the preview is where they show.
         *
         * @param event The event, or null when neither this phone nor the server has it
         * @param signedInUid The reader's uid, or null while signed out
         */
        fun of(event: Event?, signedInUid: String?): EventCardOutcome = when {
            event == null -> Missing
            AwaitingAnswers.eventAwaitsAnswerFrom(event, signedInUid.orEmpty()) -> AwaitingMe
            else -> Preview(event)
        }
    }
}

/**
 * Opens the event a chat card names (`ChatEventCardHost` draws what this finds).
 *
 * Its own ViewModel rather than more of `ChatViewModel`, for the reason `ChatSearchViewModel`
 * gives: that class is at detekt's function budget, and its lifetime is tied to the process-wide
 * unread badge.
 *
 * **The server is asked when this phone does not have the event yet.** The thread is mirrored in
 * realtime and events are not — they arrive with the sync — so the card for a new event routinely
 * reaches the thread before the event reaches Room, as a change request reaches the inbox before
 * its event (`ChangeRequestViewModel.resolveEvent`, which reads the same two calls). Reading Room
 * alone would tell a parent that an event created a minute ago no longer exists. Neither call ever
 * returns a deleted event: [EventRepository.getEventById] hides this phone's pending tombstones and
 * [EventRepository.fetchRemoteEvent] answers null for the server's, and never over this phone's
 * own unsent delete.
 *
 * @param eventRepository Reads the event, from Room first and the server second
 * @param userRepository The signed-in uid, which decides whether the event waits on this parent
 */
@HiltViewModel
class ChatEventCardViewModel @Inject constructor(
    private val eventRepository: EventRepository,
    private val userRepository: UserRepository
) : ViewModel() {

    private val _outcome = MutableStateFlow<EventCardOutcome?>(null)

    /**
     * What the last tapped card found, until the screen has acted on it and called [dismiss]. The
     * preview stays open for as long as this is [EventCardOutcome.Preview], so it survives a
     * rotation; the other two are acted on once. Null while nothing is open or looked up.
     */
    val outcome: StateFlow<EventCardOutcome?> = _outcome.asStateFlow()

    /** The lookup in flight, replaced by the next tap rather than raced against it. */
    private var lookup: Job? = null

    /** Looks up [eventId], the event a tapped card names, and publishes what it found as [outcome]. */
    fun open(eventId: String) {
        lookup?.cancel()
        lookup = viewModelScope.launch {
            _outcome.value = find(eventId)
        }
    }

    /** The screen has acted on [outcome], or the preview was closed: nothing is open any more. */
    fun dismiss() {
        _outcome.value = null
    }

    private suspend fun find(eventId: String): EventCardOutcome = try {
        val event = eventRepository.getEventById(eventId) ?: eventRepository.fetchRemoteEvent(eventId)
        EventCardOutcome.of(event, userRepository.getCurrentUserId())
    } catch (e: CancellationException) {
        throw e
    } catch (
        @Suppress("TooGenericExceptionCaught") e: Exception
    ) {
        // Room failing to answer is not something the parent can act on; there is still nothing
        // to open, and the tap must not look as if it was ignored.
        Log.w(TAG, "Looking up the event a chat card names failed", e)
        EventCardOutcome.Missing
    }

    private companion object {
        const val TAG = "ChatEventCardViewModel"
    }
}
