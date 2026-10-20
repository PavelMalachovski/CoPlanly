package com.coparently.app.presentation.changerequests

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.TextButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.coparently.app.R
import com.coparently.app.domain.changerequests.HistoryEntry
import com.coparently.app.domain.changerequests.RequestOutcome
import com.coparently.app.domain.changerequests.RequestSubject
import com.coparently.app.presentation.common.ParentNames
import com.coparently.app.presentation.common.PillChip
import com.coparently.app.presentation.common.SectionGroup
import com.coparently.app.presentation.common.asString
import com.coparently.app.presentation.common.rememberToday
import com.coparently.app.presentation.common.stacksAtLargeFont
import com.coparently.app.presentation.theme.Spacing
import com.coparently.app.utils.dateWithTime

/**
 * The inbox's History (phase 2, spec §6.2): every request of the family on screen that was
 * answered, withdrawn or — since phase 3a — left with no answer, newest first, as one group of rows.
 *
 * A row says what the request was, what it asked for when that is a time, how it ended — on the
 * inbox's own neutral status chip, with the outcome's shape in it — and who answered when; a "No
 * answer" row names nobody as answering, and carries what its clock knows: the deadline, the last
 * chance, when it was opened, the reminders, and that the calendar did not change. The time sits
 * in its own part after " · " rather than inside the sentence: in Czech, Russian and Ukrainian
 * "answered <date>" would need a preposition and a case.
 *
 * The one action a row carries is **Propose a change**, on an accepted day swap with a day still
 * ahead (tester feedback, October 2026): the agreement stays in this list as it was given, and the
 * change goes to the co-parent as a new offer.
 *
 * @param entries The history, newest first.
 * @param parentNames Names each parent — never "Mom" or "Dad".
 * @param myUid The signed-in parent, for "your request" and "you answered".
 * @param highlightedKey The [HistoryEntry.key] a chat card arrived for, drawn highlighted.
 * @param onProposeChange Opens the change for an accepted swap's row; null draws no action.
 */
@Composable
internal fun RequestHistoryGroup(
    entries: List<HistoryEntry>,
    parentNames: ParentNames,
    myUid: String,
    highlightedKey: String? = null,
    onProposeChange: ((HistoryEntry) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val today = rememberToday().value
    SectionGroup(modifier = modifier) {
        entries.forEachIndexed { index, entry ->
            if (index > 0) Divider()
            HistoryRow(
                entry = entry,
                parentNames = parentNames,
                myUid = myUid,
                highlighted = entry.key == highlightedKey,
                onProposeChange = onProposeChange
                    ?.takeIf { !entry.queued && entry.swapDays.keys.any { it >= today } }
                    ?.let { open -> { open(entry) } }
            )
        }
    }
}

@Composable
private fun HistoryRow(
    entry: HistoryEntry,
    parentNames: ParentNames,
    myUid: String,
    highlighted: Boolean,
    onProposeChange: (() -> Unit)?
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (highlighted) Modifier.background(MaterialTheme.colorScheme.secondaryContainer) else Modifier)
            // One stop for TalkBack: the subject, the outcome and who answered when.
            .semantics(mergeDescendants = true) {}
            .padding(horizontal = Spacing.L, vertical = Spacing.M),
        verticalArrangement = Arrangement.spacedBy(Spacing.XS)
    ) {
        // From the large-font scale the chip goes under the title, as every row with a trailing
        // chip does: beside it, German broke "Betreuungsplan" mid-word at 150 %.
        if (stacksAtLargeFont()) {
            Text(text = subjectTitle(entry.subject), style = MaterialTheme.typography.bodyLarge)
            OutcomeChip(entry)
        } else {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Spacing.S),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = subjectTitle(entry.subject),
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.weight(1f)
                )
                OutcomeChip(entry)
            }
        }
        proposedTime(entry.subject)?.let { time ->
            Text(text = time.format(dateWithTime("MMMEd")), style = MaterialTheme.typography.bodyMedium)
        }
        // Nobody answered a "No answer", so its row claims no answer and names nobody as answering:
        // it says what its clock knows instead (phase 3a, spec §6.6).
        if (entry.outcome == RequestOutcome.NO_ANSWER) {
            entry.clock?.let { clock ->
                Text(
                    text = RequestClockText.historyMeta(clock).map { it.asString() }.joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            Text(
                text = stringResource(
                    R.string.request_history_when,
                    whoAnswered(entry, parentNames, myUid),
                    entry.decidedAt.format(dateWithTime("MMMd"))
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        onProposeChange?.let { open ->
            val description = stringResource(R.string.request_history_propose_change_description)
            TextButton(onClick = open, modifier = Modifier.semantics { contentDescription = description }) {
                Text(stringResource(R.string.request_history_propose_change))
            }
        }
    }
}

/**
 * How the request ended, on one neutral chip for every answer and withdrawal — the shape of the
 * icon carries it. "No answer" (phase 3a) is the one outlined chip, with no icon: `onSurfaceVariant`
 * on an `outline` border, never the error colour, which would read as "Declined". This phone's own
 * answer that has not left yet says Queued instead (CQ-20).
 */
@Composable
private fun OutcomeChip(entry: HistoryEntry) {
    if (entry.queued) {
        PillChip(
            label = stringResource(R.string.change_request_status_queued),
            icon = Icons.Default.Schedule,
            iconDescription = stringResource(R.string.change_request_status_queued_desc),
            contentColor = MaterialTheme.colorScheme.onSurfaceVariant
        )
    } else {
        // A null container is PillChip's outlined variant, drawn on the theme's `outline` border.
        val outlined = entry.outcome == RequestOutcome.NO_ANSWER
        PillChip(
            label = stringResource(
                when (entry.outcome) {
                    RequestOutcome.ACCEPTED -> R.string.change_request_status_accepted
                    RequestOutcome.DECLINED -> R.string.change_request_status_declined
                    RequestOutcome.WITHDRAWN -> R.string.change_request_status_cancelled
                    RequestOutcome.NO_ANSWER -> R.string.request_clock_no_answer
                }
            ),
            icon = when (entry.outcome) {
                RequestOutcome.ACCEPTED -> Icons.Default.Check
                RequestOutcome.DECLINED -> Icons.Default.Close
                RequestOutcome.WITHDRAWN -> Icons.AutoMirrored.Filled.Undo
                RequestOutcome.NO_ANSWER -> null
            },
            container = if (outlined) null else MaterialTheme.colorScheme.surfaceVariant,
            contentColor = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/** The time a request asked for, for the two kinds that asked for one. */
private fun proposedTime(subject: RequestSubject) = when (subject) {
    is RequestSubject.NewTime -> subject.proposedStart
    is RequestSubject.NewEvent -> subject.start
    is RequestSubject.DaySwap, RequestSubject.CustodySchedule -> null
}

/** Who asked and who answered, from this parent's side — or who withdrew it. */
@Composable
private fun whoAnswered(entry: HistoryEntry, parentNames: ParentNames, myUid: String): String = when {
    entry.outcome == RequestOutcome.WITHDRAWN && entry.decidedBy == myUid ->
        stringResource(R.string.request_history_withdrawn_by_you)
    entry.outcome == RequestOutcome.WITHDRAWN ->
        stringResource(R.string.request_history_withdrawn_by, parentNames.labelForUid(entry.decidedBy))
    entry.askedBy == myUid ->
        stringResource(R.string.request_history_yours_answered, parentNames.labelForUid(entry.decidedBy))
    else -> stringResource(R.string.request_history_theirs_answered, parentNames.labelForUid(entry.askedBy))
}
