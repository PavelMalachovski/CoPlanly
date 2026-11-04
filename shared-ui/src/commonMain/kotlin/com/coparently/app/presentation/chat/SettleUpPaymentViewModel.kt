package com.coparently.app.presentation.chat

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
 * The account the co-parent of the thread on screen receives payments on.
 *
 * @property uid The co-parent.
 * @property iban Their IBAN, or null when they have added none.
 */
data class PayeeAccount(val uid: String, val iban: String?)

/**
 * Follows the co-parent of the family on screen and the IBAN they receive payments on.
 *
 * In its own file since MON-24 phase 2 (Task 10), out of `SettleUpPayment.kt`, whose composables
 * draw the payment code with ZXing and share it through Android and stay in the app until the
 * screens move.
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
