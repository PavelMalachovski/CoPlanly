package com.coparently.app.presentation.payments

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.coparently.app.R
import com.coparently.app.data.family.FamilyViewSource
import com.coparently.app.data.payment.PaymentStore
import com.coparently.app.data.remote.firebase.FirebaseAuthService
import com.coparently.app.domain.payments.Payment
import com.coparently.app.domain.payments.PaymentKind
import com.coparently.app.presentation.common.UiText
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.math.BigDecimal
import java.time.YearMonth
import javax.inject.Inject

/**
 * Payments between the two parents of the family on screen: "I paid" and "Money arrived" for a
 * month's settle-up and for child support (owner decision, 25 October 2026).
 *
 * Follows the family switcher, so a switch never shows one family's payments under the other's
 * name. A write keeps the family it was asked for, which the caller passes.
 *
 * @property myUid The signed-in parent, for [com.coparently.app.domain.payments.PaymentState.of].
 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class PaymentsViewModel @Inject constructor(
    private val store: PaymentStore,
    auth: FirebaseAuthService,
    familyViewSource: FamilyViewSource
) : ViewModel() {

    val myUid: String? = auth.getCurrentUser()?.uid

    /** The family on screen's payments; empty while none can be read. */
    val payments: StateFlow<List<Payment>> = familyViewSource.observe()
        .map { it.onScreen }
        .distinctUntilChanged()
        .flatMapLatest { family ->
            if (family == null) flowOf(emptyList()) else store.observe(family).map { it.orEmpty() }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), emptyList())

    private val _busy = MutableStateFlow(false)

    /** True while a write is on its way; the buttons wait for it. */
    val busy: StateFlow<Boolean> = _busy.asStateFlow()

    private val _error = MutableStateFlow<UiText?>(null)

    /** What went wrong with the last write, for a snackbar; cleared once shown. */
    val error: StateFlow<UiText?> = _error.asStateFlow()

    /** The payment of [kind] for [month] in [currency], or null when none is recorded. */
    fun paymentFor(payments: List<Payment>, kind: PaymentKind, month: YearMonth, currency: String): Payment? =
        payments.firstOrNull { it.kind == kind && it.month == month && it.currency == currency }

    /** Records that this parent paid [amount] of [currency] for [month] in [familyId]. */
    fun markPaid(familyId: String, kind: PaymentKind, month: YearMonth, currency: String, amount: BigDecimal) =
        write { store.markPaid(familyId, kind, month, currency, amount) }

    /** Records that [payment]'s money arrived. */
    fun confirm(payment: Payment) = write { store.confirm(payment) }

    /** The snackbar has shown [error]. */
    fun errorShown() {
        _error.value = null
    }

    private fun write(block: suspend () -> Boolean) {
        if (_busy.value) return
        _busy.value = true
        viewModelScope.launch {
            val landed = block()
            _busy.value = false
            if (!landed) _error.update { UiText.Res(R.string.payment_error_not_saved) }
        }
    }

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}
