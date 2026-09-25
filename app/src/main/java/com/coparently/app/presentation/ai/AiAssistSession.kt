package com.coparently.app.presentation.ai

import com.coparently.app.R
import com.coparently.app.data.ai.AiConsentManager
import com.coparently.app.domain.ai.AiAssistResult
import com.coparently.app.domain.ai.isCurrent
import com.coparently.app.presentation.common.UiText
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Where one AI request stands, for the control that started it.
 */
sealed interface AiAssistState {

    /** Nothing in flight; the control can be tapped. */
    data object Idle : AiAssistState

    /** The consent dialog is showing; nothing has been sent. */
    data object AskingConsent : AiAssistState

    /** Recording the consent or waiting for the answer. */
    data object Working : AiAssistState

    /** The request ended without text; [message] says why, in the reader's language. */
    data class Failed(val message: UiText) : AiAssistState
}

/**
 * The consent-then-call sequence every AI control shares: check the consent, ask for it when it is
 * missing, record it on "I agree", call, and hand the text on — or say why not.
 *
 * Not a ViewModel: each screen's ViewModel owns one in its own scope, so the reply suggestion and
 * the month summary cannot drift into asking differently. **Nothing is sent before the parent has
 * agreed**, and a refusal for want of consent ([AiAssistResult.ConsentRequired]) drops the
 * remembered answer and asks again rather than showing an error.
 *
 * @param scope The owning ViewModel's scope
 * @param consent Reads and records the consent
 * @param onText Receives the text of a successful request, once
 */
class AiAssistSession(
    private val scope: CoroutineScope,
    private val consent: AiConsentManager,
    private val onText: (String) -> Unit
) {

    private val _state = MutableStateFlow<AiAssistState>(AiAssistState.Idle)

    /** Where the current request stands. */
    val state: StateFlow<AiAssistState> = _state.asStateFlow()

    /** The request waiting on the consent dialog, or in flight. */
    private var pending: (suspend () -> AiAssistResult)? = null
    private var job: Job? = null

    /**
     * Starts [call], asking for consent first when there is none. Ignored while one is in flight.
     */
    fun request(call: suspend () -> AiAssistResult) {
        if (_state.value == AiAssistState.Working) return
        pending = call
        job = scope.launch {
            _state.value = AiAssistState.Working
            if (consent.current().isCurrent()) {
                send(call)
            } else {
                _state.value = AiAssistState.AskingConsent
            }
        }
    }

    /** "I agree": records the consent, then sends the waiting request. */
    fun agree() {
        val call = pending ?: return
        job = scope.launch {
            _state.value = AiAssistState.Working
            if (consent.grant()) {
                send(call)
            } else {
                _state.value = AiAssistState.Failed(UiText.Res(R.string.ai_consent_not_saved))
            }
        }
    }

    /** "Cancel" on the consent dialog: nothing is recorded and nothing is sent. */
    fun decline() {
        pending = null
        _state.value = AiAssistState.Idle
    }

    /** Abandons whatever is in flight — the control that asked has gone away. */
    fun cancel() {
        job?.cancel()
        job = null
        pending = null
        _state.value = AiAssistState.Idle
    }

    /** Clears a shown failure, so the control can be tapped again. */
    fun dismissFailure() {
        if (_state.value is AiAssistState.Failed) _state.value = AiAssistState.Idle
    }

    private suspend fun send(call: suspend () -> AiAssistResult) {
        when (val result = call()) {
            is AiAssistResult.Text -> {
                pending = null
                _state.value = AiAssistState.Idle
                onText(result.text)
            }
            AiAssistResult.ConsentRequired -> {
                // The server holds no current consent (withdrawn on another phone, or a newer
                // wording): ask again, keeping the request for the answer.
                consent.forget()
                _state.value = AiAssistState.AskingConsent
            }
            else -> {
                pending = null
                _state.value = AiAssistState.Failed(result.failureText())
            }
        }
    }
}

/** What to tell the parent about a request that brought back no text. */
internal fun AiAssistResult.failureText(): UiText = UiText.Res(
    when (this) {
        AiAssistResult.Disabled -> R.string.ai_error_disabled
        AiAssistResult.RateLimited -> R.string.ai_error_rate_limited
        AiAssistResult.Unavailable -> R.string.ai_error_unavailable
        AiAssistResult.InvalidRequest -> R.string.ai_error_invalid_request
        AiAssistResult.EmptyThread -> R.string.ai_error_empty_thread
        AiAssistResult.NotParticipant -> R.string.ai_error_not_participant
        AiAssistResult.PairingNotLive -> R.string.ai_error_pairing_not_live
        AiAssistResult.ConsentRequired, AiAssistResult.Failed, is AiAssistResult.Text -> R.string.ai_error_failed
    }
)
