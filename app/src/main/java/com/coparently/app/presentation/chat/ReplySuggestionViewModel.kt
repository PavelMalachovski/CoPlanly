package com.coparently.app.presentation.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.coparently.app.data.ai.AiConsentManager
import com.coparently.app.domain.ai.AiAssistAvailability
import com.coparently.app.domain.ai.AiAssistRepository
import com.coparently.app.presentation.ai.AiAssistSession
import com.coparently.app.presentation.ai.AiAssistState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject

/**
 * "Suggest a reply" in the templates sheet: a draft written server-side from the thread's last
 * messages, put into the composer **for the parent to edit and send** — never sent by itself.
 *
 * Its own ViewModel rather than more of `ChatViewModel`, which is about the thread; this is one
 * control in one sheet, and it renders nothing while [available] is false.
 *
 * @param availability Whether this build offers the assist
 * @param consentManager The AI-assist consent, asked before the first request
 * @param repository The `aiAssist` callable
 */
@HiltViewModel
class ReplySuggestionViewModel @Inject constructor(
    availability: AiAssistAvailability,
    consentManager: AiConsentManager,
    private val repository: AiAssistRepository
) : ViewModel() {

    /** Whether the row is drawn at all. */
    val available: Boolean = availability.enabled

    private val _suggestion = MutableStateFlow<String?>(null)

    /** A draft waiting to be put into the composer; [consumeSuggestion] once it is. */
    val suggestion: StateFlow<String?> = _suggestion.asStateFlow()

    private val session = AiAssistSession(viewModelScope, consentManager) { text -> _suggestion.value = text }

    /** Where the request stands: idle, asking for consent, working, or failed with a reason. */
    val state: StateFlow<AiAssistState> = session.state

    /**
     * Asks for a draft reply in [conversationId], written in [locale].
     *
     * @param draftHint What the parent has typed so far, sent so the draft can build on it
     */
    fun suggest(conversationId: String, locale: String, draftHint: String?) {
        if (!available) return
        session.request { repository.suggestReply(conversationId, locale, draftHint) }
    }

    /** "I agree" on the consent dialog. */
    fun agree() = session.agree()

    /** "Cancel" on the consent dialog. */
    fun decline() = session.decline()

    /** The sheet closed: abandon whatever is in flight. */
    fun cancel() = session.cancel()

    /** The failure was read; the row can be tapped again. */
    fun dismissFailure() = session.dismissFailure()

    /** The draft is in the composer. */
    fun consumeSuggestion() {
        _suggestion.value = null
    }
}
