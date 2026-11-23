package com.coparently.app.presentation.payments

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.coparently.app.data.chat.ChatPartner
import com.coparently.app.data.chat.ChatPartnerSource
import com.coparently.app.data.payment.PaymentAccounts
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/**
 * The account the co-parent of the family on screen receives payments on.
 *
 * @property uid The co-parent.
 * @property iban Their IBAN, or null when they have added none.
 */
data class PayeeAccount(val uid: String, val iban: String?)

/**
 * Follows the co-parent of the family on screen and the IBAN they receive payments on.
 *
 * The payer's payment code on the Money card and the Expenses header ([SettleUpPanel]) pays this
 * account. It lived with the chat's settle-up cards until settle up left the chat (November 2026).
 */
class SettleUpPaymentViewModel(
    chatPartnerSource: ChatPartnerSource,
    paymentAccounts: PaymentAccounts
) : ViewModel() {

    /** The co-parent's account, or null while there is none to show. */
    @OptIn(ExperimentalCoroutinesApi::class)
    val payee: StateFlow<PayeeAccount?> = chatPartnerSource.observe()
        .flatMapLatest { partner ->
            if (partner is ChatPartner.Linked) {
                paymentAccounts.observeIban(partner.partnerUid).map { PayeeAccount(partner.partnerUid, it) }
            } else {
                flowOf(null)
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), null)

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}
