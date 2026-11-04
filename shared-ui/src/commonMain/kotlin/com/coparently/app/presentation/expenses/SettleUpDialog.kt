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
import androidx.compose.ui.text.input.KeyboardCapitalization
import com.coparently.app.domain.payment.Iban
import com.coparently.app.presentation.theme.Spacing
import com.coparently.app.resources.Res
import com.coparently.app.resources.settle_up_cancel
import com.coparently.app.resources.settle_up_dialog_owed
import com.coparently.app.resources.settle_up_dialog_owing
import com.coparently.app.resources.settle_up_dialog_title
import com.coparently.app.resources.settle_up_iban_invalid
import com.coparently.app.resources.settle_up_iban_label
import com.coparently.app.resources.settle_up_iban_save_failed
import com.coparently.app.resources.settle_up_send
import com.coparently.app.utils.ReaderDateText
import com.coparently.app.utils.ReaderMoneyText
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import kotlin.math.abs

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
    viewModel: SettleUpViewModel = koinViewModel()
) {
    val savedIban by viewModel.myIban.collectAsState()
    val send by viewModel.sendState.collectAsState()
    val owedToMe = pending.ask.netForMe > 0
    var iban by rememberSaveable { mutableStateOf<String?>(null) }
    val shownIban = iban ?: savedIban?.let(Iban::grouped).orEmpty()
    val ibanInvalid = owedToMe && shownIban.isNotBlank() && Iban.normalize(shownIban) == null
    val amount = remember(pending) { ReaderMoneyText.amount(abs(pending.ask.netForMe), pending.ask.currency) }
    val month = remember(pending.month) {
        ReaderDateText.pattern(pending.month, "LLLL yyyy")
    }
    LaunchedEffect(send) { if (send == SettleUpSend.SENT) onSent() }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(Res.string.settle_up_dialog_title, month)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.M)) {
                Text(
                    stringResource(
                        if (owedToMe) Res.string.settle_up_dialog_owed else Res.string.settle_up_dialog_owing,
                        coParentName,
                        amount
                    )
                )
                if (owedToMe) {
                    OutlinedTextField(
                        value = shownIban,
                        onValueChange = { iban = it },
                        label = { Text(stringResource(Res.string.settle_up_iban_label)) },
                        isError = ibanInvalid,
                        supportingText = if (ibanInvalid) {
                            { Text(stringResource(Res.string.settle_up_iban_invalid)) }
                        } else {
                            null
                        },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                if (send == SettleUpSend.IBAN_NOT_SAVED) {
                    Text(stringResource(Res.string.settle_up_iban_save_failed))
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { viewModel.send(pending, shownIban.takeIf { owedToMe }) },
                enabled = !ibanInvalid && send != SettleUpSend.SENDING
            ) {
                Text(stringResource(Res.string.settle_up_send))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(Res.string.settle_up_cancel)) }
        }
    )
}
