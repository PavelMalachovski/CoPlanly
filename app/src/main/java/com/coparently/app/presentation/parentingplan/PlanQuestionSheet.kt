package com.coparently.app.presentation.parentingplan

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SheetState
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.coparently.app.R
import com.coparently.app.data.repository.ParentingPlanPair
import com.coparently.app.presentation.common.ConfirmationDialog
import com.coparently.app.presentation.theme.Spacing

/**
 * One question, both answers, and the tick that turns them into an agreement (MON-5).
 *
 * **Your answer is editable and theirs is not, in the same sheet.** That is the point of showing
 * them together: a parent should be able to see that they cannot write the other's half, not
 * merely be prevented from it by a rule they never meet.
 *
 * @param questionId The catalogue id being answered.
 * @param plan Both halves as they stand.
 * @param coParentName What to call the other parent, already resolved through `ParentNames`.
 * @param onSaveAnswer Records this parent's answer.
 * @param onAgree Ticks agreement with the co-parent's wording, or unticks it when given null.
 * @param onDismiss Closes the sheet.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
// One callback per action the sheet offers, plus both halves and the name to label the other
// with. Collapsing them into a state object would hide which of the two halves is editable,
// which is the one thing this screen exists to make visible. Its length is the one sheet laid
// out top to bottom, plus the discard guard every edit of it needs.
@Suppress("LongParameterList", "LongMethod")
fun PlanQuestionSheet(
    questionId: String,
    plan: ParentingPlanPair,
    coParentName: String,
    onSaveAnswer: (String) -> Unit,
    onAgree: (String?) -> Unit,
    onDismiss: () -> Unit
) {
    val prompt = PlanStrings.questionPrompt(questionId) ?: return
    val theirAnswer = plan.theirs?.answerTo(questionId)
    // Seeded once per question rather than tracked: re-seeding on every emission would take the
    // cursor away mid-sentence when the co-parent's own write arrives through the listener.
    val seeded = remember(questionId) { plan.yours.answers[questionId].orEmpty() }
    var draft by remember(questionId) { mutableStateOf(seeded) }
    val agreed = theirAnswer != null && plan.yours.agreedTo[questionId] == theirAnswer

    var confirmDiscard by remember { mutableStateOf(false) }
    val sheetState = rememberGuardedSheetState(
        unsaved = draft != seeded,
        onRefused = { confirmDiscard = true }
    )
    if (confirmDiscard) {
        DiscardAnswerDialog(
            onKeep = { confirmDiscard = false },
            onDiscard = {
                confirmDiscard = false
                onDismiss()
            }
        )
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Spacing.XL)
                .padding(bottom = Spacing.XXL),
            verticalArrangement = Arrangement.spacedBy(Spacing.L)
        ) {
            Text(text = stringResource(prompt), style = MaterialTheme.typography.titleMedium)

            YourAnswerField(draft = draft, onDraftChange = { draft = it })

            TheirAnswer(theirAnswer = theirAnswer, coParentName = coParentName)

            if (theirAnswer != null) {
                AgreementRow(
                    agreed = agreed,
                    onToggle = { onAgree(if (agreed) null else theirAnswer) }
                )
            }

            Button(
                onClick = {
                    onSaveAnswer(draft)
                    onDismiss()
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(stringResource(R.string.parenting_plan_save))
            }
        }
    }
}

/**
 * A sheet state that will not hide while [unsaved] is true, calling [onRefused] instead.
 *
 * A swipe down, a tap outside or Back used to close the sheet and drop whatever had been typed;
 * all three go through `confirmValueChange`. [unsaved] is read through `rememberUpdatedState`
 * because the sheet state keeps the lambda it was created with.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun rememberGuardedSheetState(unsaved: Boolean, onRefused: () -> Unit): SheetState {
    val currentUnsaved by rememberUpdatedState(unsaved)
    val currentOnRefused by rememberUpdatedState(onRefused)
    return rememberModalBottomSheetState(
        skipPartiallyExpanded = true,
        confirmValueChange = { target ->
            val refuse = target == SheetValue.Hidden && currentUnsaved
            if (refuse) currentOnRefused()
            !refuse
        }
    )
}

/** Asks before an edited answer is thrown away. */
@Composable
private fun DiscardAnswerDialog(onKeep: () -> Unit, onDiscard: () -> Unit) {
    ConfirmationDialog(
        title = stringResource(R.string.common_discard_changes_title),
        message = stringResource(R.string.parenting_plan_discard_message),
        confirmText = stringResource(R.string.common_discard),
        dismissText = stringResource(R.string.common_keep_editing),
        isDestructive = true,
        onDismiss = onKeep,
        onConfirm = onDiscard
    )
}

/**
 * The half this parent may edit.
 *
 * "Your answer" is the field's own `label`, not a caption above it, so TalkBack announces the
 * field by what it is for; a free-standing caption was read as a separate item and the field
 * itself as an unnamed edit box. The label takes the theme's primary while the field is focused,
 * which keeps the editable half visibly apart from the co-parent's.
 */
@Composable
private fun YourAnswerField(draft: String, onDraftChange: (String) -> Unit) {
    OutlinedTextField(
        value = draft,
        onValueChange = onDraftChange,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 120.dp),
        label = { Text(stringResource(R.string.parenting_plan_your_answer)) },
        placeholder = { Text(stringResource(R.string.parenting_plan_answer_hint)) }
    )
}

@Composable
private fun TheirAnswer(theirAnswer: String?, coParentName: String) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.XS)) {
        Text(
            text = stringResource(R.string.parenting_plan_their_answer, coParentName),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Surface(
            color = MaterialTheme.colorScheme.surfaceContainer,
            shape = MaterialTheme.shapes.medium,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = theirAnswer
                    ?: stringResource(R.string.parenting_plan_their_answer_missing, coParentName),
                style = MaterialTheme.typography.bodyMedium,
                color = if (theirAnswer == null) {
                    MaterialTheme.colorScheme.onSurfaceVariant
                } else {
                    MaterialTheme.colorScheme.onSurface
                },
                modifier = Modifier.padding(Spacing.L)
            )
        }
    }
}

@Composable
private fun AgreementRow(agreed: Boolean, onToggle: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.XS)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(Spacing.S),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Checkbox(checked = agreed, onCheckedChange = { onToggle() })
            Text(
                text = stringResource(R.string.parenting_plan_agree),
                style = MaterialTheme.typography.bodyMedium
            )
        }
        Text(
            text = stringResource(R.string.parenting_plan_agree_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
