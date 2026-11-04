package com.coparently.app.presentation.expenses

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.coparently.app.data.payment.PaymentAccounts
import com.coparently.app.data.payment.SettleUpMessages
import com.coparently.app.domain.payment.Iban
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.datetime.YearMonth

/**
 * One currency's balance a parent tapped Settle up on.
 *
 * @property netForMe Positive when the co-parent owes this parent, negative when this parent owes.
 * @property currency The balance's ISO currency code.
 */
data class SettleUpAsk(val netForMe: Double, val currency: String)

/**
 * A [SettleUpAsk] for the month it was tapped in, and the family whose balance it is — so the card
 * goes to that family's thread even if the switcher moves before Send (October 2026 isolation
 * audit). Null [familyId] sends to the family on screen.
 */
data class PendingSettleUp(val ask: SettleUpAsk, val month: YearMonth, val familyId: String? = null)

/** Where sending a settle-up stands. */
enum class SettleUpSend { IDLE, SENDING, SENT, IBAN_NOT_SAVED }

/**
 * Sends a settle-up card, and keeps this parent's IBAN — the account the co-parent's payment code
 * pays to (tester feedback, October 2026).
 *
 * In `:shared-ui` since MON-24 phase 2 (Task 10, slice 10d), split out of the app's
 * `SettleUpDialog.kt`, whose dialog stays with the screens.
 */
class SettleUpViewModel(
    private val settleUpMessages: SettleUpMessages,
    private val paymentAccounts: PaymentAccounts
) : ViewModel() {

    /** This parent's saved IBAN, or null for none. */
    val myIban: StateFlow<String?> = paymentAccounts.observeMine()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), null)

    private val _sendState = MutableStateFlow(SettleUpSend.IDLE)

    /** Where the last [send] stands. */
    val sendState: StateFlow<SettleUpSend> = _sendState.asStateFlow()

    /**
     * Saves [iban] when it changed — only on a balance owed to this parent, where it is what the
     * co-parent pays to — then posts the card. A save that fails sends nothing, so the co-parent is
     * never asked to pay to an account the app could not record.
     *
     * @param iban What the field holds, already checked by [Iban.normalize]; null or blank clears it.
     */
    fun send(pending: PendingSettleUp, iban: String?) {
        if (_sendState.value == SettleUpSend.SENDING) return
        _sendState.value = SettleUpSend.SENDING
        viewModelScope.launch {
            if (pending.ask.netForMe > 0) {
                val wanted = Iban.normalize(iban)
                // The saved value, read once from the server rather than from a screen's stream.
                val saved = withTimeoutOrNull(READ_TIMEOUT_MS) { paymentAccounts.observeMine().first() }
                if (wanted != saved && !paymentAccounts.setMine(wanted)) {
                    _sendState.value = SettleUpSend.IBAN_NOT_SAVED
                    return@launch
                }
            }
            settleUpMessages.post(
                pending.ask.netForMe,
                pending.ask.currency,
                pending.month,
                pending.familyId
            )
            _sendState.value = SettleUpSend.SENT
        }
    }

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
        const val READ_TIMEOUT_MS = 5_000L
    }
}
