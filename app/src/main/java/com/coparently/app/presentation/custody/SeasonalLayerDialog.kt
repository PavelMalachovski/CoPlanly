package com.coparently.app.presentation.custody

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.coparently.app.R
import com.coparently.app.domain.custody.ContactWindow
import com.coparently.app.domain.holidays.VacationSuggestion
import com.coparently.app.domain.parentingplan.PlanReference
import com.coparently.app.presentation.common.LocalDatePickerDialog
import com.coparently.app.presentation.common.ParentNames
import com.coparently.app.presentation.common.PillChip
import com.coparently.app.presentation.parentingplan.PlanReferenceCard
import com.coparently.app.presentation.parentingplan.coParentLabel
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

/** Which end of the range the date picker is open on. */
private enum class RangeEnd { FROM, TO }

/**
 * The editor for one seasonal layer (MON-14): a name, a range, and how its days are shared.
 *
 * "Fill from school holidays" offers the upcoming breaks of the parent's own holiday calendar as
 * the range — a suggestion the parent confirms, never a schedule applied by itself. What the
 * dialog saves goes through the proposal flow like any other schedule change.
 *
 * @param initial The draft to open on.
 * @param isEditing True for an existing layer, which may keep its current pattern.
 * @param suggestions School vacations to fill the range from; the row is absent when empty.
 * @param parentNames Resolves a slot to that parent's name.
 * @param reference The agreed parenting-plan answer the editor was opened for (MON-21), quoted
 *   read-only above the fields; null for an editor opened any other way.
 * @param onConfirm Receives the draft to save.
 * @param onDelete Deletes the layer; null for a new one.
 * @param onDismiss Closes the dialog.
 */
@Composable
@Suppress("LongParameterList") // one dialog, its content and its three outcomes
fun SeasonalLayerDialog(
    initial: SeasonalLayerDraft,
    isEditing: Boolean,
    suggestions: List<VacationSuggestion>,
    parentNames: ParentNames,
    reference: PlanReference? = null,
    onConfirm: (SeasonalLayerDraft) -> Unit,
    onDelete: (() -> Unit)?,
    onDismiss: () -> Unit
) {
    var draft by remember { mutableStateOf(initial) }
    var picking by remember { mutableStateOf<RangeEnd?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            val title = if (isEditing) R.string.seasonal_dialog_edit_title else R.string.seasonal_dialog_add_title
            Text(stringResource(title))
        },
        text = {
            LayerEditorBody(
                draft = draft,
                isEditing = isEditing,
                suggestions = suggestions,
                parentNames = parentNames,
                reference = reference,
                onChange = { draft = it },
                onPick = { picking = it }
            )
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(draft) }, enabled = draft.isValid) {
                Text(stringResource(R.string.seasonal_confirm))
            }
        },
        dismissButton = {
            Row {
                onDelete?.let {
                    TextButton(onClick = it) {
                        Text(stringResource(R.string.seasonal_delete), color = MaterialTheme.colorScheme.error)
                    }
                }
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.seasonal_cancel)) }
            }
        }
    )

    picking?.let { end ->
        LocalDatePickerDialog(
            initialDate = if (end == RangeEnd.FROM) draft.from else draft.to,
            confirmLabel = stringResource(R.string.seasonal_ok),
            dismissLabel = stringResource(R.string.seasonal_cancel),
            onConfirm = { date ->
                draft = if (end == RangeEnd.FROM) draft.copy(from = date) else draft.copy(to = date)
            },
            onDismiss = { picking = null }
        )
    }
}

/** The dialog's content: name, range, school-holiday suggestions, shape, validation. */
@Composable
@Suppress("LongParameterList") // the draft, what it may offer, and its two callbacks
private fun LayerEditorBody(
    draft: SeasonalLayerDraft,
    isEditing: Boolean,
    suggestions: List<VacationSuggestion>,
    parentNames: ParentNames,
    reference: PlanReference?,
    onChange: (SeasonalLayerDraft) -> Unit,
    onPick: (RangeEnd) -> Unit
) {
    Column(
        modifier = Modifier.verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // MON-21: the agreed answer, above the fields the parent fills in from it by hand.
        reference?.let { PlanReferenceCard(reference = it, coParentName = parentNames.coParentLabel()) }
        OutlinedTextField(
            value = draft.name,
            onValueChange = { onChange(draft.copy(name = it.take(MAX_NAME_INPUT))) },
            label = { Text(stringResource(R.string.seasonal_name_label)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        LayerRange(draft = draft, onPick = onPick)
        if (suggestions.isNotEmpty()) {
            LayerSuggestions(suggestions) { suggestion ->
                onChange(
                    draft.copy(
                        from = suggestion.dates.start,
                        to = suggestion.dates.endInclusive,
                        name = draft.name.ifBlank { suggestion.displayName() }
                    )
                )
            }
        }
        LayerShapePicker(draft, isEditing, parentNames, onChange)
        if (!draft.isValid && draft.name.isNotBlank()) {
            Text(
                text = stringResource(R.string.seasonal_invalid_range),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error
            )
        }
    }
}

/** The two ends of the range, each opening the date picker. */
@Composable
private fun LayerRange(draft: SeasonalLayerDraft, onPick: (RangeEnd) -> Unit) {
    val format = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        TextButton(onClick = { onPick(RangeEnd.FROM) }) {
            Text(stringResource(R.string.seasonal_from, draft.from.format(format)))
        }
        TextButton(onClick = { onPick(RangeEnd.TO) }) {
            Text(stringResource(R.string.seasonal_to, draft.to.format(format)))
        }
    }
}

/** "Fill from school holidays": one chip per upcoming break. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun LayerSuggestions(suggestions: List<VacationSuggestion>, onPick: (VacationSuggestion) -> Unit) {
    val format = DateTimeFormatter.ofLocalizedDate(FormatStyle.SHORT)
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            text = stringResource(R.string.seasonal_fill_from_school),
            style = MaterialTheme.typography.labelLarge
        )
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            suggestions.forEach { suggestion ->
                PillChip(
                    label = "${suggestion.displayName()} ${suggestion.dates.start.format(format)}" +
                        "–${suggestion.dates.endInclusive.format(format)}",
                    onClick = { onPick(suggestion) }
                )
            }
        }
    }
}

/** How the days are shared, and which parent has them or starts. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun LayerShapePicker(
    draft: SeasonalLayerDraft,
    isEditing: Boolean,
    parentNames: ParentNames,
    onChange: (SeasonalLayerDraft) -> Unit
) {
    val shapes = LayerShape.entries.filter { isEditing || it != LayerShape.KEEP }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(text = stringResource(R.string.seasonal_shape_label), style = MaterialTheme.typography.labelLarge)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            shapes.forEach { shape ->
                FilterChip(
                    selected = draft.shape == shape,
                    onClick = { onChange(draft.copy(shape = shape)) },
                    label = { Text(stringResource(shape.label())) }
                )
            }
        }
        if (draft.shape != LayerShape.KEEP) {
            Text(
                text = stringResource(
                    if (draft.shape == LayerShape.ALL_WITH) R.string.seasonal_with else R.string.seasonal_starts_with
                ),
                style = MaterialTheme.typography.labelLarge
            )
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf(ContactWindow.SLOT_ONE, ContactWindow.SLOT_TWO).forEach { slot ->
                    FilterChip(
                        selected = draft.firstSlot == slot,
                        onClick = { onChange(draft.copy(firstSlot = slot)) },
                        label = { Text(parentNames.labelFor(slot)) }
                    )
                }
            }
        }
    }
}

/** A shape's chip label. */
private fun LayerShape.label(): Int = when (this) {
    LayerShape.KEEP -> R.string.seasonal_shape_keep
    LayerShape.ALL_WITH -> R.string.seasonal_shape_all
    LayerShape.ALTERNATING_WEEKS -> R.string.seasonal_shape_weeks
    LayerShape.SPLIT_IN_HALF -> R.string.seasonal_shape_half
}

/** The break's name in the device language when it is the calendar's own, English otherwise. */
private fun VacationSuggestion.displayName(): String =
    if (Locale.getDefault().language == localLanguage) nameLocal else nameEn

/** Longer input than a layer name may hold is cut rather than refused mid-word by validation. */
private const val MAX_NAME_INPUT = 80
