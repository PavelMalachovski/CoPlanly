package com.coparently.app.presentation.review

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Celebration
import androidx.compose.material.icons.filled.Event
import androidx.compose.material.icons.filled.EventBusy
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.SyncAlt
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.intl.Locale
import androidx.compose.ui.text.style.TextAlign
import com.coparently.app.domain.review.MonthMoney
import com.coparently.app.domain.review.MonthReview
import com.coparently.app.domain.review.MonthStatsWire
import com.coparently.app.presentation.ai.AiAssistState
import com.coparently.app.presentation.ai.AiConsentDialog
import com.coparently.app.presentation.common.GroupLabel
import com.coparently.app.presentation.common.ParentNames
import com.coparently.app.presentation.common.SectionGroup
import com.coparently.app.presentation.common.SectionRow
import com.coparently.app.presentation.common.asString
import com.coparently.app.presentation.common.rememberParentNames
import com.coparently.app.presentation.theme.IconSizes
import com.coparently.app.presentation.theme.ScreenContainerColor
import com.coparently.app.presentation.theme.Spacing
import com.coparently.app.presentation.theme.screenTopAppBarColors
import com.coparently.app.resources.Res
import com.coparently.app.resources.common_back
import com.coparently.app.resources.month_review_days
import com.coparently.app.resources.month_review_days_without_schedule
import com.coparently.app.resources.month_review_even
import com.coparently.app.resources.month_review_events
import com.coparently.app.resources.month_review_group_activity
import com.coparently.app.resources.month_review_group_money
import com.coparently.app.resources.month_review_group_schedule
import com.coparently.app.resources.month_review_group_summary
import com.coparently.app.resources.month_review_handovers
import com.coparently.app.resources.month_review_next
import com.coparently.app.resources.month_review_no_expenses
import com.coparently.app.resources.month_review_no_schedule
import com.coparently.app.resources.month_review_note
import com.coparently.app.resources.month_review_paid_by
import com.coparently.app.resources.month_review_paid_by_you
import com.coparently.app.resources.month_review_parent_days
import com.coparently.app.resources.month_review_previous
import com.coparently.app.resources.month_review_special_days
import com.coparently.app.resources.month_review_summarize
import com.coparently.app.resources.month_review_summarize_subtitle
import com.coparently.app.resources.month_review_summarizing
import com.coparently.app.resources.month_review_summary_label
import com.coparently.app.resources.month_review_swaps_agreed
import com.coparently.app.resources.month_review_swaps_offered
import com.coparently.app.resources.month_review_title
import com.coparently.app.resources.month_review_to_even_out
import com.coparently.app.resources.month_review_total
import com.coparently.app.utils.ReaderDateText
import com.coparently.app.utils.ReaderMoneyText
import kotlinx.datetime.YearMonth
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

/**
 * Settings → Family → *Month in review*: whose days the month's were, the handovers, the swaps,
 * the shared events and the money — and, when the build offers it, a short AI-written summary of
 * exactly those figures above them.
 *
 * A detail screen off Settings beside the export, because it is the same kind of thing: a look
 * back over the family's records, not a tab's daily business. The figures are the screen; the
 * summary is marked as AI-generated and sits above them so it can be checked against them.
 * Nothing on it scores or blames a parent (see [MonthReview]).
 *
 * @param onNavigateUp Returns to Settings
 * @param viewModel The month's figures and the summary's state
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MonthReviewScreen(
    onNavigateUp: () -> Unit,
    viewModel: MonthReviewViewModel = koinViewModel()
) {
    val state by viewModel.state.collectAsState()
    val names = rememberParentNames(viewModel.parents.collectAsState().value)
    val summary by viewModel.summary.collectAsState()
    val summaryState by viewModel.summaryState.collectAsState()

    Scaffold(
        containerColor = ScreenContainerColor,
        topBar = { MonthReviewTopBar(onNavigateUp) }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(Spacing.L),
            verticalArrangement = Arrangement.spacedBy(Spacing.L)
        ) {
            MonthSwitcher(
                month = state.month,
                canGoForward = state.canGoForward,
                onPrevious = viewModel::previousMonth,
                onNext = viewModel::nextMonth
            )
            val review = state.review
            if (review == null) {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.CenterHorizontally))
            } else {
                // No summary for a month the server could not be told about: without a schedule
                // there are no days to send, and a button that could only fail is item 8.
                if (viewModel.aiAvailable && MonthStatsWire.canSend(review)) {
                    SummarySection(
                        summary = summary,
                        state = summaryState,
                        onSummarize = {
                            viewModel.dismissFailure()
                            viewModel.summarize(Locale.current.toLanguageTag(), names)
                        }
                    )
                }
                ScheduleSection(review, names)
                ActivitySection(review)
                MoneySection(review.money, names)
                Text(
                    text = stringResource(Res.string.month_review_note),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }

    if (summaryState == AiAssistState.AskingConsent) {
        AiConsentDialog(onAgree = viewModel::agree, onCancel = viewModel::decline)
    }
}

/** The screen's title and its up arrow. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MonthReviewTopBar(onNavigateUp: () -> Unit) {
    TopAppBar(
        colors = screenTopAppBarColors(),
        title = { Text(stringResource(Res.string.month_review_title)) },
        navigationIcon = {
            IconButton(onClick = onNavigateUp) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = stringResource(Res.string.common_back)
                )
            }
        }
    )
}

/** The month's name between the two arrows; the later arrow stops at the current month. */
@Composable
private fun MonthSwitcher(month: YearMonth, canGoForward: Boolean, onPrevious: () -> Unit, onNext: () -> Unit) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onPrevious) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                contentDescription = stringResource(Res.string.month_review_previous)
            )
        }
        Text(
            text = ReaderDateText.month(month, "yMMMM").replaceFirstChar { it.uppercase() },
            style = MaterialTheme.typography.titleLarge,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .weight(1f)
                .semantics { heading() }
        )
        IconButton(onClick = onNext, enabled = canGoForward) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = stringResource(Res.string.month_review_next)
            )
        }
    }
}

/**
 * The AI summary: the button while there is none, then the text, labelled as AI-generated and as
 * something to check against the figures below it.
 */
@Composable
private fun SummarySection(summary: String?, state: AiAssistState, onSummarize: () -> Unit) {
    val working = state == AiAssistState.Working
    val failure = (state as? AiAssistState.Failed)?.message
    Column {
        GroupLabel(stringResource(Res.string.month_review_group_summary))
        SectionGroup {
            if (summary != null) {
                Column(
                    modifier = Modifier.padding(Spacing.L),
                    verticalArrangement = Arrangement.spacedBy(Spacing.S)
                ) {
                    Text(
                        text = stringResource(Res.string.month_review_summary_label),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(text = summary, style = MaterialTheme.typography.bodyMedium)
                }
            } else {
                SectionRow(
                    icon = Icons.Default.AutoAwesome,
                    title = stringResource(Res.string.month_review_summarize),
                    supporting = when {
                        working -> stringResource(Res.string.month_review_summarizing)
                        failure != null -> failure.asString()
                        else -> stringResource(Res.string.month_review_summarize_subtitle)
                    },
                    supportingColor = if (failure != null) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    onClick = if (working) null else onSummarize,
                    trailing = if (working) {
                        { CircularProgressIndicator(modifier = Modifier.size(IconSizes.Standard)) }
                    } else {
                        null
                    }
                )
            }
        }
    }
}

/** Whose days the month's were, how often the children changed hands, and the special days. */
@Composable
private fun ScheduleSection(review: MonthReview, names: ParentNames) {
    Column {
        GroupLabel(stringResource(Res.string.month_review_group_schedule))
        SectionGroup {
            if (!review.hasSchedule) {
                SectionRow(
                    icon = Icons.Default.EventBusy,
                    title = stringResource(Res.string.month_review_no_schedule)
                )
                return@SectionGroup
            }
            review.daysBySlot.entries.sortedBy { it.key }.forEachIndexed { index, (slot, days) ->
                if (index > 0) Divider()
                SectionRow(
                    icon = Icons.Default.Person,
                    title = names.labelFor(slot),
                    trailing = { Value(pluralStringResource(Res.plurals.month_review_days, days, days)) }
                )
            }
            if (review.daysWithoutSchedule > 0) {
                Divider()
                SectionRow(
                    icon = Icons.Default.EventBusy,
                    title = stringResource(Res.string.month_review_days_without_schedule),
                    trailing = {
                        Value(
                            pluralStringResource(
                                Res.plurals.month_review_days,
                                review.daysWithoutSchedule,
                                review.daysWithoutSchedule
                            )
                        )
                    }
                )
            }
            Divider()
            SectionRow(
                icon = Icons.Default.SyncAlt,
                title = stringResource(Res.string.month_review_handovers),
                trailing = { Value(review.handovers.toString()) }
            )
            if (review.specialDays > 0) {
                Divider()
                SpecialDaysRow(review, names)
            }
        }
    }
}

/** How many holidays, vacation days and birthdays the month had, and whose days they were. */
@Composable
private fun SpecialDaysRow(review: MonthReview, names: ParentNames) {
    val perParent = review.specialDaysBySlot.entries.sortedBy { it.key }.map { (slot, days) ->
        stringResource(
            Res.string.month_review_parent_days,
            names.labelFor(slot),
            pluralStringResource(Res.plurals.month_review_days, days, days)
        )
    }
    SectionRow(
        icon = Icons.Default.Celebration,
        title = stringResource(Res.string.month_review_special_days),
        supporting = perParent.joinToString(separator = " · ").ifEmpty { null },
        trailing = { Value(review.specialDays.toString()) }
    )
}

/** The swaps offered and agreed, and the shared events. */
@Composable
private fun ActivitySection(review: MonthReview) {
    Column {
        GroupLabel(stringResource(Res.string.month_review_group_activity))
        SectionGroup {
            SectionRow(
                icon = Icons.Default.SwapHoriz,
                title = stringResource(Res.string.month_review_swaps_offered),
                trailing = { Value(review.swapDaysOffered.toString()) }
            )
            Divider()
            SectionRow(
                icon = Icons.Default.SwapHoriz,
                title = stringResource(Res.string.month_review_swaps_agreed),
                trailing = { Value(review.swapDaysAgreed.toString()) }
            )
            Divider()
            SectionRow(
                icon = Icons.Default.Event,
                title = stringResource(Res.string.month_review_events),
                trailing = { Value(review.sharedEvents.toString()) }
            )
        }
    }
}

/**
 * One row per currency: the total, who paid what, and what would even it out. Amounts wrap rather
 * than being cut off (design item 15), and the trailing total stacks under the title at large text.
 */
@Composable
private fun MoneySection(money: List<MonthMoney>, names: ParentNames) {
    Column {
        GroupLabel(stringResource(Res.string.month_review_group_money))
        SectionGroup {
            if (money.isEmpty()) {
                SectionRow(
                    icon = Icons.Default.Payments,
                    title = stringResource(Res.string.month_review_no_expenses)
                )
                return@SectionGroup
            }
            money.forEachIndexed { index, entry ->
                if (index > 0) Divider()
                val paid = entry.paidByUid.entries.sortedBy { it.key }.map { (uid, amount) ->
                    val money = ReaderMoneyText.amount(amount, entry.currency)
                    if (names.isReaderUid(uid)) {
                        stringResource(Res.string.month_review_paid_by_you, money)
                    } else {
                        stringResource(Res.string.month_review_paid_by, names.labelForUid(uid), money)
                    }
                }
                val settlement = entry.settlement?.let {
                    stringResource(
                        Res.string.month_review_to_even_out,
                        ReaderMoneyText.amount(it.amount, entry.currency),
                        names.labelForUid(it.fromUid),
                        names.labelForUid(it.toUid)
                    )
                } ?: stringResource(Res.string.month_review_even)
                SectionRow(
                    icon = Icons.Default.Payments,
                    title = stringResource(Res.string.month_review_total, entry.currency),
                    supporting = (paid + settlement).joinToString(separator = "\n"),
                    trailing = { Value(ReaderMoneyText.amount(entry.total, entry.currency)) },
                    stackTrailingAtLargeFont = true
                )
            }
        }
    }
}

/** A row's figure: never truncated, never ellipsised. */
@Composable
private fun Value(text: String) {
    Text(text = text, style = MaterialTheme.typography.titleMedium)
}
