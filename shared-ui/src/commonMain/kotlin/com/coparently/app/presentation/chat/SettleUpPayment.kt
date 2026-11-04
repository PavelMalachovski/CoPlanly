package com.coparently.app.presentation.chat

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.coparently.app.domain.activity.ActivityAnnouncement
import com.coparently.app.domain.activity.ActivityEntityType
import com.coparently.app.domain.activity.ActivityKind
import com.coparently.app.domain.family.FamilyKey
import com.coparently.app.domain.model.Message
import com.coparently.app.domain.money.DecimalText
import com.coparently.app.domain.payment.Iban
import com.coparently.app.domain.payment.PaymentQr
import com.coparently.app.domain.payment.PaymentRequest
import com.coparently.app.domain.payments.Payment
import com.coparently.app.domain.payments.PaymentKind
import com.coparently.app.domain.payments.PaymentState
import com.coparently.app.domain.time.IsoText
import com.coparently.app.presentation.common.LocalAppMessages
import com.coparently.app.presentation.common.ParentNames
import com.coparently.app.presentation.payments.PaymentsViewModel
import com.coparently.app.presentation.platform.LocalQrCodes
import com.coparently.app.presentation.platform.LocalSensitiveClipboard
import com.coparently.app.presentation.platform.QrMatrix
import com.coparently.app.presentation.theme.Spacing
import com.coparently.app.resources.Res
import com.coparently.app.resources.payment_card_received
import com.coparently.app.resources.payment_card_waiting
import com.coparently.app.resources.payment_i_paid
import com.coparently.app.resources.payment_money_arrived
import com.coparently.app.resources.settle_up_copy_iban
import com.coparently.app.resources.settle_up_iban_copied
import com.coparently.app.resources.settle_up_no_account
import com.coparently.app.resources.settle_up_pay_hint
import com.coparently.app.resources.settle_up_pay_title
import com.coparently.app.resources.settle_up_qr_description
import com.coparently.app.resources.settle_up_share_chooser
import com.coparently.app.resources.settle_up_share_qr
import com.coparently.app.utils.ReaderDateText
import com.coparently.app.utils.ReaderMoneyText
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

/**
 * How a settle-up card shows the payer what to pay (tester feedback, October 2026: "a QR code for
 * the amount at Settle up"), or null where no thread provides it — a preview, a screenshot test.
 *
 * A CompositionLocal rather than a parameter, for [LocalChatAttachments]' reason: the detekt
 * baseline keys entries on `MessageItem`'s signature.
 */
val LocalSettleUpPayment = compositionLocalOf<(@Composable (Message, Boolean) -> Unit)?> { null }

/**
 * Provides [LocalSettleUpPayment] for [content], and [LocalOpenSupport]: where a child support
 * card leads (25 October 2026).
 */
@Composable
fun SettleUpPaymentHost(
    parentNames: ParentNames,
    onOpenSupport: (() -> Unit)? = null,
    viewModel: SettleUpPaymentViewModel = koinViewModel(),
    paymentsViewModel: PaymentsViewModel = koinViewModel(),
    content: @Composable () -> Unit
) {
    val payee by viewModel.payee.collectAsState()
    val payments by paymentsViewModel.payments.collectAsState()
    val busy by paymentsViewModel.busy.collectAsState()
    val steps = PaymentSteps(payments, paymentsViewModel.myUid, enabled = !busy, paymentsViewModel)
    CompositionLocalProvider(
        LocalOpenSupport provides onOpenSupport,
        LocalSettleUpPayment provides { message, mine ->
            if (message.activity?.kind?.isPayment == true) {
                PaymentCardStep(message, steps)
            } else {
                SettleUpPaymentBlock(message, mine, payee, parentNames, steps)
            }
        },
        content = content
    )
}

/**
 * What the chat knows of the family's payments, for the step under a card: "I paid" under a
 * settle-up the reader pays, "Money arrived" under a payment made to the reader (25 October 2026).
 */
private class PaymentSteps(
    val payments: List<Payment>,
    val myUid: String?,
    val enabled: Boolean,
    val viewModel: PaymentsViewModel
) {
    /** The payment a card names: by its id for a payment card, by month and currency otherwise. */
    fun forCard(card: ActivityAnnouncement): Payment? = when {
        card.entityType == ActivityEntityType.PAYMENT ->
            payments.firstOrNull { it.id == card.entityId }
        else -> runCatching { IsoText.parseYearMonth(card.title) }.getOrNull()?.let { month ->
            card.currency?.let { viewModel.paymentFor(payments, PaymentKind.EXPENSES, month, it) }
        }
    }
}

/** Under a "paid" card on the payee's phone: "Money arrived", or that it was received. */
@Composable
private fun PaymentCardStep(message: Message, steps: PaymentSteps) {
    val card = message.activity ?: return
    if (card.kind != ActivityKind.SETTLE_UP_PAID && card.kind != ActivityKind.SUPPORT_PAID) return
    val payment = steps.forCard(card) ?: return
    when (PaymentState.of(payment, steps.myUid)) {
        PaymentState.PAID_TO_ME -> TextButton(onClick = { steps.viewModel.confirm(payment) }, enabled = steps.enabled) {
            Text(stringResource(Res.string.payment_money_arrived))
        }
        PaymentState.PAID_BY_ME -> PaymentNote(Res.string.payment_card_waiting)
        PaymentState.CONFIRMED -> PaymentNote(Res.string.payment_card_received)
        PaymentState.NONE -> Unit
    }
}

@Composable
private fun PaymentNote(text: StringResource) {
    Text(
        text = stringResource(text),
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = Spacing.XS)
    )
}

/**
 * A settle-up card's sentence on this phone — "you owe" or "you are owed", by who reads it — with
 * the month and the amount in the reader's language.
 */
@Composable
internal fun settleUpCardText(card: ActivityAnnouncement, isCurrentUser: Boolean): String {
    val month = remember(card.whenIso) {
        card.whenIso?.let { iso -> runCatching { IsoText.parseDate(iso) }.getOrNull() }
            ?.let { ReaderDateText.pattern(it, "LLLL yyyy") }
            ?: card.title
    }
    return stringResource(ActivityCardText.settleUpResource(card.kind, isCurrentUser), month, amountText(card))
}

private fun amountText(card: ActivityAnnouncement): String =
    card.amount?.let { ReaderMoneyText.amount(it, card.currency) } ?: card.amount.orEmpty()

/**
 * Under a settle-up card, on the payer's phone only: the payment code for the receiving parent's
 * account, the IBAN to copy, and a way to hand the code to the bank's app — which cannot scan the
 * screen it is shown on. Without an account it says so; on the receiving parent's phone it is
 * nothing.
 */
@Composable
private fun SettleUpPaymentBlock(
    message: Message,
    mine: Boolean,
    payee: PayeeAccount?,
    parentNames: ParentNames,
    steps: PaymentSteps
) {
    val card = message.activity?.takeIf { it.kind.readerPays(mine) } ?: return
    // In minor units, as `MinorUnits.fromText` reads it (the app's, which delegates to this).
    val amount = card.amount?.let { DecimalText.parseHalfUp(it, PaymentQr.MONEY_SCALE) }
    val currency = card.currency
    if (payee == null || amount == null || currency == null) return
    // Once the payer said they paid, the code is not offered again (25 October 2026).
    val recorded = steps.forCard(card)
    if (recorded != null) {
        PaymentNote(
            if (recorded.confirmed) Res.string.payment_card_received else Res.string.payment_card_waiting
        )
        return
    }
    val name = parentNames.labelForUid(payee.uid)
    Column(
        modifier = Modifier
            .widthIn(max = BLOCK_MAX_WIDTH)
            .padding(top = Spacing.XS)
            .background(MaterialTheme.colorScheme.surfaceContainer, MaterialTheme.shapes.medium)
            .padding(Spacing.M),
        verticalArrangement = Arrangement.spacedBy(Spacing.S)
    ) {
        val iban = payee.iban
        if (iban == null) {
            Text(stringResource(Res.string.settle_up_no_account, name), style = MaterialTheme.typography.bodyMedium)
            return@Column
        }
        val shown = amountText(card)
        Text(stringResource(Res.string.settle_up_pay_title, shown, name), style = MaterialTheme.typography.titleSmall)
        val request = PaymentRequest(iban, amount, currency, name, "CoPlanly ${card.title}")
        val qrCodes = LocalQrCodes.current
        val qr = remember(request, qrCodes) { PaymentQr.encode(request)?.let(qrCodes::encode) }
        if (qr != null) {
            QrCode(qr, stringResource(Res.string.settle_up_qr_description, shown, name))
            Text(stringResource(Res.string.settle_up_pay_hint), style = MaterialTheme.typography.bodySmall)
        }
        Text(Iban.grouped(iban), style = MaterialTheme.typography.bodyMedium)
        PaymentActions(iban, qr)
        // Said by the payer once the money is on its way; the co-parent then confirms it arrived.
        val month = runCatching { IsoText.parseYearMonth(card.title) }.getOrNull()
        if (month != null) {
            TextButton(
                onClick = {
                    steps.viewModel.markPaid(
                        FamilyKey.of(payee.uid, steps.myUid.orEmpty()),
                        PaymentKind.EXPENSES,
                        month,
                        currency,
                        amount
                    )
                },
                enabled = steps.enabled && !steps.myUid.isNullOrBlank()
            ) {
                Text(stringResource(Res.string.payment_i_paid))
            }
        }
    }
}

/**
 * Copy the IBAN, and share the code as an image when there is one; each only where the phone can
 * (`SensitiveClipboard.available`, and a code only where `QrCodes` drew one — the iPhone until
 * phase 3 has neither, and the IBAN is read off the screen there).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PaymentActions(iban: String, qr: QrMatrix?) {
    val clipboard = LocalSensitiveClipboard.current
    val qrCodes = LocalQrCodes.current
    val appMessages = LocalAppMessages.current
    val copied = stringResource(Res.string.settle_up_iban_copied)
    val chooser = stringResource(Res.string.settle_up_share_chooser)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.S)) {
        if (clipboard.available) {
            TextButton(onClick = {
                // Marked sensitive, as the invite code is: a bank account is not for the clipboard's
                // preview or a keyboard's suggestions (security audit, October 2026).
                clipboard.copySensitive("IBAN", iban)
                appMessages?.show(copied)
            }) {
                Text(stringResource(Res.string.settle_up_copy_iban))
            }
        }
        if (qr != null) {
            TextButton(onClick = { qrCodes.shareImage(qr, chooser) }) {
                Text(stringResource(Res.string.settle_up_share_qr))
            }
        }
    }
}

/**
 * The code, black on white in both themes: a bank's scanner reads dark modules on a light
 * ground, so these two colours are the one place the theme's roles do not apply.
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

private val BLOCK_MAX_WIDTH = 300.dp
private val QR_SIZE = 200.dp
private val QR_QUIET_ZONE = 12.dp
