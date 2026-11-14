package com.coparently.app.presentation.support

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
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
import androidx.compose.ui.text.input.KeyboardType
import com.coparently.app.domain.family.FamilyKey
import com.coparently.app.domain.money.DecimalText
import com.coparently.app.domain.money.SupportedCurrency
import com.coparently.app.domain.payment.PaymentQr
import com.coparently.app.domain.payments.Payment
import com.coparently.app.domain.payments.PaymentState
import com.coparently.app.domain.support.SupportAgreement
import com.coparently.app.domain.support.SupportReminderRule
import com.coparently.app.domain.support.SupportTerms
import com.coparently.app.presentation.common.BannerTone
import com.coparently.app.presentation.common.EmptyState
import com.coparently.app.presentation.common.GroupLabel
import com.coparently.app.presentation.common.InlineBanner
import com.coparently.app.presentation.common.ParentNames
import com.coparently.app.presentation.common.PushedTopBar
import com.coparently.app.presentation.common.SectionGroup
import com.coparently.app.presentation.common.SectionRow
import com.coparently.app.presentation.common.asString
import com.coparently.app.presentation.common.rememberParentNames
import com.coparently.app.presentation.common.rememberUiTextResolver
import com.coparently.app.presentation.theme.ScreenContainerColor
import com.coparently.app.presentation.theme.Spacing
import com.coparently.app.resources.Res
import com.coparently.app.resources.common_back
import com.coparently.app.resources.payment_card_received
import com.coparently.app.resources.payment_card_waiting
import com.coparently.app.resources.payment_i_paid
import com.coparently.app.resources.payment_money_arrived
import com.coparently.app.resources.support_accept
import com.coparently.app.resources.support_amount_line
import com.coparently.app.resources.support_asks_title
import com.coparently.app.resources.support_decline
import com.coparently.app.resources.support_dialog_amount
import com.coparently.app.resources.support_dialog_cancel
import com.coparently.app.resources.support_dialog_currency
import com.coparently.app.resources.support_dialog_day
import com.coparently.app.resources.support_dialog_day_hint
import com.coparently.app.resources.support_dialog_note
import com.coparently.app.resources.support_dialog_payer
import com.coparently.app.resources.support_dialog_send
import com.coparently.app.resources.support_dialog_title
import com.coparently.app.resources.support_month_due
import com.coparently.app.resources.support_months
import com.coparently.app.resources.support_no_family_description
import com.coparently.app.resources.support_no_family_title
import com.coparently.app.resources.support_none_description
import com.coparently.app.resources.support_none_title
import com.coparently.app.resources.support_proposal_body
import com.coparently.app.resources.support_propose
import com.coparently.app.resources.support_propose_change
import com.coparently.app.resources.support_reminder_days_before
import com.coparently.app.resources.support_reminder_on_the_day
import com.coparently.app.resources.support_reminder_row
import com.coparently.app.resources.support_reminder_row_supporting
import com.coparently.app.resources.support_terms_line
import com.coparently.app.resources.support_title
import com.coparently.app.resources.support_unavailable
import com.coparently.app.resources.support_waiting_title
import com.coparently.app.resources.support_withdraw
import com.coparently.app.utils.ReaderDateText
import kotlinx.datetime.TimeZone
import kotlinx.datetime.YearMonth
import kotlinx.datetime.todayIn
import kotlinx.datetime.yearMonth
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import kotlin.time.Clock

/**
 * Family → Money → Child support (25 October 2026, owner decisions): the agreement, its waiting
 * proposal, the last months paid or not, and the payer's reminder.
 *
 * @param onNavigateUp Returns to the Family tab.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
@Suppress("LongMethod") // one scaffold, its four states and its dialog
fun SupportScreen(onNavigateUp: () -> Unit, viewModel: SupportViewModel = koinViewModel()) {
    val state by viewModel.state.collectAsState()
    val busy by viewModel.busy.collectAsState()
    val error by viewModel.error.collectAsState()
    val names = rememberParentNames(viewModel.parents.collectAsState().value)
    val defaultCurrency by viewModel.defaultCurrency.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val textResolver = rememberUiTextResolver()
    var proposing by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(error) {
        error?.let {
            snackbarHostState.showSnackbar(it.asString(textResolver))
            viewModel.errorShown()
        }
    }

    Scaffold(
        containerColor = ScreenContainerColor,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            PushedTopBar(
                title = stringResource(Res.string.support_title),
                backLabel = stringResource(Res.string.common_back),
                onBack = onNavigateUp
            )
        }
    ) { padding ->
        val bodyModifier = Modifier
            .fillMaxSize()
            .padding(padding)
        when (val shown = state) {
            SupportUiState.Loading -> Unit
            SupportUiState.NoFamily -> EmptyState(
                icon = Icons.Default.Payments,
                title = stringResource(Res.string.support_no_family_title),
                description = stringResource(Res.string.support_no_family_description),
                modifier = bodyModifier
            )
            is SupportUiState.Unavailable -> EmptyState(
                icon = Icons.Default.Payments,
                title = stringResource(Res.string.support_unavailable),
                modifier = bodyModifier
            )
            is SupportUiState.Loaded -> SupportBody(
                state = shown,
                myUid = viewModel.myUid,
                names = names,
                enabled = !busy,
                actions = SupportActions(
                    propose = { proposing = true },
                    accept = viewModel::accept,
                    clear = viewModel::clearProposal,
                    markPaid = { terms, month -> viewModel.markPaid(shown, terms, month) },
                    confirm = viewModel::confirm,
                    // Offered only where this phone runs the reminder's job (not the iPhone yet).
                    setReminder = { days: Int? -> viewModel.setReminder(shown.familyId, days) }
                        .takeIf { viewModel.remindersAvailable }
                ),
                modifier = bodyModifier
            )
        }
    }

    val loaded = state as? SupportUiState.Loaded
    if (proposing && loaded != null) {
        ProposeSupportDialog(
            current = loaded.agreement?.agreement,
            defaultCurrency = defaultCurrency,
            myUid = viewModel.myUid.orEmpty(),
            participants = loaded.agreement?.participants
                ?: FamilyKey.membersOf(loaded.familyId)?.toList().orEmpty(),
            names = names,
            onConfirm = { terms ->
                viewModel.propose(loaded, terms)
                proposing = false
            },
            onDismiss = { proposing = false }
        )
    }
}

/** What the body's buttons do. */
private class SupportActions(
    val propose: () -> Unit,
    val accept: (SupportAgreement) -> Unit,
    val clear: (SupportAgreement) -> Unit,
    val markPaid: (SupportTerms, YearMonth) -> Unit,
    val confirm: (Payment) -> Unit,
    /** Sets the payer's reminder; null where this phone cannot remind ([SupportViewModel.remindersAvailable]). */
    val setReminder: ((Int?) -> Unit)?
)

@Composable
@Suppress("LongParameterList", "LongMethod") // the agreement, its proposal, the months and the reminder, in order
private fun SupportBody(
    state: SupportUiState.Loaded,
    myUid: String?,
    names: ParentNames,
    enabled: Boolean,
    actions: SupportActions,
    modifier: Modifier
) {
    val agreement = state.agreement
    val terms = agreement?.agreement
    val proposal = agreement?.proposal
    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(Spacing.L),
        verticalArrangement = Arrangement.spacedBy(Spacing.M)
    ) {
        item(key = "agreement") {
            if (terms == null) {
                InlineBanner(
                    title = stringResource(Res.string.support_none_title),
                    text = stringResource(Res.string.support_none_description),
                    tone = BannerTone.INFO
                )
            } else {
                SectionGroup {
                    SectionRow(
                        title = stringResource(Res.string.support_amount_line, supportAmount(terms)),
                        icon = Icons.Default.Payments,
                        supporting = stringResource(
                            Res.string.support_terms_line,
                            terms.dayOfMonth,
                            names.labelForUid(terms.payerUid),
                            supportMonth(terms.fromMonth)
                        )
                    )
                }
            }
        }
        if (proposal != null && agreement != null) {
            item(key = "proposal") {
                val mine = proposal.proposedBy == myUid
                val text = stringResource(
                    Res.string.support_proposal_body,
                    supportAmount(proposal.terms),
                    proposal.terms.dayOfMonth,
                    names.labelForUid(proposal.terms.payerUid)
                )
                InlineBanner(
                    title = if (mine) {
                        stringResource(Res.string.support_waiting_title, names.labelForUid(names.parents.coParent?.uid))
                    } else {
                        stringResource(Res.string.support_asks_title, names.labelForUid(proposal.proposedBy))
                    },
                    text = text,
                    tone = if (mine) BannerTone.INFO else BannerTone.ATTENTION,
                    actions = {
                        if (mine) {
                            TextButton(onClick = { actions.clear(agreement) }, enabled = enabled) {
                                Text(stringResource(Res.string.support_withdraw))
                            }
                        } else {
                            TextButton(onClick = { actions.clear(agreement) }, enabled = enabled) {
                                Text(stringResource(Res.string.support_decline))
                            }
                            TextButton(onClick = { actions.accept(agreement) }, enabled = enabled) {
                                Text(stringResource(Res.string.support_accept))
                            }
                        }
                    }
                )
            }
        }
        // A new proposal only while none of the co-parent's waits: theirs is answered first.
        if (proposal == null || proposal.proposedBy == myUid) {
            item(key = "propose") {
                TextButton(onClick = actions.propose, enabled = enabled) {
                    Text(
                        stringResource(
                            if (terms == null) Res.string.support_propose else Res.string.support_propose_change
                        )
                    )
                }
            }
        }
        if (terms != null) {
            months(state, terms, myUid, enabled, actions)
            val setReminder = actions.setReminder
            if (terms.payerUid == myUid && setReminder != null) {
                item(key = "reminder") { ReminderGroup(state.reminderDays, setReminder) }
            }
        }
    }
}

private fun LazyListScope.months(
    state: SupportUiState.Loaded,
    terms: SupportTerms,
    myUid: String?,
    enabled: Boolean,
    actions: SupportActions
) {
    val months = terms.monthsUpTo(Clock.System.todayIn(TimeZone.currentSystemDefault()))
    if (months.isEmpty()) return
    item(key = "months") {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.S)) {
            GroupLabel(stringResource(Res.string.support_months))
            SectionGroup {
                months.forEachIndexed { index, month ->
                    if (index > 0) Divider()
                    val payment = state.paymentFor(month)
                    val paymentState = PaymentState.of(payment, myUid)
                    SectionRow(
                        title = supportMonth(month),
                        icon = if (paymentState == PaymentState.CONFIRMED) {
                            Icons.Default.CheckCircle
                        } else {
                            Icons.Default.Schedule
                        },
                        supporting = when (paymentState) {
                            PaymentState.CONFIRMED -> stringResource(Res.string.payment_card_received)
                            PaymentState.PAID_BY_ME, PaymentState.PAID_TO_ME ->
                                stringResource(Res.string.payment_card_waiting)
                            PaymentState.NONE -> stringResource(
                                Res.string.support_month_due,
                                ReaderDateText.pattern(terms.dueDate(month), "d MMMM")
                            )
                        },
                        trailing = {
                            when {
                                paymentState == PaymentState.NONE && terms.payerUid == myUid ->
                                    TextButton(onClick = { actions.markPaid(terms, month) }, enabled = enabled) {
                                        Text(stringResource(Res.string.payment_i_paid))
                                    }
                                paymentState == PaymentState.PAID_TO_ME && payment != null ->
                                    TextButton(onClick = { actions.confirm(payment) }, enabled = enabled) {
                                        Text(stringResource(Res.string.payment_money_arrived))
                                    }
                            }
                        },
                        stackTrailingAtLargeFont = true
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ReminderGroup(days: Int?, onChange: (Int?) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.S)) {
        SectionGroup {
            SectionRow(
                title = stringResource(Res.string.support_reminder_row),
                icon = Icons.Default.NotificationsActive,
                supporting = stringResource(Res.string.support_reminder_row_supporting),
                trailing = {
                    Switch(
                        checked = days != null,
                        onCheckedChange = { on -> onChange(if (on) SupportReminderRule.DAYS_BEFORE[1] else null) }
                    )
                }
            )
        }
        if (days != null) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.S)) {
                SupportReminderRule.DAYS_BEFORE.forEach { option ->
                    FilterChip(
                        selected = option == days,
                        onClick = { onChange(option) },
                        label = {
                            Text(
                                if (option == 0) {
                                    stringResource(Res.string.support_reminder_on_the_day)
                                } else {
                                    pluralStringResource(Res.plurals.support_reminder_days_before, option, option)
                                }
                            )
                        }
                    )
                }
            }
        }
    }
}

/**
 * Proposes the terms: the monthly amount, its currency, the day of the month and who pays, from
 * this month. Seeded from the agreement in force, so a change starts from what it changes.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
@Suppress("LongParameterList", "LongMethod") // four fields, each with what it needs to offer
private fun ProposeSupportDialog(
    current: SupportTerms?,
    defaultCurrency: String,
    myUid: String,
    participants: List<String>,
    names: ParentNames,
    onConfirm: (SupportTerms) -> Unit,
    onDismiss: () -> Unit
) {
    var amountText by rememberSaveable {
        mutableStateOf(current?.amountMinor?.let { DecimalText.plain(it, PaymentQr.MONEY_SCALE) }.orEmpty())
    }
    var currency by rememberSaveable { mutableStateOf(current?.currency ?: defaultCurrency) }
    var dayText by rememberSaveable { mutableStateOf((current?.dayOfMonth ?: DEFAULT_DAY).toString()) }
    var payer by rememberSaveable {
        mutableStateOf(current?.payerUid ?: participants.firstOrNull { it != myUid } ?: myUid)
    }
    val amount = SupportViewModel.parseAmount(amountText)
    val day = dayText.toIntOrNull()?.takeIf { it in 1..SupportTerms.MAX_DAY }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(Res.string.support_dialog_title)) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(Spacing.M)
            ) {
                OutlinedTextField(
                    value = amountText,
                    onValueChange = { amountText = it },
                    label = { Text(stringResource(Res.string.support_dialog_amount)) },
                    isError = amountText.isNotBlank() && amount == null,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Text(stringResource(Res.string.support_dialog_currency), style = MaterialTheme.typography.labelLarge)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.S)) {
                    SupportedCurrency.entries.forEach { option ->
                        FilterChip(
                            selected = option.code == currency,
                            onClick = { currency = option.code },
                            label = { Text(option.code) }
                        )
                    }
                }
                OutlinedTextField(
                    value = dayText,
                    onValueChange = { dayText = it.filter(Char::isDigit).take(2) },
                    label = { Text(stringResource(Res.string.support_dialog_day)) },
                    supportingText = { Text(stringResource(Res.string.support_dialog_day_hint)) },
                    isError = day == null,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Text(stringResource(Res.string.support_dialog_payer), style = MaterialTheme.typography.labelLarge)
                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                    participants.forEachIndexed { index, uid ->
                        SegmentedButton(
                            selected = payer == uid,
                            onClick = { payer = uid },
                            shape = SegmentedButtonDefaults.itemShape(index = index, count = participants.size),
                            label = { Text(names.labelForUid(uid)) }
                        )
                    }
                }
                Text(stringResource(Res.string.support_dialog_note), style = MaterialTheme.typography.bodySmall)
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (amount != null && day != null) {
                        val fromMonth = Clock.System.todayIn(TimeZone.currentSystemDefault()).yearMonth
                        onConfirm(SupportTerms(amount, currency, day, payer, fromMonth))
                    }
                },
                enabled = amount != null && day != null && payer.isNotBlank()
            ) {
                Text(stringResource(Res.string.support_dialog_send))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(Res.string.support_dialog_cancel)) }
        }
    )
}

/** The day a first proposal starts on. */
private const val DEFAULT_DAY = 5
