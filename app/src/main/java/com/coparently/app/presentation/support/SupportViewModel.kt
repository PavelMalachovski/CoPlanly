package com.coparently.app.presentation.support

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.coparently.app.R
import com.coparently.app.data.family.FamilyViewSource
import com.coparently.app.data.payment.PaymentStore
import com.coparently.app.data.remote.firebase.FirebaseAuthService
import com.coparently.app.data.support.SupportAgreementStore
import com.coparently.app.data.support.SupportRead
import com.coparently.app.data.support.SupportReminderScheduler
import com.coparently.app.data.support.SupportReminders
import com.coparently.app.domain.payments.Payment
import com.coparently.app.domain.payments.PaymentKind
import com.coparently.app.domain.repository.PreferencesRepository
import com.coparently.app.domain.support.SupportAgreement
import com.coparently.app.domain.support.SupportTerms
import com.coparently.app.presentation.common.Parents
import com.coparently.app.presentation.common.ParentsSource
import com.coparently.app.presentation.common.UiText
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.math.BigDecimal
import java.time.YearMonth
import javax.inject.Inject

/** What the child support screen shows for the family on screen. */
sealed interface SupportUiState {
    /** Not read yet. */
    data object Loading : SupportUiState

    /** The account has no family on screen: there is nobody to agree with. */
    data object NoFamily : SupportUiState

    /** The family's document could not be read. */
    data class Unavailable(val familyId: String) : SupportUiState

    /**
     * The family's child support.
     *
     * @property agreement The agreement and any proposal, or null while the family has neither.
     * @property payments The family's child support payments.
     * @property reminderDays This phone's reminder, in days before the due day, or null when off.
     */
    data class Loaded(
        val familyId: String,
        val agreement: SupportAgreement?,
        val payments: List<Payment>,
        val reminderDays: Int?
    ) : SupportUiState {
        /** The payment recorded for [month] in the agreement's currency, or null. */
        fun paymentFor(month: YearMonth): Payment? {
            val currency = agreement?.agreement?.currency ?: return null
            return payments.firstOrNull { it.month == month && it.currency == currency }
        }
    }
}

/**
 * Family → Money → Child support (25 October 2026, owner decisions): the agreed monthly amount,
 * its proposal and answer, each month paid or not with the payer's "I paid" and the payee's
 * "Money arrived", and the payer's optional reminder.
 *
 * Follows the family switcher; every write names the family it was asked for, read from the state
 * the parent saw rather than from whatever is on screen when it lands.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
@Suppress("LongParameterList") // the agreement, its payments, the reminder, and who is who
class SupportViewModel @Inject constructor(
    private val store: SupportAgreementStore,
    private val payments: PaymentStore,
    private val reminders: SupportReminders,
    private val reminderScheduler: SupportReminderScheduler,
    auth: FirebaseAuthService,
    familyViewSource: FamilyViewSource,
    parentsSource: ParentsSource,
    preferencesRepository: PreferencesRepository
) : ViewModel() {

    /** The signed-in parent. */
    val myUid: String? = auth.getCurrentUser()?.uid

    /** The two parents, to name who pays. */
    val parents: StateFlow<Parents> = parentsSource.observe()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), Parents())

    /** The currency a first proposal starts in: the parent's default. */
    val defaultCurrency: StateFlow<String> = preferencesRepository.getDefaultCurrencyFlow()
        .map { it.code }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), "CZK")

    /** Re-reads this phone's reminder after it is changed here. */
    private val reminderChanged = MutableStateFlow(0)

    /** The screen's state. */
    val state: StateFlow<SupportUiState> = familyViewSource.observe()
        .map { it.onScreen }
        .distinctUntilChanged()
        .flatMapLatest { family ->
            if (family == null) {
                flowOf(SupportUiState.NoFamily)
            } else {
                combine(
                    store.observe(family),
                    payments.observe(family).map { list -> list.orEmpty().filter { it.kind == PaymentKind.SUPPORT } },
                    reminderChanged
                ) { read, paid, _ ->
                    when (read) {
                        SupportRead.Unavailable -> SupportUiState.Unavailable(family)
                        SupportRead.None -> SupportUiState.Loaded(family, null, paid, reminderOf(family))
                        is SupportRead.Known -> SupportUiState.Loaded(family, read.agreement, paid, reminderOf(family))
                    }
                }.onStart { emit(SupportUiState.Loading) }
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), SupportUiState.Loading)

    private val _busy = MutableStateFlow(false)

    /** True while a write is on its way. */
    val busy: StateFlow<Boolean> = _busy.asStateFlow()

    private val _error = MutableStateFlow<UiText?>(null)

    /** What went wrong with the last write, for a snackbar; cleared once shown. */
    val error: StateFlow<UiText?> = _error.asStateFlow()

    private fun reminderOf(familyId: String): Int? = myUid?.let { reminders.daysBefore(it, familyId) }

    /** Proposes [terms] in [state]'s family: the first terms or a change. */
    fun propose(state: SupportUiState.Loaded, terms: SupportTerms) =
        write { store.propose(state.familyId, terms, state.agreement) }

    /** Accepts the co-parent's waiting proposal. */
    fun accept(agreement: SupportAgreement) = write { store.accept(agreement) }

    /** Declines the co-parent's proposal, or withdraws this parent's own. */
    fun clearProposal(agreement: SupportAgreement) = write { store.clearProposal(agreement) }

    /** Records that this parent paid [month]'s child support under [terms]. */
    fun markPaid(state: SupportUiState.Loaded, terms: SupportTerms, month: YearMonth) =
        write { payments.markPaid(state.familyId, PaymentKind.SUPPORT, month, terms.currency, terms.amount) }

    /** Records that [payment]'s money arrived. */
    fun confirm(payment: Payment) = write { payments.confirm(payment) }

    /** Turns this phone's reminder in [familyId] on with [days] before the due day, or off with null. */
    fun setReminder(familyId: String, days: Int?) {
        val uid = myUid ?: return
        reminders.set(uid, familyId, days)
        if (days != null) reminderScheduler.schedule()
        reminderChanged.value += 1
    }

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
            if (!landed) _error.value = UiText.Res(R.string.payment_error_not_saved)
        }
    }

    /** The amount a parent types. */
    companion object {
        private const val STOP_TIMEOUT_MS = 5_000L

        /**
         * [text] as an amount — digits with an optional decimal comma or dot, at most two places,
         * above zero — or null when it is not one.
         */
        fun parseAmount(text: String): BigDecimal? =
            text.trim().replace(" ", "").replace(',', '.').toBigDecimalOrNull()
                ?.takeIf { it.signum() > 0 && it.scale() <= 2 }
    }
}
