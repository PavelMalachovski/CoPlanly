package com.coparently.app.presentation.payments

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.coparently.app.domain.payments.Payment
import com.coparently.app.domain.payments.PaymentKind
import kotlinx.datetime.YearMonth
import org.koin.compose.viewmodel.koinViewModel

/**
 * What a screen showing [SettleUpPanel] needs to know of the family on screen's payments, read
 * once in composition: the recorded payments, the co-parent's account and whether a write is on its
 * way. The Money card on Home and the Expenses header read it alike, so the two never disagree.
 *
 * @property payments The family on screen's recorded payments
 * @property myUid The signed-in parent
 * @property familyOnScreen The family "I paid" records into when the caller names none
 * @property payeeIban The account the co-parent is paid on, or null when they have added none
 * @property busy True while a write is on its way
 */
class SettleUpSource internal constructor(
    val payments: List<Payment>,
    val myUid: String?,
    val familyOnScreen: String?,
    val payeeIban: String?,
    val busy: Boolean,
    private val viewModel: PaymentsViewModel
) {
    /** The panel for [month]'s balance in [currency], or null when there is nothing to settle. */
    fun stateFor(netForMe: Double, splitKnown: Boolean, currency: String, month: YearMonth): SettleUpPanelState? =
        settleUpPanelState(
            netForMe = netForMe,
            splitKnown = splitKnown,
            currency = currency,
            month = month,
            payment = viewModel.paymentFor(payments, PaymentKind.EXPENSES, month, currency),
            myUid = myUid
        )

    /**
     * The buttons for [state]: "I paid" records [state]'s amount in [familyId] (the family whose
     * balance was drawn, or the family on screen when null), "Money arrived" confirms the recorded
     * payment, and asking to settle is the caller's [onAskToSettle].
     */
    fun actionsFor(state: SettleUpPanelState, familyId: String?, onAskToSettle: () -> Unit) = SettleUpPanelActions(
        onAskToSettle = onAskToSettle,
        onMarkPaid = {
            (familyId ?: familyOnScreen)?.let { family ->
                viewModel.markPaid(family, PaymentKind.EXPENSES, state.month, state.currency, state.amountMinor)
            }
        },
        onConfirm = viewModel::confirm,
        enabled = !busy && !myUid.isNullOrBlank()
    )
}

/** The family on screen's [SettleUpSource], from the payments and the co-parent's account. */
@Composable
fun rememberSettleUpSource(
    paymentsViewModel: PaymentsViewModel = koinViewModel(),
    payeeViewModel: SettleUpPaymentViewModel = koinViewModel()
): SettleUpSource {
    val payments by paymentsViewModel.payments.collectAsState()
    val familyOnScreen by paymentsViewModel.familyOnScreen.collectAsState()
    val busy by paymentsViewModel.busy.collectAsState()
    val payee by payeeViewModel.payee.collectAsState()
    return SettleUpSource(
        payments = payments,
        myUid = paymentsViewModel.myUid,
        familyOnScreen = familyOnScreen,
        payeeIban = payee?.iban,
        busy = busy,
        viewModel = paymentsViewModel
    )
}
