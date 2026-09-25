package com.coparently.app.presentation.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import com.coparently.app.R
import com.coparently.app.domain.custody.CustodyPatternDiff
import com.coparently.app.domain.custody.CustodyProposal
import com.coparently.app.domain.custody.DaySwapGroup
import com.coparently.app.domain.parentingplan.CitationStatus
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
 * The answers Home's dialogs can give, grouped so the dialogs take one parameter for them.
 *
 * @property onAcceptSwap Accepts a day-swap offer.
 * @property onDeclineSwap Declines a day-swap offer.
 * @property onAcceptProposal Accepts the pending custody proposal.
 * @property onDeclineProposal Declines the pending custody proposal.
 * @property onOpenChangeRequests Opens the inbox, where event change requests are answered.
 */
class AwaitingActions(
    val onAcceptSwap: (DaySwapGroup) -> Unit,
    val onDeclineSwap: (DaySwapGroup) -> Unit,
    val onAcceptProposal: () -> Unit,
    val onDeclineProposal: () -> Unit,
    val onOpenChangeRequests: () -> Unit
)

/**
 * The pop-up ask the owner walkthrough called for (items 4/13): what waits on this parent's
 * answer confronts them on open, instead of hiding behind a row they may never tap.
 *
 * One dialog at a time — a custody proposal first, then day swaps. A swap carries enough context
 * to answer right here (who, which day), so it gets real Accept/Decline buttons; event change
 * requests carry times and notes, so their dialog routes to the inbox that can show them. "Later"
 * (or tapping outside) puts the ask off until it changes ([PutOffAsksViewModel]): it stays in the
 * calendar's banner and the inbox, but Home stops interrupting with it. It used to return on
 * every visit, which the UI tour showed as the same dialog five screens running (R-2).
 *
 * @param state The dashboard, for the swaps and requests awaiting this parent.
 * @param parentNames Resolves a uid to that parent's name.
 * @param proposal The co-parent's pending custody proposal, or null.
 * @param actions What each answer does.
 * @param putOffAsks Which asks this person put off, and until when.
 */
@Composable
internal fun AwaitingDialogs(
    state: HomeUiState.Dashboard,
    parentNames: ParentNames,
    proposal: ProposalAsk?,
    actions: AwaitingActions,
    putOffAsks: PutOffAsksViewModel = hiltViewModel()
) {
    // Stored, not remembered: "Later" puts an ask off until it changes (release audit R-2), and
    // an ask that followed the screen came back on every visit to Home.
    val putOff by putOffAsks.putOff.collectAsState()

    // Each ask under its revision, so a fresh proposal, an amended offer or a new request opens
    // again even after the last was put off.
    val proposalKey = proposal?.let { "proposal_${it.proposal.proposedAt}" }
    val requestsKey = "requests_${state.awaitingRequestCount}"
    val waiting = buildSet {
        proposalKey?.let(::add)
        state.awaitingSwaps.forEach { add("swap_${it.revision}") }
        if (state.awaitingRequestCount > 0) add(requestsKey)
    }
    val dismiss: (String) -> Unit = { key -> putOffAsks.putOff(key, waiting) }
    // One dialog per *offer*, not per day. A week offered as one agreement used to raise seven
    // dialogs in a row, each dismissal revealing the next and every one asking the same question.
    val swapGroup = state.awaitingSwaps.firstOrNull { "swap_${it.revision}" !in putOff }

    when {
        proposal != null && proposalKey != null && proposalKey !in putOff ->
            ProposalDialog(proposal, parentNames, onDismiss = { dismiss(proposalKey) }, actions = actions)

        swapGroup != null ->
            SwapDialog(swapGroup, parentNames, onDismiss = { dismiss("swap_${swapGroup.revision}") }, actions = actions)

        state.awaitingRequestCount > 0 && requestsKey !in putOff ->
            RequestsDialog(
                onDismiss = { dismiss(requestsKey) },
                onReview = actions.onOpenChangeRequests
            )
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
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.M)) {
                Text(if (diff == null) who else "$who\n\n$diff")
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

/** One day-swap offer, answered in place. */
@Composable
private fun SwapDialog(
    swapGroup: DaySwapGroup,
    parentNames: ParentNames,
    onDismiss: () -> Unit,
    actions: AwaitingActions
) {
    val first = swapGroup.swaps.first()
    val dateFormat = DateTimeFormatter.ofLocalizedDate(FormatStyle.FULL)
    val context = LocalContext.current
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.home_dialog_swap_title)) },
        text = {
            Text(
                if (swapGroup.dayCount == 1) {
                    stringResource(
                        R.string.home_dialog_swap_message,
                        parentNames.labelForUid(first.override.requestedBy),
                        unbreakableDate(swapGroup.firstDate.format(dateFormat))
                    )
                } else {
                    context.resources.getQuantityString(
                        R.plurals.home_dialog_swap_message_days,
                        swapGroup.dayCount,
                        swapGroup.dayCount,
                        parentNames.labelForUid(first.override.requestedBy),
                        unbreakableDate(swapGroup.firstDate.format(dateFormat)),
                        unbreakableDate(swapGroup.lastDate.format(dateFormat))
                    )
                }
            )
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
