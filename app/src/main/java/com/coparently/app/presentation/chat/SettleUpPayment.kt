package com.coparently.app.presentation.chat

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.util.Log
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.coparently.app.R
import com.coparently.app.data.chat.ChatPartner
import com.coparently.app.data.chat.ChatPartnerSource
import com.coparently.app.data.payment.PaymentAccounts
import com.coparently.app.domain.activity.ActivityAnnouncement
import com.coparently.app.domain.activity.ActivityEntityType
import com.coparently.app.domain.activity.ActivityKind
import com.coparently.app.domain.family.FamilyKey
import com.coparently.app.domain.model.Message
import com.coparently.app.domain.payment.Iban
import com.coparently.app.domain.payment.PaymentQr
import com.coparently.app.domain.payment.PaymentRequest
import com.coparently.app.domain.payments.Payment
import com.coparently.app.domain.payments.PaymentKind
import com.coparently.app.domain.payments.PaymentState
import com.coparently.app.presentation.common.LocalAppMessages
import com.coparently.app.presentation.common.ParentNames
import com.coparently.app.presentation.common.copySensitive
import com.coparently.app.presentation.expenses.currencyFormat
import com.coparently.app.presentation.payments.PaymentsViewModel
import com.coparently.app.presentation.theme.Spacing
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.common.BitMatrix
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import java.io.File
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale
import javax.inject.Inject

/**
 * How a settle-up card shows the payer what to pay (tester feedback, October 2026: "a QR code for
 * the amount at Settle up"), or null where no thread provides it — a preview, a screenshot test.
 *
 * A CompositionLocal rather than a parameter, for [LocalChatAttachments]' reason: the detekt
 * baseline keys entries on `MessageItem`'s signature.
 */
val LocalSettleUpPayment = compositionLocalOf<(@Composable (Message, Boolean) -> Unit)?> { null }

/**
 * The account the co-parent of the thread on screen receives payments on.
 *
 * @property uid The co-parent.
 * @property iban Their IBAN, or null when they have added none.
 */
data class PayeeAccount(val uid: String, val iban: String?)

/** Follows the co-parent of the family on screen and the IBAN they receive payments on. */
@HiltViewModel
class SettleUpPaymentViewModel @Inject constructor(
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

/**
 * Provides [LocalSettleUpPayment] for [content], and [LocalOpenSupport]: where a child support
 * card leads (25 October 2026).
 */
@Composable
fun SettleUpPaymentHost(
    parentNames: ParentNames,
    onOpenSupport: (() -> Unit)? = null,
    viewModel: SettleUpPaymentViewModel = hiltViewModel(),
    paymentsViewModel: PaymentsViewModel = hiltViewModel(),
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
        else -> runCatching { YearMonth.parse(card.title) }.getOrNull()?.let { month ->
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
            Text(stringResource(R.string.payment_money_arrived))
        }
        PaymentState.PAID_BY_ME -> PaymentNote(R.string.payment_card_waiting)
        PaymentState.CONFIRMED -> PaymentNote(R.string.payment_card_received)
        PaymentState.NONE -> Unit
    }
}

@Composable
private fun PaymentNote(text: Int) {
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
        card.whenIso?.let { iso -> runCatching { LocalDate.parse(iso) }.getOrNull() }
            ?.format(DateTimeFormatter.ofPattern("LLLL yyyy", Locale.getDefault()))
            ?: card.title
    }
    return stringResource(ActivityCardText.settleUpResource(card.kind, isCurrentUser), month, amountText(card))
}

private fun amountText(card: ActivityAnnouncement): String {
    val amount = card.amount?.toBigDecimalOrNull() ?: return card.amount.orEmpty()
    val currency = card.currency ?: return amount.toPlainString()
    return currencyFormat(currency).format(amount)
}

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
    val amount = card.amount?.toBigDecimalOrNull()
    val currency = card.currency
    if (payee == null || amount == null || currency == null) return
    // Once the payer said they paid, the code is not offered again (25 October 2026).
    val recorded = steps.forCard(card)
    if (recorded != null) {
        PaymentNote(
            if (recorded.confirmed) R.string.payment_card_received else R.string.payment_card_waiting
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
            Text(stringResource(R.string.settle_up_no_account, name), style = MaterialTheme.typography.bodyMedium)
            return@Column
        }
        val shown = amountText(card)
        Text(stringResource(R.string.settle_up_pay_title, shown, name), style = MaterialTheme.typography.titleSmall)
        val request = PaymentRequest(iban, amount, currency, name, "CoPlanly ${card.title}")
        val qr = remember(request) { PaymentQr.encode(request)?.let(::qrMatrix) }
        if (qr != null) {
            QrCode(qr, stringResource(R.string.settle_up_qr_description, shown, name))
            Text(stringResource(R.string.settle_up_pay_hint), style = MaterialTheme.typography.bodySmall)
        }
        Text(Iban.grouped(iban), style = MaterialTheme.typography.bodyMedium)
        PaymentActions(iban, qr)
        // Said by the payer once the money is on its way; the co-parent then confirms it arrived.
        val month = runCatching { YearMonth.parse(card.title) }.getOrNull()
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
                Text(stringResource(R.string.payment_i_paid))
            }
        }
    }
}

/** Copy the IBAN, and share the code as an image when there is one. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PaymentActions(iban: String, qr: BitMatrix?) {
    val context = LocalContext.current
    val appMessages = LocalAppMessages.current
    val copied = stringResource(R.string.settle_up_iban_copied)
    val chooser = stringResource(R.string.settle_up_share_chooser)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.S)) {
        TextButton(onClick = {
            // Marked sensitive, as the invite code is: a bank account is not for the clipboard's
            // preview or a keyboard's suggestions (security audit, October 2026).
            copySensitive(context, "IBAN", iban)
            appMessages?.show(copied)
        }) {
            Text(stringResource(R.string.settle_up_copy_iban))
        }
        if (qr != null) {
            TextButton(onClick = { shareQr(context, qr, chooser) }) {
                Text(stringResource(R.string.settle_up_share_qr))
            }
        }
    }
}

/**
 * The code, black on white in both themes: a bank's scanner reads dark modules on a light
 * ground, so these two colours are the one place the theme's roles do not apply.
 */
@Composable
private fun QrCode(matrix: BitMatrix, description: String) {
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

/** [content] as a QR matrix of one cell per module, error correction M as all three standards ask. */
private fun qrMatrix(content: String): BitMatrix? = runCatching {
    QRCodeWriter().encode(
        content,
        BarcodeFormat.QR_CODE,
        0,
        0,
        mapOf(EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.M, EncodeHintType.MARGIN to 0)
    )
}.onFailure { Log.w(TAG, "The payment code could not be drawn", it) }.getOrNull()

/** Writes the code to the cache as a PNG and opens the share sheet on it. */
private fun shareQr(context: Context, matrix: BitMatrix, chooserTitle: String) {
    runCatching {
        val scale = SHARE_PIXELS / (matrix.width + 2 * SHARE_QUIET_MODULES)
        val side = (matrix.width + 2 * SHARE_QUIET_MODULES) * scale
        val bitmap = Bitmap.createBitmap(side, side, Bitmap.Config.RGB_565)
        bitmap.eraseColor(android.graphics.Color.WHITE)
        for (y in 0 until matrix.height) {
            for (x in 0 until matrix.width) {
                if (!matrix[x, y]) continue
                val left = (x + SHARE_QUIET_MODULES) * scale
                val top = (y + SHARE_QUIET_MODULES) * scale
                for (dy in 0 until scale) {
                    for (dx in 0 until scale) bitmap.setPixel(left + dx, top + dy, android.graphics.Color.BLACK)
                }
            }
        }
        val dir = File(context.cacheDir, "payment_codes").apply { mkdirs() }
        val file = File(dir, "settle-up.png")
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, PNG_QUALITY, it) }
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "image/png"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(send, chooserTitle))
    }.onFailure { Log.w(TAG, "The payment code could not be shared", it) }
}

private const val TAG = "SettleUpPayment"
private val BLOCK_MAX_WIDTH = 300.dp
private val QR_SIZE = 200.dp
private val QR_QUIET_ZONE = 12.dp
private const val SHARE_PIXELS = 800
private const val SHARE_QUIET_MODULES = 4
private const val PNG_QUALITY = 100
