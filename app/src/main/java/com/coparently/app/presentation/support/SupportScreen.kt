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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.hilt.navigation.compose.hiltViewModel
import com.coparently.app.R
import com.coparently.app.domain.family.FamilyKey
import com.coparently.app.domain.money.SupportedCurrency
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
import com.coparently.app.presentation.common.SectionGroup
import com.coparently.app.presentation.common.SectionRow
import com.coparently.app.presentation.common.asString
import com.coparently.app.presentation.common.rememberParentNames
import com.coparently.app.presentation.expenses.currencyFormat
import com.coparently.app.presentation.theme.Spacing
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Family → Money → Child support (25 October 2026, owner decisions): the agreement, its waiting
 * proposal, the last months paid or not, and the payer's reminder.
 *
 * @param onNavigateUp Returns to the Family tab.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
@Suppress("LongMethod") // one scaffold, its four states and its dialog
fun SupportScreen(onNavigateUp: () -> Unit, viewModel: SupportViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsState()
    val busy by viewModel.busy.collectAsState()
    val error by viewModel.error.collectAsState()
    val names = rememberParentNames(viewModel.parents.collectAsState().value)
    val defaultCurrency by viewModel.defaultCurrency.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current
    var proposing by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(error) {
        error?.let {
            snackbarHostState.showSnackbar(it.asString(context))
            viewModel.errorShown()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.support_title)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateUp) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.common_back)
                        )
                    }
                }
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
                title = stringResource(R.string.support_no_family_title),
                description = stringResource(R.string.support_no_family_description),
                modifier = bodyModifier
            )
            is SupportUiState.Unavailable -> EmptyState(
                icon = Icons.Default.Payments,
                title = stringResource(R.string.support_unavailable),
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
                    setReminder = { days -> viewModel.setReminder(shown.familyId, days) }
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
    val setReminder: (Int?) -> Unit
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
                    title = stringResource(R.string.support_none_title),
                    text = stringResource(R.string.support_none_description),
                    tone = BannerTone.INFO
                )
            } else {
                SectionGroup {
                    SectionRow(
                        title = stringResource(R.string.support_amount_line, money(terms)),
                        icon = Icons.Default.Payments,
                        supporting = stringResource(
                            R.string.support_terms_line,
                            terms.dayOfMonth,
                            names.labelForUid(terms.payerUid),
                            monthName(terms.fromMonth)
                        )
                    )
                }
            }
        }
        if (proposal != null && agreement != null) {
            item(key = "proposal") {
                val mine = proposal.proposedBy == myUid
                val text = stringResource(
                    R.string.support_proposal_body,
                    money(proposal.terms),
                    proposal.terms.dayOfMonth,
                    names.labelForUid(proposal.terms.payerUid)
                )
                InlineBanner(
                    title = if (mine) {
                        stringResource(R.string.support_waiting_title, names.labelForUid(names.parents.coParent?.uid))
                    } else {
                        stringResource(R.string.support_asks_title, names.labelForUid(proposal.proposedBy))
                    },
                    text = text,
                    tone = if (mine) BannerTone.INFO else BannerTone.ATTENTION,
                    actions = {
                        if (mine) {
                            TextButton(onClick = { actions.clear(agreement) }, enabled = enabled) {
                                Text(stringResource(R.string.support_withdraw))
                            }
                        } else {
                            TextButton(onClick = { actions.clear(agreement) }, enabled = enabled) {
                                Text(stringResource(R.string.support_decline))
                            }
                            TextButton(onClick = { actions.accept(agreement) }, enabled = enabled) {
                                Text(stringResource(R.string.support_accept))
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
                        stringResource(if (terms == null) R.string.support_propose else R.string.support_propose_change)
                    )
                }
            }
        }
        if (terms != null) {
            months(state, terms, myUid, enabled, actions)
            if (terms.payerUid == myUid) {
                item(key = "reminder") { ReminderGroup(state.reminderDays, actions.setReminder) }
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
    val months = terms.monthsUpTo(LocalDate.now())
    if (months.isEmpty()) return
    item(key = "months") {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.S)) {
            GroupLabel(stringResource(R.string.support_months))
            SectionGroup {
                months.forEachIndexed { index, month ->
                    if (index > 0) Divider()
                    val payment = state.paymentFor(month)
                    val paymentState = PaymentState.of(payment, myUid)
                    SectionRow(
                        title = monthName(month),
                        icon = if (paymentState == PaymentState.CONFIRMED) {
                            Icons.Default.CheckCircle
                        } else {
                            Icons.Default.Schedule
                        },
                        supporting = when (paymentState) {
                            PaymentState.CONFIRMED -> stringResource(R.string.payment_card_received)
                            PaymentState.PAID_BY_ME, PaymentState.PAID_TO_ME ->
                                stringResource(R.string.payment_card_waiting)
                            PaymentState.NONE -> stringResource(
                                R.string.support_month_due,
                                terms.dueDate(month).format(DateTimeFormatter.ofPattern("d MMMM", Locale.getDefault()))
                            )
                        },
                        trailing = {
                            when {
                                paymentState == PaymentState.NONE && terms.payerUid == myUid ->
                                    TextButton(onClick = { actions.markPaid(terms, month) }, enabled = enabled) {
                                        Text(stringResource(R.string.payment_i_paid))
                                    }
                                paymentState == PaymentState.PAID_TO_ME && payment != null ->
                                    TextButton(onClick = { actions.confirm(payment) }, enabled = enabled) {
                                        Text(stringResource(R.string.payment_money_arrived))
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
                title = stringResource(R.string.support_reminder_row),
                icon = Icons.Default.NotificationsActive,
                supporting = stringResource(R.string.support_reminder_row_supporting),
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
                                    stringResource(R.string.support_reminder_on_the_day)
                                } else {
                                    pluralStringResource(R.plurals.support_reminder_days_before, option, option)
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
    var amountText by rememberSaveable { mutableStateOf(current?.amount?.toPlainString().orEmpty()) }
    var currency by rememberSaveable { mutableStateOf(current?.currency ?: defaultCurrency) }
    var dayText by rememberSaveable { mutableStateOf((current?.dayOfMonth ?: DEFAULT_DAY).toString()) }
    var payer by rememberSaveable {
        mutableStateOf(current?.payerUid ?: participants.firstOrNull { it != myUid } ?: myUid)
    }
    val amount = SupportViewModel.parseAmount(amountText)
    val day = dayText.toIntOrNull()?.takeIf { it in 1..SupportTerms.MAX_DAY }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.support_dialog_title)) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(Spacing.M)
            ) {
                OutlinedTextField(
                    value = amountText,
                    onValueChange = { amountText = it },
                    label = { Text(stringResource(R.string.support_dialog_amount)) },
                    isError = amountText.isNotBlank() && amount == null,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Text(stringResource(R.string.support_dialog_currency), style = MaterialTheme.typography.labelLarge)
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
                    label = { Text(stringResource(R.string.support_dialog_day)) },
                    supportingText = { Text(stringResource(R.string.support_dialog_day_hint)) },
                    isError = day == null,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Text(stringResource(R.string.support_dialog_payer), style = MaterialTheme.typography.labelLarge)
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
                Text(stringResource(R.string.support_dialog_note), style = MaterialTheme.typography.bodySmall)
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (amount != null && day != null) {
                        onConfirm(SupportTerms(amount, currency, day, payer, YearMonth.now()))
                    }
                },
                enabled = amount != null && day != null && payer.isNotBlank()
            ) {
                Text(stringResource(R.string.support_dialog_send))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.support_dialog_cancel)) }
        }
    )
}

/** [terms]' monthly amount in the reader's conventions. */
private fun money(terms: SupportTerms): String = currencyFormat(terms.currency).format(terms.amount)

/** [month] as the reader's language names it: "October 2026". */
private fun monthName(month: YearMonth): String =
    month.format(DateTimeFormatter.ofPattern("LLLL yyyy", Locale.getDefault()))
        .replaceFirstChar { it.titlecase(Locale.getDefault()) }

/** The day a first proposal starts on. */
private const val DEFAULT_DAY = 5
