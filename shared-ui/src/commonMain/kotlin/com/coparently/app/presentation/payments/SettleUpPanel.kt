package com.coparently.app.presentation.payments

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.coparently.app.domain.money.DecimalText
import com.coparently.app.domain.payment.Iban
import com.coparently.app.domain.payment.PaymentQr
import com.coparently.app.domain.payment.PaymentRequest
import com.coparently.app.domain.payments.Payment
import com.coparently.app.domain.time.IsoText
import com.coparently.app.presentation.common.LocalAppMessages
import com.coparently.app.presentation.common.PillChip
import com.coparently.app.presentation.common.stacksAtLargeFont
import com.coparently.app.presentation.platform.LocalQrCodes
import com.coparently.app.presentation.platform.LocalSensitiveClipboard
import com.coparently.app.presentation.platform.LocalShareSheet
import com.coparently.app.presentation.platform.QrMatrix
import com.coparently.app.presentation.theme.Spacing
import com.coparently.app.presentation.theme.titleSmallEmphasized
import com.coparently.app.resources.Res
import com.coparently.app.resources.payment_chip_settled
import com.coparently.app.resources.payment_chip_waiting_for
import com.coparently.app.resources.payment_chip_waiting_for_you
import com.coparently.app.resources.payment_i_paid
import com.coparently.app.resources.payment_money_arrived
import com.coparently.app.resources.payment_paid_to_you_named
import com.coparently.app.resources.payment_panel_ask
import com.coparently.app.resources.payment_panel_check_bank
import com.coparently.app.resources.payment_panel_not_settled
import com.coparently.app.resources.payment_panel_paid_on
import com.coparently.app.resources.payment_panel_pay
import com.coparently.app.resources.payment_panel_says_paid
import com.coparently.app.resources.payment_panel_settled
import com.coparently.app.resources.payment_panel_show_code
import com.coparently.app.resources.payment_waiting_named
import com.coparently.app.resources.settle_up_copy_iban
import com.coparently.app.resources.settle_up_iban_copied
import com.coparently.app.resources.settle_up_no_account
import com.coparently.app.resources.settle_up_pay_hint
import com.coparently.app.resources.settle_up_qr_description
import com.coparently.app.resources.settle_up_share_chooser
import com.coparently.app.resources.settle_up_share_qr
import com.coparently.app.utils.ReaderDateText
import com.coparently.app.utils.ReaderMoneyText
import com.coparently.app.utils.unbreakableDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import org.jetbrains.compose.resources.stringResource
import kotlin.time.Instant

/**
 * What this parent can do about a month's settle-up, under its balance on the Home Money card and
 * the Expenses header (redesign, November 2026: settle up leaves the chat for Money; board
 * MoneySettleUp). One anatomy for the five [SettleUpPanelState]s: a status chip where one parent
 * waits on the other, a sentence, and at most the step that is this parent's.
 *
 * The status is said in words and the chip's colour repeats it, in the theme's roles and never a
 * parent's hue: waiting for you is `primary` filled, waiting for the co-parent outlined, settled
 * `tertiaryContainer`. Every sentence wraps; an amount is never cut off (design refresh item 15),
 * and the buttons sit in a [FlowRow], so they stack once the text grows.
 *
 * The payment code is made on the phone and nothing goes to a bank (item 45): Czech QR Platba,
 * Slovak PAY by square or the EPC code, by the payee's IBAN ([PaymentQr]), drawn through the
 * platform's [com.coparently.app.presentation.platform.QrCodes].
 *
 * @param state Where the month stands for this parent
 * @param coParentName The co-parent's name, never a role
 * @param payeeIban The account the co-parent is paid on, or null when they have added none; read
 *   only where this parent pays
 * @param actions What the buttons do
 */
@Composable
fun SettleUpPanel(
    state: SettleUpPanelState,
    coParentName: String,
    payeeIban: String?,
    actions: SettleUpPanelActions,
    modifier: Modifier = Modifier
) {
    val amount = panelAmount(state)
    val month = remember(state.month) { ReaderDateText.pattern(state.month, "LLLL") }
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(Spacing.S)
    ) {
        when (state) {
            is SettleUpPanelState.AskToSettle -> {
                Note(stringResource(Res.string.payment_panel_not_settled, month))
                Button(onClick = actions.onAskToSettle, enabled = actions.enabled) {
                    ButtonLabel(stringResource(Res.string.payment_panel_ask, coParentName))
                }
            }
            is SettleUpPanelState.Pay -> {
                StatusChip(PanelStatus.WAITING_FOR_YOU, coParentName)
                Title(stringResource(Res.string.payment_panel_pay, coParentName, amount, month))
                PaymentCode(state, coParentName, payeeIban, onMarkPaid = actions.onMarkPaid, enabled = actions.enabled)
            }
            is SettleUpPanelState.WaitingForConfirm -> WaitingForConfirm(state, amount, coParentName, payeeIban)
            is SettleUpPanelState.Confirm -> {
                StatusChip(PanelStatus.WAITING_FOR_YOU, coParentName)
                val paidOn = paidOnText(state.paidAtMillis)
                Title(
                    withoutDoubledFullStop(
                        paidOn?.let { stringResource(Res.string.payment_panel_says_paid, coParentName, amount, it) }
                            ?: stringResource(Res.string.payment_paid_to_you_named, coParentName, amount)
                    )
                )
                Note(stringResource(Res.string.payment_panel_check_bank))
                Button(onClick = { actions.onConfirm(state.payment) }, enabled = actions.enabled) {
                    ButtonLabel(stringResource(Res.string.payment_money_arrived))
                }
            }
            is SettleUpPanelState.Settled -> {
                StatusChip(PanelStatus.SETTLED, coParentName)
                Title(stringResource(Res.string.payment_panel_settled, month, amount))
            }
        }
    }
}

/**
 * What [SettleUpPanel]'s buttons do.
 *
 * @property onAskToSettle Opens the settle-up question, which posts the card for the record
 * @property onMarkPaid Records that this parent paid the month's amount
 * @property onConfirm Records that the co-parent's payment arrived
 * @property enabled False while a write is on its way, so a second tap waits for it
 */
class SettleUpPanelActions(
    val onAskToSettle: () -> Unit,
    val onMarkPaid: () -> Unit,
    val onConfirm: (Payment) -> Unit,
    val enabled: Boolean = true
)

/** Who a settle-up waits on, as its chip says it. */
private enum class PanelStatus { WAITING_FOR_YOU, WAITING_FOR_THEM, SETTLED }

@Composable
private fun StatusChip(status: PanelStatus, coParentName: String) {
    val colors = MaterialTheme.colorScheme
    when (status) {
        PanelStatus.WAITING_FOR_YOU -> PillChip(
            label = stringResource(Res.string.payment_chip_waiting_for_you),
            container = colors.primary,
            contentColor = colors.onPrimary
        )
        // No container: PillChip draws the outline itself.
        PanelStatus.WAITING_FOR_THEM -> PillChip(
            label = stringResource(Res.string.payment_chip_waiting_for, coParentName),
            contentColor = colors.onSurfaceVariant
        )
        PanelStatus.SETTLED -> PillChip(
            label = stringResource(Res.string.payment_chip_settled),
            container = colors.tertiaryContainer,
            contentColor = colors.onTertiaryContainer
        )
    }
}

/** The payer has said they paid: waiting for the co-parent, and the code once more on request. */
@Composable
private fun WaitingForConfirm(
    state: SettleUpPanelState.WaitingForConfirm,
    amount: String,
    coParentName: String,
    payeeIban: String?
) {
    StatusChip(PanelStatus.WAITING_FOR_THEM, coParentName)
    val paidOn = paidOnText(state.paidAtMillis)
    Title(
        withoutDoubledFullStop(
            paidOn?.let { stringResource(Res.string.payment_panel_paid_on, amount, it, coParentName) }
                ?: stringResource(Res.string.payment_waiting_named, amount, coParentName)
        )
    )
    // The code is not offered again unasked (25 October 2026), but a payer whose bank refused the
    // first transfer still needs it.
    var showCode by rememberSaveable { mutableStateOf(false) }
    if (showCode) {
        PaymentCode(state, coParentName, payeeIban, onMarkPaid = null, enabled = false)
    } else if (payeeIban != null) {
        TextButton(onClick = { showCode = true }) {
            Text(stringResource(Res.string.payment_panel_show_code))
        }
    }
}

/**
 * The payment code for the co-parent's account, the IBAN to copy, a way to hand the code to the
 * bank's app — which cannot scan the screen it is shown on — and "I paid". Without an account it
 * says so and still offers "I paid": a parent may pay by other means.
 *
 * @param onMarkPaid Records the payment, or null where it is already recorded
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PaymentCode(
    state: SettleUpPanelState,
    coParentName: String,
    payeeIban: String?,
    onMarkPaid: (() -> Unit)?,
    enabled: Boolean
) {
    val amount = panelAmount(state)
    val qrCodes = LocalQrCodes.current
    val request = payeeIban?.let { iban ->
        PaymentRequest(
            iban = iban,
            amountMinor = state.amountMinor,
            currency = state.currency,
            beneficiary = coParentName,
            note = "CoPlanly ${IsoText.yearMonth(state.month)}"
        )
    }
    val qr = remember(request, qrCodes) { request?.let(PaymentQr::encode)?.let(qrCodes::encode) }
    if (payeeIban == null) {
        Note(stringResource(Res.string.settle_up_no_account, coParentName))
    } else {
        if (qr != null) {
            Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                QrCode(qr, stringResource(Res.string.settle_up_qr_description, amount, coParentName))
            }
            Note(stringResource(Res.string.settle_up_pay_hint))
        }
        IbanRow(payeeIban)
    }
    val shareSheet = LocalShareSheet.current
    val chooser = stringResource(Res.string.settle_up_share_chooser)
    val canShare = qr != null && shareSheet.available
    if (!canShare && onMarkPaid == null) return
    val buttons = buildList<@Composable (Modifier) -> Unit> {
        if (canShare && qr != null) {
            add { sized ->
                OutlinedButton(onClick = { qrCodes.shareImage(qr, chooser) }, modifier = sized) {
                    ButtonLabel(stringResource(Res.string.settle_up_share_qr))
                }
            }
        }
        if (onMarkPaid != null) {
            add { sized ->
                Button(onClick = onMarkPaid, enabled = enabled, modifier = sized) {
                    ButtonLabel(stringResource(Res.string.payment_i_paid))
                }
            }
        }
    }
    PaymentButtons(buttons)
}

/**
 * Share and "I paid" at the row's end, or, from font scale 1.3 ([stacksAtLargeFont]), one above
 * the other at full width (text review, November 2026), so a German or Ukrainian label at 150 %
 * neither breaks inside a word nor pushes "I paid" away. A button is never left out; the panel
 * only changes where it sits.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PaymentButtons(buttons: List<@Composable (Modifier) -> Unit>) {
    if (stacksAtLargeFont()) {
        Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Spacing.S)) {
            buttons.forEach { button -> button(Modifier.fillMaxWidth()) }
        }
    } else {
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(Spacing.S, Alignment.End),
            verticalArrangement = Arrangement.spacedBy(Spacing.S)
        ) {
            buttons.forEach { button -> button(Modifier) }
        }
    }
}

/** A button's label, centred when it wraps inside a button wider than its lines. */
@Composable
private fun ButtonLabel(text: String) {
    Text(text = text, textAlign = TextAlign.Center)
}

/**
 * The IBAN in groups of four, and Copy where the phone has a clipboard — marked sensitive, as the
 * invite code is: a bank account is not for the clipboard's preview or a keyboard's suggestions
 * (security audit, October 2026).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun IbanRow(iban: String) {
    val clipboard = LocalSensitiveClipboard.current
    val appMessages = LocalAppMessages.current
    val copied = stringResource(Res.string.settle_up_iban_copied)
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(Spacing.S),
        itemVerticalAlignment = Alignment.CenterVertically
    ) {
        Text(ibanText(Iban.grouped(iban)), style = MaterialTheme.typography.bodyMedium)
        if (clipboard.available) {
            TextButton(onClick = {
                clipboard.copySensitive("IBAN", iban)
                appMessages?.show(copied)
            }) {
                Text(stringResource(Res.string.settle_up_copy_iban))
            }
        }
    }
}

/**
 * [grouped] — an IBAN in groups of four — with its groups held together: on one line where it
 * fits, else in two even halves ("CZ65 0800 0000" over "1920 0014 5399"), the one break being
 * between them, so a group is never left alone on a line (text review, November 2026).
 */
internal fun ibanText(grouped: String): String {
    val groups = grouped.split(' ').filter { it.isNotEmpty() }
    val firstHalf = (groups.size + 1) / 2
    val halves = listOf(groups.take(firstHalf), groups.drop(firstHalf)).filter { it.isNotEmpty() }
    return halves.joinToString(" ") { half -> half.joinToString(NO_BREAK_SPACE) }
}

/**
 * The code, black on white in both themes: a bank's scanner reads dark modules on a light ground,
 * so these two colours are the one place the theme's roles do not apply.
 */
@Composable
private fun QrCode(matrix: QrMatrix, description: String) {
    Canvas(
        modifier = Modifier
            .size(QR_SIZE)
            .background(Color.White)
            .padding(QR_QUIET_ZONE)
            .semantics { contentDescription = description }
    ) {
        val cell = size.minDimension / matrix.width
        for (y in 0 until matrix.height) {
            for (x in 0 until matrix.width) {
                if (matrix[x, y]) drawRect(Color.Black, Offset(x * cell, y * cell), Size(cell, cell))
            }
        }
    }
}

@Composable
private fun Title(text: String) {
    Text(text = text, style = MaterialTheme.typography.titleSmallEmphasized)
}

/**
 * [text] without the doubled full stop a date abbreviated with its own ("22. 5.", "22 трав.")
 * leaves where a sentence ends on it (text review, November 2026: "dne 22. 5..").
 */
internal fun withoutDoubledFullStop(text: String): String = text.replace("..", ".")

@Composable
private fun Note(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}

/** [state]'s amount in the reader's conventions, in its own currency. */
private fun panelAmount(state: SettleUpPanelState): String {
    val plain = DecimalText.plain(state.amountMinor, PaymentQr.MONEY_SCALE)
    return ReaderMoneyText.amount(plain, state.currency) ?: plain
}

/** The day [paidAtMillis] fell on, on this phone's calendar ("22 Nov"), or null while unknown. */
private fun paidOnText(paidAtMillis: Long?): String? = paidAtMillis?.let { millis ->
    val day = Instant.fromEpochMilliseconds(millis).toLocalDateTime(TimeZone.currentSystemDefault()).date
    unbreakableDate(ReaderDateText.date(day, "dMMM"))
}

private const val NO_BREAK_SPACE = "\u00A0"

private val QR_SIZE = 200.dp
private val QR_QUIET_ZONE = 12.dp
