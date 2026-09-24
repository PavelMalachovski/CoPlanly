package com.coparently.app.presentation.custody

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.coparently.app.R
import com.coparently.app.domain.custody.SeasonalLayer
import com.coparently.app.presentation.common.GroupLabel
import com.coparently.app.presentation.common.ParentNames
import com.coparently.app.presentation.common.SectionGroup
import com.coparently.app.presentation.common.SectionRow
import com.coparently.app.presentation.common.asString
import com.coparently.app.presentation.common.rememberParentNames
import com.coparently.app.presentation.parentingplan.PlanReferenceCard
import com.coparently.app.presentation.parentingplan.coParentLabel
import com.coparently.app.presentation.theme.ParentColors
import com.coparently.app.presentation.theme.Spacing
import com.coparently.app.presentation.theme.dimensions
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/**
 * Which layer the editor is open on: [layerId] null for a new one, opened on [draft]. [fromPlan]
 * when it was opened for an agreed parenting-plan answer (MON-21), which it then quotes and cites.
 */
private data class EditorTarget(
    val layerId: String?,
    val draft: SeasonalLayerDraft,
    val fromPlan: Boolean = false
)

/**
 * The seasonal schedules (MON-14) and the holiday-fairness summary (MON-20), under the custody
 * pattern editor.
 *
 * Each change here is sent on its own — the screen's Save button saves only the base pattern —
 * and goes the way a base-pattern change does: a proposal to the co-parent once the pair shares
 * a schedule, never written onto their calendar unasked. The fairness card only counts; its
 * "Propose a change" opens this section's editor.
 */
@Composable
fun SeasonalScheduleSection(viewModel: SeasonalScheduleViewModel = hiltViewModel()) {
    val dims = dimensions()
    val layers by viewModel.layersState.collectAsState()
    val fairness by viewModel.fairnessState.collectAsState()
    val parentNames = rememberParentNames(viewModel.parents.collectAsState().value)
    var editor by remember { mutableStateOf<EditorTarget?>(null) }
    val openNew = { editor = newLayerTarget() }
    OpenOnceForPlan(layers) { editor = newLayerTarget(fromPlan = true) }

    Column(modifier = Modifier.padding(vertical = dims.paddingMedium)) {
        GroupLabel(text = stringResource(R.string.seasonal_title))
        Text(
            text = stringResource(R.string.seasonal_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = Spacing.XS)
        )
        Spacer(modifier = Modifier.height(dims.paddingSmall))
        layers.planReference?.let {
            PlanReferenceCard(it, parentNames.coParentLabel(), Modifier.padding(bottom = dims.paddingSmall))
        }
        SeasonalLayersGroup(
            state = layers,
            parentNames = parentNames,
            onEdit = { editor = EditorTarget(it.id, SeasonalLayerDraft.of(it)) },
            onAdd = openNew
        )
        layers.message?.let {
            Text(
                text = it.asString(),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(horizontal = Spacing.XS, vertical = dims.paddingSmall)
            )
        }
        Spacer(modifier = Modifier.height(dims.paddingMedium))
        HolidayFairnessCard(
            state = fairness,
            parentNames = parentNames,
            onSelectYear = viewModel::selectYear,
            onProposeChange = openNew.takeIf { layers.hasBasePattern }
        )
    }

    editor?.let { target ->
        LayerEditor(
            target = target,
            state = layers,
            parentNames = parentNames,
            viewModel = viewModel,
            onClose = { editor = null }
        )
    }
}

/** A new layer's editor, opening on the next fortnight; [fromPlan] for one opened from the plan. */
private fun newLayerTarget(fromPlan: Boolean = false): EditorTarget {
    val start = LocalDate.now()
    return EditorTarget(null, SeasonalLayerDraft(from = start, to = start.plusDays(DEFAULT_LAYER_DAYS)), fromPlan)
}

/**
 * Opened from an agreed holiday answer in the parenting plan (MON-21): opens a new layer's editor
 * once, quoting the answer, as soon as there is a base pattern to layer on. Saveable, so a
 * rotation or a return from the date picker does not open it a second time.
 */
@Composable
private fun OpenOnceForPlan(state: SeasonalLayersUiState, onOpen: () -> Unit) {
    var opened by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(state.planReference, state.hasBasePattern) {
        if (state.planReference != null && state.hasBasePattern && !opened) {
            opened = true
            onOpen()
        }
    }
}

/** The editor for one layer, new or existing; [onClose] runs after a save, a delete or a dismiss. */
@Composable
private fun LayerEditor(
    target: EditorTarget,
    state: SeasonalLayersUiState,
    parentNames: ParentNames,
    viewModel: SeasonalScheduleViewModel,
    onClose: () -> Unit
) {
    SeasonalLayerDialog(
        initial = target.draft,
        isEditing = target.layerId != null,
        suggestions = state.suggestions,
        parentNames = parentNames,
        reference = state.planReference.takeIf { target.fromPlan },
        onConfirm = { draft ->
            viewModel.saveLayer(draft, target.layerId, citePlan = target.fromPlan)
            onClose()
        },
        onDelete = target.layerId?.let { id ->
            {
                viewModel.deleteLayer(id)
                onClose()
            }
        },
        onDismiss = onClose
    )
}

/**
 * The layers as one [SectionGroup], with the row that adds one — or the sentence that says why
 * none can be added yet.
 */
@Composable
private fun SeasonalLayersGroup(
    state: SeasonalLayersUiState,
    parentNames: ParentNames,
    onEdit: (SeasonalLayer) -> Unit,
    onAdd: () -> Unit
) {
    val coParent = parentNames.parents.coParent?.let { parentNames.labelForUid(it.uid) }
        ?: parentNames.coParentFallback
    val pending = when {
        state.coParentProposalPending -> stringResource(R.string.seasonal_pending_theirs, coParent)
        state.ownProposalPending -> stringResource(R.string.seasonal_pending_mine, coParent)
        else -> null
    }
    SectionGroup {
        if (!state.hasBasePattern) {
            SectionRow(title = stringResource(R.string.seasonal_needs_base_pattern), icon = Icons.Default.DateRange)
        } else {
            pending?.let {
                SectionRow(title = it, icon = Icons.Default.DateRange)
                Divider()
            }
            state.layers.sortedBy { it.fromDate }.forEach { layer ->
                SectionRow(
                    title = layer.name,
                    supporting = layerSummary(layer, parentNames),
                    leading = { LayerDot(layer) },
                    onClick = { onEdit(layer) }.takeIf { !state.isSaving }
                )
                Divider()
            }
            SectionRow(
                title = stringResource(R.string.seasonal_add),
                icon = Icons.Default.Add,
                onClick = onAdd.takeIf { !state.isSaving }
            )
        }
    }
}

/** A dot in the colour of whoever has the layer's first day. */
@Composable
private fun LayerDot(layer: SeasonalLayer) {
    Box(
        modifier = Modifier
            .size(12.dp)
            .background(ParentColors.fill(layer.custodyFor(layer.fromDate)), CircleShape)
    )
}

/** "1 Jul 2026 – 31 Aug 2026 · Alice 31 · Bob 31": the range and how its days are shared. */
@Composable
private fun layerSummary(layer: SeasonalLayer, parentNames: ParentNames): String {
    val format = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)
    val counts = (0 until layer.spanDays).map { layer.custodyFor(layer.fromDate.plusDays(it)) }
        .groupingBy { it }
        .eachCount()
    val shares = listOf("mom", "dad").mapNotNull { slot ->
        counts[slot]?.let { "${parentNames.labelFor(slot)} $it" }
    }
    return (listOf("${layer.fromDate.format(format)} – ${layer.toDate.format(format)}") + shares)
        .joinToString(" · ")
}

/** How long a new layer runs before the parent picks its dates. */
private const val DEFAULT_LAYER_DAYS = 13L
