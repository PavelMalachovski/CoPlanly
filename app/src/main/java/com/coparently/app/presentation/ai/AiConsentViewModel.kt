package com.coparently.app.presentation.ai

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.coparently.app.R
import com.coparently.app.data.ai.AiConsentManager
import com.coparently.app.domain.ai.AiAssistAvailability
import com.coparently.app.domain.ai.AiConsent
import com.coparently.app.presentation.common.UiText
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * The Settings row for the AI-assist consent: whether it was given, and turning it off (GDPR
 * Art. 7(3): withdrawing is as easy as giving). Giving it is asked where it is used — the reply
 * suggestion and the month summary — never here.
 *
 * @param availability Whether this build offers the assist; when it does not, the row is not drawn
 *   and the profile is never read
 * @param manager Reads and withdraws the consent
 */
@HiltViewModel
class AiConsentViewModel @Inject constructor(
    availability: AiAssistAvailability,
    private val manager: AiConsentManager
) : ViewModel() {

    /** Whether the row is drawn at all. */
    val available: Boolean = availability.enabled

    /**
     * The stored consent, or null when never given or withdrawn. `Eagerly`, like the health
     * consent's row, so a privacy row never renders a wrong answer for a frame once known.
     */
    val consent: StateFlow<AiConsent?> = (if (available) manager.observe() else emptyFlow<AiConsent?>())
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    private val _withdrawing = MutableStateFlow(false)

    /** Whether a withdrawal is in flight, so the row cannot start a second. */
    val withdrawing: StateFlow<Boolean> = _withdrawing.asStateFlow()

    private val _messages = MutableSharedFlow<UiText>(extraBufferCapacity = 1)

    /** How each withdrawal ended, once, for a snackbar. */
    val messages: SharedFlow<UiText> = _messages.asSharedFlow()

    /** Withdraws the consent. Confirmed by the screen before it is called. */
    fun withdraw() {
        if (!available || _withdrawing.value) return
        _withdrawing.value = true
        viewModelScope.launch {
            try {
                val withdrawn = manager.withdraw()
                _messages.emit(
                    UiText.Res(if (withdrawn) R.string.ai_consent_withdrawn else R.string.ai_consent_withdraw_failed)
                )
            } finally {
                _withdrawing.value = false
            }
        }
    }
}
