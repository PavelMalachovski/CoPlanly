package com.coparently.app.presentation.consent

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.coparently.app.data.consent.HealthConsentManager
import com.coparently.app.data.consent.HealthConsentWithdrawal
import com.coparently.app.domain.consent.HealthConsent
import com.coparently.app.domain.consent.isCurrent
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * The child-health consent gate (GDPR Art. 9(2)(a)), for every screen that asks or withdraws it:
 * the child editor, the onboarding child step and the Settings row.
 *
 * A child's medical section is **locked** until [unlocked] is true — shown as one line and an
 * "Add medical details" action that opens the consent dialog ([ask]). "Not now" ([notNow]) closes
 * the dialog and leaves the section locked; nothing else on the form depends on it.
 *
 * @param manager Reads, records and withdraws the consent.
 */
@HiltViewModel
class HealthConsentViewModel @Inject constructor(
    private val manager: HealthConsentManager
) : ViewModel() {

    /**
     * The stored consent, or null when never given or withdrawn — for the Settings row, which
     * prints its date. `Eagerly`, like [TelemetryConsentViewModel.consent], so a privacy row never
     * renders a wrong answer for a frame.
     */
    val consent: StateFlow<HealthConsent?> = manager.observe()
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    /**
     * Whether health details may be entered: a consent to the dialog **as worded today**. False
     * for none, and false for a consent to an older wording, which is asked again.
     */
    val unlocked: StateFlow<Boolean> = consent
        .map { it.isCurrent() }
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    private val _asking = MutableStateFlow(false)

    /** Whether the consent dialog is showing. */
    val asking: StateFlow<Boolean> = _asking.asStateFlow()

    private val _withdrawing = MutableStateFlow(false)

    /** Whether a withdrawal is in flight, so the Settings row cannot start a second. */
    val withdrawing: StateFlow<Boolean> = _withdrawing.asStateFlow()

    private val _withdrawal = MutableSharedFlow<HealthConsentWithdrawal>(extraBufferCapacity = 1)

    /** How each withdrawal ended, once, for a snackbar. */
    val withdrawal: SharedFlow<HealthConsentWithdrawal> = _withdrawal.asSharedFlow()

    /** Opens the consent dialog. */
    fun ask() {
        _asking.value = true
    }

    /** "Not now": closes the dialog and records nothing. The medical section stays locked. */
    fun notNow() {
        _asking.value = false
    }

    /** "I agree": records the consent at the current wording version and time. */
    fun agree() {
        _asking.value = false
        viewModelScope.launch {
            try {
                manager.grant()
            } catch (e: CancellationException) {
                throw e
            } catch (
                @Suppress("TooGenericExceptionCaught") e: Exception
            ) {
                // The section simply stays locked; the parent can agree again.
                Log.e(TAG, "Recording the health consent failed", e)
            }
        }
    }

    /**
     * Withdraws the consent and clears the health details on the child records this parent
     * created. Confirmed by the screen before it is called.
     */
    fun withdraw() {
        if (_withdrawing.value) return
        _withdrawing.value = true
        viewModelScope.launch {
            try {
                _withdrawal.emit(manager.withdraw())
            } catch (e: CancellationException) {
                throw e
            } catch (
                @Suppress("TooGenericExceptionCaught") e: Exception
            ) {
                Log.e(TAG, "Withdrawing the health consent failed", e)
                _withdrawal.emit(HealthConsentWithdrawal.FAILED)
            } finally {
                _withdrawing.value = false
            }
        }
    }

    private companion object {
        const val TAG = "HealthConsentViewModel"
    }
}
