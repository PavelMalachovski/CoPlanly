package com.coparently.app.presentation.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import com.coparently.app.R
import com.coparently.app.domain.custody.CustodyPatternDiff
import com.coparently.app.domain.custody.CustodyProposal
import com.coparently.app.domain.custody.DaySwapGroup
import com.coparently.app.domain.expenses.SplitRatio
import com.coparently.app.domain.expenses.SplitRatioProposal
import com.coparently.app.domain.parentingplan.CitationStatus
import com.coparently.app.presentation.calendar.MiniMonthModel
import com.coparently.app.presentation.calendar.components.isConsecutive
import com.coparently.app.presentation.changerequests.ProposedSchedulePreview
import com.coparently.app.presentation.changerequests.SwapDayAnswers
import com.coparently.app.presentation.changerequests.SwapMonths
import com.coparently.app.presentation.changerequests.splitSharesText
import com.coparently.app.presentation.common.ParentNames
import com.coparently.app.presentation.custody.custodyDiffDescription
import com.coparently.app.presentation.parentingplan.planCitationLine
import com.coparently.app.presentation.theme.Spacing
import com.coparently.app.utils.unbreakableDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/**
 * The co-parent's pending custody proposal, as Home's dialog asks about it.
 *
 * One value rather than three parameters, so the dialog's signature stays within detekt's limit
 * as what it shows grows — it grew here by [citation] (MON-21).
 *
 * @property proposal The proposal waiting for this parent's answer.
 * @property diff What it would change, or null when the two patterns cannot be compared.
 * @property citation Where it came from, derived by `ChangeRequestViewModel.pendingProposalCitation`
 *   — the same live value the inbox card reads, so the two never disagree.
 */
data class ProposalAsk(
    val proposal: CustodyProposal,
    val diff: CustodyPatternDiff?,
    val citation: CitationStatus = CitationStatus.None
)

/**
 * The co-parent's proposed expense split, as Home's dialog asks about it.
 *
 * @property proposal The proposal waiting for this parent's answer.
 * @property agreed The split agreed now — what the proposal would replace — or null while it is not
 *   known; the dialog then shows the proposal alone.
 */
data class SplitAsk(
    val proposal: SplitRatioProposal,
    val agreed: SplitRatio?
)

/**
 * The proposals waiting on this parent's answer, each of which Home asks about in a dialog of its
 * own. One value, so [AwaitingDialogs] keeps one parameter for them as they grow — it grew here by
 * [split].
 *
 * @property proposal The co-parent's pending custody proposal, or null.
 * @property split The co-parent's proposed expense split, or null.
 * @property swapMonths Each waiting swap's months, by group key (21 October 2026); a swap with none
 *   yet is asked about without them.
 */
data class HomeAsks(
    val proposal: ProposalAsk? = null,
    val split: SplitAsk? = null,
    val swapMonths: Map<String, List<MiniMonthModel>> = emptyMap()
)

/**
 * The answers Home's dialogs can give, grouped so the dialogs take one parameter for them.
 *
 * @property onAcceptSwap Accepts a day-swap offer.
 * @property onDeclineSwap Declines a day-swap offer.
 * @property onAcceptProposal Accepts the pending custody proposal.
 * @property onDeclineProposal Declines the pending custody proposal.
 * @property onOpenChangeRequests Opens the inbox, where event change requests are answered.
 * @property onSwapSeen Records that this parent opened a day-swap offer: its dialog was shown
 *   (phase 3a, spec §4.5).
 * @property onProposalSeen Records that this parent opened the custody proposal: its dialog was shown.
 * @property onAcceptSplit Accepts the proposed expense split.
 * @property onDeclineSplit Declines the proposed expense split.
 * @property onSplitSeen Records that this parent opened the proposed split: its dialog was shown.
 * @property onAnswerSwapDays Answers a day-swap offer day by day (21 October 2026): ISO date to
 *   the answer, a day left out staying open.
 */
data class AwaitingActions(
    val onAcceptSwap: (DaySwapGroup) -> Unit,
    val onDeclineSwap: (DaySwapGroup) -> Unit,
    val onAcceptProposal: () -> Unit,
    val onDeclineProposal: () -> Unit,
    val onOpenChangeRequests: () -> Unit,
    val onSwapSeen: (DaySwapGroup) -> Unit,
    val onProposalSeen: () -> Unit,
    val onAcceptSplit: () -> Unit,
    val onDeclineSplit: () -> Unit,
    val onSplitSeen: () -> Unit,
    val onAnswerSwapDays: (DaySwapGroup, Map<String, Boolean>) -> Unit = { _, _ -> }
)

/**
 * The pop-up ask the owner walkthrough called for (items 4/13): what waits on this parent's
 * answer confronts them on open, instead of hiding behind a row they may never tap.
 *
 * One dialog at a time ([HomeAskQueue]) — a custody proposal first, then a proposed expense split,
 * then day swaps. A proposal, a split and a swap carry enough context to answer right here (who,
 * what, which day), so they get real Accept/Decline buttons; event change requests carry times and
 * notes, so their dialog routes to the inbox that can show them. "Later" (or tapping outside) puts
 * the ask off until it changes ([PutOffAsksViewModel]): it stays in the calendar's banner and the
 * inbox, but Home stops interrupting with it. It used to return on every visit, which the UI tour
 * showed as the same dialog five screens running (R-2).
 *
 * @param state The dashboard, for the swaps and requests awaiting this parent.
 * @param parentNames Resolves a uid to that parent's name.
 * @param asks The co-parent's pending custody proposal and proposed split; either may be absent.
 * @param actions What each answer does.
 * @param putOffAsks Which asks this person put off, and until when.
 */
@Composable
internal fun AwaitingDialogs(
    state: HomeUiState.Dashboard,
    parentNames: ParentNames,
    asks: HomeAsks,
    actions: AwaitingActions,
    putOffAsks: PutOffAsksViewModel = hiltViewModel()
) {
    // Stored, not remembered: "Later" puts an ask off until it changes (release audit R-2), and
    // an ask that followed the screen came back on every visit to Home.
    val putOff by putOffAsks.putOff.collectAsState()

    // Each ask under its revision, so a fresh proposal, an amended offer or a new request opens
    // again even after the last was put off. One dialog per *offer*, not per day: a week offered as
    // one agreement used to raise seven dialogs in a row, each asking the same question.
    val queue = HomeAskQueue.of(asks, state.awaitingSwaps, state.awaitingRequestIds)
    val waiting = queue.flatMapTo(mutableSetOf()) { it.keys }
    val dismiss: (Set<String>) -> Unit = { keys -> putOffAsks.putOff(keys, waiting) }

    when (val ask = HomeAskQueue.next(queue, putOff)) {
        is HomeAsk.Proposal ->
            ProposalDialog(ask.ask, parentNames, onDismiss = { dismiss(ask.keys) }, actions = actions)

        is HomeAsk.Split ->
            SplitDialog(ask.ask, parentNames, onDismiss = { dismiss(ask.keys) }, actions = actions)

        is HomeAsk.Swap ->
            SwapDialog(
                ask.group,
                asks.swapMonths[ask.group.key].orEmpty(),
                parentNames,
                onDismiss = { dismiss(ask.keys) },
                actions = actions
            )

        is HomeAsk.Requests ->
            RequestsDialog(
                onDismiss = { dismiss(ask.keys) },
                onReview = actions.onOpenChangeRequests
            )

        null -> Unit
    }
}

/**
 * Who proposed a new schedule, what it would change, and — when it was built from the parenting
 * plan — which agreed answer it cites (MON-21), worded by `planCitationLine` exactly as the inbox
 * card words it. A proposal citing nothing says nothing about it.
 */
@Composable
private fun ProposalDialog(
    ask: ProposalAsk,
    parentNames: ParentNames,
    onDismiss: () -> Unit,
    actions: AwaitingActions
) {
    // Shown is opened (spec §4.5): once per proposal, however often the dialog recomposes.
    LaunchedEffect(ask.proposal.proposedAt) { actions.onProposalSeen() }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.custody_proposal_inbox_title)) },
        text = {
            // Who proposed it, and what it would actually do. The agreed pattern and the proposed
            // one sit on the same document, so the diff costs no extra read.
            val who = stringResource(
                R.string.custody_proposal_inbox_body,
                parentNames.labelForUid(ask.proposal.proposedBy)
            )
            val diff = custodyDiffDescription(ask.diff, parentNames)
            val citation = planCitationLine(ask.citation)
            // Scrolls: the mini month makes the dialog taller than a small phone at large text.
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(Spacing.M)
            ) {
                Text(if (diff == null) who else "$who\n\n$diff")
                // What it looks like, always — the diff above is missing when the pattern changes.
                ProposedSchedulePreview(ask.proposal, parentNames)
                citation?.let { line ->
                    Text(
                        text = line,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                onDismiss()
                actions.onAcceptProposal()
            }) { Text(stringResource(R.string.custody_proposal_accept)) }
        },
        dismissButton = {
            Row {
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.home_dialog_later)) }
                TextButton(onClick = {
                    onDismiss()
                    actions.onDeclineProposal()
                }) { Text(stringResource(R.string.custody_proposal_decline)) }
            }
        }
    )
}

/**
 * Who proposed a new expense split and what it would be, each share beside the name of the parent
 * who pays it, as the Expenses banner words it; then the split agreed now, so "35 / 65" says what it
 * replaces. The answer is the inbox's and the Expenses banner's: accepting prices the expenses
 * recorded from then on, and the ones already recorded keep their split.
 */
@Composable
private fun SplitDialog(
    ask: SplitAsk,
    parentNames: ParentNames,
    onDismiss: () -> Unit,
    actions: AwaitingActions
) {
    // Shown is opened (spec §4.5): once per proposal, however often the dialog recomposes.
    LaunchedEffect(ask.proposal.proposedAtMillis) { actions.onSplitSeen() }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.expenses_split_proposal_title)) },
        text = {
            // Scrolls: names and money at the largest text can outgrow a small phone, and an amount
            // is never cut off (design item 15).
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(Spacing.M)
            ) {
                Text(
                    stringResource(
                        R.string.expenses_split_proposal_body_named,
                        parentNames.labelForUid(ask.proposal.proposedBy),
                        splitSharesText(ask.proposal.ratio, parentNames)
                    )
                )
                ask.agreed?.let { now ->
                    Text(
                        text = stringResource(R.string.split_request_now, splitSharesText(now, parentNames)),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                onDismiss()
                actions.onAcceptSplit()
            }) { Text(stringResource(R.string.change_request_accept)) }
        },
        dismissButton = {
            Row {
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.home_dialog_later)) }
                TextButton(onClick = {
                    onDismiss()
                    actions.onDeclineSplit()
                }) { Text(stringResource(R.string.change_request_decline)) }
            }
        }
    )
}

/**
 * One day-swap offer, answered in place: whole, or day by day (21 October 2026), with the months
 * its days fall in, each day in the colour of whom it would go to.
 */
@Composable
private fun SwapDialog(
    swapGroup: DaySwapGroup,
    months: List<MiniMonthModel>,
    parentNames: ParentNames,
    onDismiss: () -> Unit,
    actions: AwaitingActions
) {
    val myUid = parentNames.parents.me?.uid
    // Shown is opened (spec §4.5): once per offer, however often the dialog recomposes.
    LaunchedEffect(swapGroup.revision) { actions.onSwapSeen(swapGroup) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.home_dialog_swap_title)) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(Spacing.M)
            ) {
                Text(swapDialogMessage(swapGroup, parentNames))
                SwapMonths(months, parentNames)
                myUid?.let { uid ->
                    SwapDayAnswers(swapGroup, uid, parentNames) { picked ->
                        onDismiss()
                        actions.onAnswerSwapDays(swapGroup, picked)
                    }
                }
            }
        },
        confirmButton = {
            // Dismissed on tap as well: the answer's round trip through Firestore takes a
            // moment, and the dialog must not sit there inviting a second tap meanwhile.
            TextButton(onClick = {
                onDismiss()
                actions.onAcceptSwap(swapGroup)
            }) {
                Text(stringResource(R.string.day_swap_accept))
            }
        },
        dismissButton = {
            Row {
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.home_dialog_later)) }
                TextButton(onClick = {
                    onDismiss()
                    actions.onDeclineSwap(swapGroup)
                }) {
                    Text(stringResource(R.string.day_swap_decline))
                }
            }
        }
    )
}

/**
 * Who offers which days: one day, a run from its first to its last day, or days picked apart one by
 * one (21 October 2026), which "from Monday to Friday" would misdescribe.
 */
@Composable
private fun swapDialogMessage(swapGroup: DaySwapGroup, parentNames: ParentNames): String {
    val first = swapGroup.swaps.first()
    val dateFormat = DateTimeFormatter.ofLocalizedDate(FormatStyle.FULL)
    val shortDate = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)
    val from = parentNames.labelForUid(first.override.requestedBy)
    return when {
        swapGroup.dayCount == 1 -> stringResource(
            R.string.home_dialog_swap_message,
            from,
            unbreakableDate(swapGroup.firstDate.format(dateFormat))
        )
        !isConsecutive(swapGroup.swaps.map { it.date }) -> stringResource(
            R.string.home_dialog_swap_message_days_apart,
            from,
            swapGroup.swaps.joinToString(", ") { unbreakableDate(it.date.format(shortDate)) }
        )
        else -> LocalContext.current.resources.getQuantityString(
            R.plurals.home_dialog_swap_message_days,
            swapGroup.dayCount,
            swapGroup.dayCount,
            from,
            unbreakableDate(swapGroup.firstDate.format(dateFormat)),
            unbreakableDate(swapGroup.lastDate.format(dateFormat))
        )
    }
}

/** The event change requests waiting, as one summary that routes to the inbox. */
@Composable
private fun RequestsDialog(onDismiss: () -> Unit, onReview: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.home_dialog_requests_title)) },
        text = { Text(stringResource(R.string.home_dialog_requests_message)) },
        confirmButton = {
            TextButton(onClick = {
                onDismiss()
                onReview()
            }) {
                Text(stringResource(R.string.home_dialog_review))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.home_dialog_later)) }
        }
    )
}
