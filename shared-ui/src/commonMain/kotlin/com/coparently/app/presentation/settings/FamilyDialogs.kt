package com.coparently.app.presentation.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.coparently.app.domain.changerequests.AnswerByPick
import com.coparently.app.domain.expenses.SplitRatio
import com.coparently.app.domain.model.FamilyKind
import com.coparently.app.presentation.common.AnswerByField
import com.coparently.app.presentation.common.AnswerByPickSaver
import com.coparently.app.presentation.common.ParentNames
import com.coparently.app.presentation.common.rememberAnswerByOffer
import com.coparently.app.presentation.parentingplan.coParentLabel
import com.coparently.app.presentation.theme.Spacing
import com.coparently.app.resources.Res
import com.coparently.app.resources.onboarding_family_children
import com.coparently.app.resources.onboarding_family_pets
import com.coparently.app.resources.settings_family_kind_both
import com.coparently.app.resources.settings_family_kind_cancel
import com.coparently.app.resources.settings_family_kind_save
import com.coparently.app.resources.settings_family_kind_title
import com.coparently.app.resources.settings_split_ratio_hint
import com.coparently.app.resources.settings_split_ratio_title
import com.coparently.app.resources.settings_split_ratio_value_named
import org.jetbrains.compose.resources.stringResource
import kotlin.math.roundToInt

/*
 * The family's two answers the Family tab edits through dialogs, as Settings did: what the family
 * co-parents and the expense split. Out of `SettingsScreen.kt` into `:shared-ui` with the Family tab
 * (MON-24 phase 2, slice 11e); Settings itself follows in slice 11f.
 */

/**
 * The one-line summary of what the family co-parents, for the Settings row.
 *
 * Composable because the answer is a set of slot-like constants and the row shows names in the
 * reader's language, which a ViewModel has no `Context` to resolve.
 */
@Composable
fun caresForSummary(kinds: Set<FamilyKind>): String {
    val children = stringResource(Res.string.onboarding_family_children)
    val pets = stringResource(Res.string.onboarding_family_pets)
    return when {
        // One phrase per language, not the two labels joined: each label is a title, capitalised
        // on its own, so "%1$s and %2$s" read "Children and Pets" (docs/AUDIT-2026-10-design.md
        // D-21, found by the week-4 tour).
        kinds.containsAll(FamilyKind.ALL) -> stringResource(Res.string.settings_family_kind_both)
        FamilyKind.PETS in kinds -> pets
        else -> children
    }
}

/**
 * Changes what the family co-parents.
 *
 * A dialog rather than a second screen: two checkboxes and a confirm is the whole interaction,
 * and it is reached from a row that already says the current answer. Confirm is disabled with
 * nothing ticked — a family that co-parents neither is not a state this product has, and an OK
 * that silently did nothing would be worse than one that is plainly unavailable.
 *
 * @param selected What is currently agreed, as the union of both parents' answers.
 * @param onConfirm Called with the new set; only this parent's own record is written.
 * @param onDismiss Closes without changing anything.
 */
@Composable
fun FamilyKindDialog(
    selected: Set<FamilyKind>,
    onConfirm: (Set<FamilyKind>) -> Unit,
    onDismiss: () -> Unit
) {
    var chosen by rememberSaveable(selected) { mutableStateOf(selected.map { it.name }.toSet()) }
    val kinds = chosen.mapNotNull { name -> FamilyKind.entries.firstOrNull { it.name == name } }
        .toSet()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(Res.string.settings_family_kind_title)) },
        text = {
            Column {
                FamilyKind.entries.forEach { kind ->
                    val label = stringResource(
                        if (kind == FamilyKind.CHILDREN) {
                            Res.string.onboarding_family_children
                        } else {
                            Res.string.onboarding_family_pets
                        }
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(
                            checked = kind.name in chosen,
                            onCheckedChange = { checked ->
                                chosen = if (checked) chosen + kind.name else chosen - kind.name
                            }
                        )
                        Text(label)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(kinds) },
                enabled = kinds.isNotEmpty()
            ) {
                Text(stringResource(Res.string.settings_family_kind_save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(Res.string.settings_family_kind_cancel))
            }
        }
    )
}

/**
 * Picks a new split of a shared expense.
 *
 * A slider over whole percents for slot 1, with slot 2 taking the remainder — the two can never
 * be stored inconsistently because only one of them is a value. Percent rather than a free
 * amount because a ratio is what the parents agree; an amount is what an individual expense is.
 *
 * This dialog **proposes**. Whether it applies straight away depends on whether there is a
 * co-parent to ask, and the screen says which happened rather than letting a parent believe a
 * split the other has not agreed. A proposal is a request like any other: it carries the "Answer
 * by" block, and the co-parent sees it in their Requests and on Home until they answer.
 *
 * @param current The agreed ratio, as the slider opens.
 * @param proposes Whether confirming puts the split to the co-parent — the family has agreed one
 *   already — rather than applying it; only then does the dialog ask for a deadline.
 * @param onConfirm Called with the chosen ratio and, when it [proposes], the deadline picked.
 * @param onDismiss Closes without proposing anything.
 */
@Composable
fun SplitRatioDialog(
    current: SplitRatio,
    parentNames: ParentNames,
    proposes: Boolean,
    onConfirm: (SplitRatio, AnswerByPick?) -> Unit,
    onDismiss: () -> Unit
) {
    var momPercent by rememberSaveable(current) { mutableIntStateOf(current.momPercent) }
    var answerBy by rememberSaveable(stateSaver = AnswerByPickSaver) { mutableStateOf(AnswerByPick.DEFAULT) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(Res.string.settings_split_ratio_title)) },
        text = {
            // Scrolls: with the deadline's chips the dialog outgrows a small phone at large text.
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                Text(
                    text = stringResource(
                        Res.string.settings_split_ratio_value_named,
                        parentNames.labelFor(SLOT_MOM),
                        momPercent,
                        parentNames.labelFor(SLOT_DAD),
                        SPLIT_WHOLE_PERCENT - momPercent
                    ),
                    style = MaterialTheme.typography.titleMedium
                )
                Slider(
                    value = momPercent.toFloat(),
                    onValueChange = { momPercent = it.roundToInt() },
                    valueRange = 0f..SPLIT_WHOLE_PERCENT.toFloat(),
                    steps = SPLIT_SLIDER_STEPS
                )
                Text(
                    text = stringResource(Res.string.settings_split_ratio_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                // The split's own deadline, as every form that asks the co-parent something has
                // one (phase 3a). No cap: nothing on a date makes a split's answer come too late.
                if (proposes) {
                    AnswerByField(
                        offer = rememberAnswerByOffer(capMillis = null),
                        selected = answerBy,
                        onSelect = { answerBy = it },
                        coParentName = parentNames.coParentLabel(),
                        modifier = Modifier.padding(top = Spacing.L)
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(SplitRatio.ofMomPercent(momPercent), answerBy.takeIf { proposes }) }) {
                Text(stringResource(Res.string.settings_family_kind_save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(Res.string.settings_family_kind_cancel))
            }
        }
    )
}

/**
 * The two slot identifiers, never shown as words — [ParentNames] turns each into that person's
 * name. See CLAUDE.md: `"mom"`/`"dad"` are schema identifiers and the app never prints them.
 */
private const val SLOT_MOM = "mom"
private const val SLOT_DAD = "dad"

/** A whole share, as a percent. */
private const val SPLIT_WHOLE_PERCENT = 100

/**
 * Stops on the slider: every 5 %.
 *
 * `steps` counts the stops *between* the ends, so twenty 5 % intervals give nineteen. Whole
 * single percents would be a slider nobody can land on with a thumb.
 */
private const val SPLIT_SLIDER_STEPS = 19
