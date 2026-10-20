package com.coparently.app.presentation.expenses

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.coparently.app.R
import com.coparently.app.data.payment.PaymentAccounts
import com.coparently.app.data.payment.SettleUpMessages
import com.coparently.app.domain.payment.Iban
import com.coparently.app.presentation.theme.Spacing
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale
import javax.inject.Inject
import kotlin.math.abs

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
 */
@HiltViewModel
class SettleUpViewModel @Inject constructor(
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
            settleUpMessages.post(pending.ask.netForMe, pending.ask.currency, pending.month, pending.familyId)
            _sendState.value = SettleUpSend.SENT
        }
    }

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
        const val READ_TIMEOUT_MS = 5_000L
    }
}

/**
 * Asks before Settle up sends anything: whom the card goes to and what it will say, and — when the
 * balance is owed to this parent — the IBAN the co-parent's payment code will pay to.
 *
 * @param pending The balance and month being settled.
 * @param coParentName The co-parent's name, never a role.
 * @param onDismiss Closes the dialog, sending nothing.
 * @param onSent Called once the card is on its way.
 */
@Composable
fun SettleUpDialog(
    pending: PendingSettleUp,
    coParentName: String,
    onDismiss: () -> Unit,
    onSent: () -> Unit,
    viewModel: SettleUpViewModel = hiltViewModel()
) {
    val savedIban by viewModel.myIban.collectAsState()
    val send by viewModel.sendState.collectAsState()
    val owedToMe = pending.ask.netForMe > 0
    var iban by rememberSaveable { mutableStateOf<String?>(null) }
    val shownIban = iban ?: savedIban?.let(Iban::grouped).orEmpty()
    val ibanInvalid = owedToMe && shownIban.isNotBlank() && Iban.normalize(shownIban) == null
    val amount = remember(pending) { currencyFormat(pending.ask.currency).format(abs(pending.ask.netForMe)) }
    val month = remember(pending.month) {
        pending.month.format(DateTimeFormatter.ofPattern("LLLL yyyy", Locale.getDefault()))
    }
    LaunchedEffect(send) { if (send == SettleUpSend.SENT) onSent() }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.settle_up_dialog_title, month)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.M)) {
                Text(
                    stringResource(
                        if (owedToMe) R.string.settle_up_dialog_owed else R.string.settle_up_dialog_owing,
                        coParentName,
                        amount
                    )
                )
                if (owedToMe) {
                    OutlinedTextField(
                        value = shownIban,
                        onValueChange = { iban = it },
                        label = { Text(stringResource(R.string.settle_up_iban_label)) },
                        isError = ibanInvalid,
                        supportingText = if (ibanInvalid) {
                            { Text(stringResource(R.string.settle_up_iban_invalid)) }
                        } else {
                            null
                        },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                if (send == SettleUpSend.IBAN_NOT_SAVED) {
                    Text(stringResource(R.string.settle_up_iban_save_failed))
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { viewModel.send(pending, shownIban.takeIf { owedToMe }) },
                enabled = !ibanInvalid && send != SettleUpSend.SENDING
            ) {
                Text(stringResource(R.string.settle_up_send))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.settle_up_cancel)) }
        }
    )
}
