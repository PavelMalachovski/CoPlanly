package com.coparently.app.presentation.changerequests

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.coparently.app.domain.changerequests.HistoryEntry
import com.coparently.app.domain.changerequests.RequestOutcome
import com.coparently.app.domain.changerequests.RequestSubject
import com.coparently.app.presentation.common.ParentNames
import com.coparently.app.presentation.common.PillChip
import com.coparently.app.presentation.common.SectionGroup
import com.coparently.app.presentation.common.asString
import com.coparently.app.presentation.common.rememberToday
import com.coparently.app.presentation.common.stacksAtLargeFont
import com.coparently.app.presentation.theme.ParentColors
import com.coparently.app.presentation.theme.Spacing
import com.coparently.app.resources.Res
import com.coparently.app.resources.change_request_status_accepted
import com.coparently.app.resources.change_request_status_cancelled
import com.coparently.app.resources.change_request_status_declined
import com.coparently.app.resources.change_request_status_queued
import com.coparently.app.resources.change_request_status_queued_desc
import com.coparently.app.resources.request_clock_no_answer
import com.coparently.app.resources.request_history_legend_you
import com.coparently.app.resources.request_history_propose_change
import com.coparently.app.resources.request_history_propose_change_description
import com.coparently.app.resources.request_history_their_request
import com.coparently.app.resources.request_history_theirs_answered
import com.coparently.app.resources.request_history_when
import com.coparently.app.resources.request_history_withdrawn_by
import com.coparently.app.resources.request_history_withdrawn_by_you
import com.coparently.app.resources.request_history_your_request
import com.coparently.app.resources.request_history_yours_answered
import com.coparently.app.utils.META_SEPARATOR
import com.coparently.app.utils.ReaderDateText
import org.jetbrains.compose.resources.stringResource

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
@Suppress("LongParameterList") // the rows, who reads them, the one highlighted, its one action
fun RequestHistoryGroup(
    entries: List<HistoryEntry>,
    parentNames: ParentNames,
    myUid: String,
    highlightedKey: String? = null,
    onProposeChange: ((HistoryEntry) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val today = rememberToday().value
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(Spacing.S)) {
        AuthorLegend(parentNames)
        SectionGroup {
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
}

/** The leading bar's width: off the 4 dp grid on purpose, a stripe thick enough to read beside text. */
private val AUTHOR_BAR_WIDTH = 5.dp

/**
 * Says what the two colours mean: a dot and a name for each parent, in the colour each marks a
 * request with. Drawn only while both parents are known and are two colours — a pair still sharing
 * one slot, or a co-parent not linked yet, has nothing to tell apart.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AuthorLegend(parentNames: ParentNames) {
    val mine = parentNames.parents.me?.slot
    val theirs = parentNames.parents.coParent?.slot
    if (mine == null || theirs == null || mine == theirs) return
    FlowRow(
        modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.L),
        horizontalArrangement = Arrangement.spacedBy(Spacing.L),
        verticalArrangement = Arrangement.spacedBy(Spacing.XS)
    ) {
        LegendItem(slot = mine, label = stringResource(Res.string.request_history_legend_you))
        LegendItem(slot = theirs, label = parentNames.labelFor(theirs))
    }
}

@Composable
private fun LegendItem(slot: String, label: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.S), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(Spacing.S).background(ParentColors.fill(slot), CircleShape))
        Text(text = label, style = MaterialTheme.typography.bodySmall, color = ParentColors.text(slot))
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
    // The author's colour (design item 12), by the slot of whoever asked; nobody known, no colour.
    val authorSlot = parentNames.slotForUid(entry.askedBy)
    val authorFill = authorSlot?.let { ParentColors.fill(it) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min)
            .then(if (highlighted) Modifier.background(MaterialTheme.colorScheme.secondaryContainer) else Modifier)
            // One stop for TalkBack: the subject, the outcome and who answered when.
            .semantics(mergeDescendants = true) {}
    ) {
        if (authorFill != null) {
            Box(Modifier.width(AUTHOR_BAR_WIDTH).fillMaxHeight().background(authorFill))
        }
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = Spacing.L, vertical = Spacing.M),
            verticalArrangement = Arrangement.spacedBy(Spacing.XS)
        ) {
            TitleAndChip(entry)
            proposedTime(entry.subject)?.let { time ->
                Text(text = ReaderDateText.dateWithTime(time, "MMMEd"), style = MaterialTheme.typography.bodyMedium)
            }
            AuthorMeta(entry, parentNames, myUid)
            onProposeChange?.let { open ->
                val description = stringResource(Res.string.request_history_propose_change_description)
                TextButton(onClick = open, modifier = Modifier.semantics { contentDescription = description }) {
                    Text(stringResource(Res.string.request_history_propose_change))
                }
            }
        }
    }
}

/** The subject and its outcome chip: beside each other, or stacked from the large-font scale. */
@Composable
private fun TitleAndChip(entry: HistoryEntry) {
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
}

/** The meta line: the author in their colour, then who answered when (or what a clock knows). */
@Composable
private fun AuthorMeta(entry: HistoryEntry, parentNames: ParentNames, myUid: String) {
    val authorText = parentNames.slotForUid(entry.askedBy)?.let { ParentColors.text(it) }
    val author = authorLabel(entry, parentNames, myUid)
    // Nobody answered a "No answer", so its row claims no answer and names nobody as answering:
    // it says what its clock knows instead (phase 3a, spec §6.6).
    val rest = if (entry.outcome == RequestOutcome.NO_ANSWER) {
        entry.clock?.let { clock ->
            RequestClockText.historyMeta(clock).map { it.asString() }.joinToString(META_SEPARATOR)
        }
    } else {
        stringResource(
            Res.string.request_history_when,
            whoAnswered(entry, parentNames, myUid),
            ReaderDateText.dateWithTime(entry.decidedAt, "MMMd")
        )
    }
    // The author first and in their colour, then the rest in the quiet one: the status chip
    // above stays neutral, since a colour is a person and a status is a word (design item 5).
    Text(
        text = buildAnnotatedString {
            if (authorText != null) {
                withStyle(SpanStyle(color = authorText)) { append(author) }
            } else {
                append(author)
            }
            if (rest != null) append(META_SEPARATOR + rest)
        },
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
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
            label = stringResource(Res.string.change_request_status_queued),
            icon = Icons.Default.Schedule,
            iconDescription = stringResource(Res.string.change_request_status_queued_desc),
            contentColor = MaterialTheme.colorScheme.onSurfaceVariant
        )
    } else {
        // A null container is PillChip's outlined variant, drawn on the theme's `outline` border.
        val outlined = entry.outcome == RequestOutcome.NO_ANSWER
        PillChip(
            label = stringResource(
                when (entry.outcome) {
                    RequestOutcome.ACCEPTED -> Res.string.change_request_status_accepted
                    RequestOutcome.DECLINED -> Res.string.change_request_status_declined
                    RequestOutcome.WITHDRAWN -> Res.string.change_request_status_cancelled
                    RequestOutcome.NO_ANSWER -> Res.string.request_clock_no_answer
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
    is RequestSubject.DaySwap, RequestSubject.CustodySchedule, RequestSubject.SplitRatio -> null
}

/** Whose request it was, from this parent's side: "Your request" or "<name>'s request". */
@Composable
private fun authorLabel(entry: HistoryEntry, parentNames: ParentNames, myUid: String): String =
    if (entry.askedBy == myUid) {
        stringResource(Res.string.request_history_your_request)
    } else {
        stringResource(Res.string.request_history_their_request, parentNames.labelForUid(entry.askedBy))
    }

/** Who answered, from this parent's side — or who withdrew it. The author has its own label. */
@Composable
private fun whoAnswered(entry: HistoryEntry, parentNames: ParentNames, myUid: String): String = when {
    entry.outcome == RequestOutcome.WITHDRAWN && entry.decidedBy == myUid ->
        stringResource(Res.string.request_history_withdrawn_by_you)
    entry.outcome == RequestOutcome.WITHDRAWN ->
        stringResource(Res.string.request_history_withdrawn_by, parentNames.labelForUid(entry.decidedBy))
    entry.askedBy == myUid ->
        stringResource(Res.string.request_history_yours_answered, parentNames.labelForUid(entry.decidedBy))
    else -> stringResource(Res.string.request_history_theirs_answered)
}
