package com.coparently.app.presentation.parentingplan

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontStyle
import com.coparently.app.R
import com.coparently.app.domain.parentingplan.PlanReference
import com.coparently.app.presentation.common.GroupLabel
import com.coparently.app.presentation.common.SectionGroup
import com.coparently.app.presentation.theme.Spacing
import org.jetbrains.compose.resources.stringResource

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
                modifier = Modifier.padding(Spacing.L),
                verticalArrangement = Arrangement.spacedBy(Spacing.S)
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
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.XXS)) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(text = text, style = MaterialTheme.typography.bodyMedium, fontStyle = FontStyle.Italic)
    }
}
