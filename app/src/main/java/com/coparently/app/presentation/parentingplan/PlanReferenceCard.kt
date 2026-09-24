package com.coparently.app.presentation.parentingplan

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.unit.dp
import com.coparently.app.R
import com.coparently.app.domain.parentingplan.CitationStatus
import com.coparently.app.domain.parentingplan.PlanReference
import com.coparently.app.presentation.common.GroupLabel
import com.coparently.app.presentation.common.ParentNames
import com.coparently.app.presentation.common.SectionGroup

/**
 * The agreed parenting-plan answer, quoted read-only above the schedule editor it was opened into
 * (MON-21).
 *
 * It quotes; it does not fill anything in. The parent builds the pattern or the layer below, and
 * the note says what happens on save — the co-parent gets a proposal that names this answer, not
 * a schedule already changed.
 *
 * @param reference The question and the two agreed wordings.
 * @param coParentName The co-parent's name, for labelling their wording and the note.
 * @param modifier Placement from the caller.
 */
@Composable
fun PlanReferenceCard(reference: PlanReference, coParentName: String, modifier: Modifier = Modifier) {
    Column(modifier = modifier) {
        GroupLabel(text = stringResource(R.string.plan_reference_title))
        SectionGroup {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                PlanStrings.questionPrompt(reference.questionId)?.let { prompt ->
                    Text(text = stringResource(prompt), style = MaterialTheme.typography.titleSmall)
                }
                if (reference.sameWording) {
                    Quote(label = stringResource(R.string.plan_reference_both_agreed), text = reference.yourAnswer)
                } else {
                    Quote(label = stringResource(R.string.parenting_plan_your_answer), text = reference.yourAnswer)
                    Quote(
                        label = stringResource(R.string.parenting_plan_their_answer, coParentName),
                        text = reference.theirAnswer
                    )
                }
                Text(
                    text = stringResource(R.string.plan_reference_note, coParentName),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

/** One labelled wording, set apart as a quotation. */
@Composable
private fun Quote(label: String, text: String) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(text = text, style = MaterialTheme.typography.bodyMedium, fontStyle = FontStyle.Italic)
    }
}

/**
 * The line a proposal card shows about where the proposal came from (MON-21), or null when there
 * is nothing to say — no citation, one this build cannot read, or a question it has no wording
 * for. Never an error: a missing citation is simply an older build's proposal.
 *
 * The one wording of a [CitationStatus]: the inbox card and Home's proposal dialog both print this,
 * from the same `ChangeRequestViewModel.pendingProposalCitation`, so they cannot disagree.
 */
@Composable
fun planCitationLine(citation: CitationStatus): String? =
    citationLine(citation, changed = R.string.plan_citation_changed)

/**
 * [planCitationLine] for a one-line surface — the calendar's proposal banner. The same status and
 * the same "current" wording; "changed since" leads with the change, so an ellipsis on a narrow
 * screen cuts the question, never the fact that the answer moved.
 */
@Composable
fun planCitationShortLine(citation: CitationStatus): String? =
    citationLine(citation, changed = R.string.plan_citation_changed_short)

@Composable
private fun citationLine(citation: CitationStatus, @StringRes changed: Int): String? {
    val (questionId, current) = when (citation) {
        is CitationStatus.Current -> citation.questionId to true
        is CitationStatus.Changed -> citation.questionId to false
        CitationStatus.None -> return null
    }
    val prompt = PlanStrings.questionPrompt(questionId)?.let { stringResource(it) } ?: return null
    return stringResource(if (current) R.string.plan_citation_current else changed, prompt)
}

/**
 * The co-parent's name for the card, or the "your co-parent" fallback while none is known — the
 * same resolution the seasonal section already uses, never a role word.
 */
fun ParentNames.coParentLabel(): String =
    parents.coParent?.let { labelForUid(it.uid) } ?: coParentFallback
