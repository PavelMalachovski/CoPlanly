package com.coparently.app.presentation.family

import android.content.Context
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ChildCare
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Event
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.Pets
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.hilt.navigation.compose.hiltViewModel
import com.coparently.app.R
import com.coparently.app.data.family.AssignOutcome
import com.coparently.app.data.family.FamilyOption
import com.coparently.app.data.family.UnassignedKind
import com.coparently.app.data.family.UnassignedRecord
import com.coparently.app.presentation.common.EmptyState
import com.coparently.app.presentation.common.GroupLabel
import com.coparently.app.presentation.common.SectionGroup
import com.coparently.app.presentation.common.SectionRow
import com.coparently.app.presentation.custody.labelRes
import com.coparently.app.presentation.expenses.currencyFormat
import com.coparently.app.presentation.theme.IconSizes
import com.coparently.app.presentation.theme.LayoutConstants
import com.coparently.app.presentation.theme.Spacing
import com.coparently.app.presentation.theme.bodyLargeEmphasized
import com.coparently.app.utils.localizedDate

/** A record's day, in the reader's own order: "Aug 3, 2026", "3. 8. 2026". */
private const val DATE_SKELETON = "yMMMd"

/** Between an expense's amount and its day. */
private const val DETAIL_JOIN = " · "

/** The dialog's choice for "Keep it only mine": a family id is never blank. */
private const val KEEP_ONLY_MINE = ""

/**
 * Settings → Family → *Records without a family* (owner decision, October 2026): the parent's own
 * records that name no family while they co-parent — children and pets, events, expenses and a
 * custody schedule — and, for each, a choice of one of their live families or "Keep it only mine".
 *
 * Until it is answered a record is "Only me": shared with nobody and marked on every screen. A
 * record of a family that has ended is not here (it is history), and neither is a private event.
 * The list empties as records take a family or are kept; the empty state says what the kept ones
 * look like.
 *
 * @param onNavigateUp Returns to the screen that opened it — Settings, or Home's question.
 * @param viewModel Screen state.
 */
@Composable
fun UnassignedRecordsScreen(
    onNavigateUp: () -> Unit,
    viewModel: UnassignedRecordsViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()
    val busy by viewModel.busy.collectAsState()
    val notice by viewModel.notice.collectAsState()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    var choosingKey by rememberSaveable { mutableStateOf<String?>(null) }

    LaunchedEffect(notice) {
        notice?.let { said ->
            snackbarHostState.showSnackbar(noticeText(context, said, state))
            viewModel.noticeShown()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = { UnassignedTopBar(onNavigateUp) }
    ) { padding ->
        val bodyModifier = Modifier
            .fillMaxSize()
            .padding(padding)
        when {
            // Room answers at once; drawing the empty state first would claim there is nothing.
            state.loading -> Unit
            state.records.isEmpty() -> EmptyState(
                icon = Icons.Default.CheckCircle,
                title = stringResource(R.string.unassigned_records_empty_title),
                description = stringResource(
                    R.string.unassigned_records_empty_description,
                    stringResource(R.string.record_place_only_me)
                ),
                modifier = bodyModifier
            )
            else -> RecordsBody(
                state = state,
                enabled = !busy,
                onChoose = { choosingKey = it.key },
                modifier = bodyModifier
            )
        }
    }

    // Looked up again on every frame, so a record that took a family elsewhere closes its dialog.
    state.records.firstOrNull { it.key == choosingKey }?.let { record ->
        ChooseFamilyDialog(
            record = record,
            state = state,
            onAssign = { familyId ->
                choosingKey = null
                viewModel.assign(record, familyId)
            },
            onKeep = {
                choosingKey = null
                viewModel.keep(record)
            },
            onDismiss = { choosingKey = null }
        )
    }
}

/** The screen's title and the way back. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun UnassignedTopBar(onNavigateUp: () -> Unit) {
    TopAppBar(
        title = { Text(stringResource(R.string.unassigned_records_title)) },
        navigationIcon = {
            IconButton(onClick = onNavigateUp) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = stringResource(R.string.common_back)
                )
            }
        }
    )
}

/**
 * The explanation, then one group per kind of record, each row opening the choice.
 *
 * @param state What to list.
 * @param enabled False while an answer is being written.
 * @param onChoose Opens the choice for a record.
 * @param modifier Carries the Scaffold padding.
 */
@Composable
private fun RecordsBody(
    state: UnassignedRecordsUiState,
    enabled: Boolean,
    onChoose: (UnassignedRecord) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .verticalScroll(rememberScrollState())
            .padding(Spacing.L),
        verticalArrangement = Arrangement.spacedBy(Spacing.L)
    ) {
        Text(
            text = stringResource(R.string.unassigned_records_intro),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        state.groups.forEach { (group, records) ->
            Column {
                GroupLabel(stringResource(group.labelResource()))
                SectionGroup {
                    records.forEachIndexed { index, record ->
                        if (index > 0) Divider()
                        RecordRow(record = record, onClick = { onChoose(record) }.takeIf { enabled })
                    }
                }
            }
        }
    }
}

/** One record: what it is, its day or amount, and a chevron to the choice. */
@Composable
private fun RecordRow(record: UnassignedRecord, onClick: (() -> Unit)?) {
    SectionRow(
        icon = record.kind.icon(),
        title = recordTitle(record),
        supporting = recordDetail(record),
        onClick = onClick,
        trailing = {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(IconSizes.Standard)
            )
        }
    )
}

/**
 * Where [record] belongs: one of the families it may go to ([UnassignedRecordsUiState.choicesFor]),
 * or "Keep it only mine". A custody schedule says why only the family on screen is offered.
 *
 * @param record The record being answered.
 * @param state The families, named.
 * @param onAssign Gives the record to the chosen family's id.
 * @param onKeep Keeps the record only the parent's.
 * @param onDismiss Closes without an answer.
 */
@Composable
private fun ChooseFamilyDialog(
    record: UnassignedRecord,
    state: UnassignedRecordsUiState,
    onAssign: (String) -> Unit,
    onKeep: () -> Unit,
    onDismiss: () -> Unit
) {
    var choice by rememberSaveable(record.key) { mutableStateOf<String?>(null) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.unassigned_records_choose_title)) },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                Text(text = recordTitle(record), style = MaterialTheme.typography.bodyLargeEmphasized)
                if (record.kind == UnassignedKind.CUSTODY) {
                    Text(
                        text = stringResource(R.string.unassigned_records_custody_note),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = Spacing.S)
                    )
                }
                state.choicesFor(record).forEach { family ->
                    ChoiceRow(
                        label = familyChoiceLabel(family),
                        selected = choice == family.familyId,
                        onSelect = { choice = family.familyId }
                    )
                }
                ChoiceRow(
                    label = stringResource(R.string.unassigned_records_keep_mine),
                    selected = choice == KEEP_ONLY_MINE,
                    onSelect = { choice = KEEP_ONLY_MINE },
                    hint = stringResource(R.string.unassigned_records_keep_mine_hint)
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = choice != null,
                onClick = { choice?.let { if (it == KEEP_ONLY_MINE) onKeep() else onAssign(it) } }
            ) {
                Text(stringResource(R.string.unassigned_records_save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.common_dialog_cancel))
            }
        }
    )
}

/**
 * One choice in [ChooseFamilyDialog]: a radio button and its label, the whole row the target, as
 * the family switcher's rows are.
 */
@Composable
private fun ChoiceRow(label: String, selected: Boolean, onSelect: () -> Unit, hint: String? = null) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .selectable(selected = selected, role = Role.RadioButton, onClick = onSelect)
            .defaultMinSize(minHeight = LayoutConstants.MIN_TOUCH_TARGET)
            .padding(vertical = Spacing.S),
        horizontalArrangement = Arrangement.spacedBy(Spacing.L),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(selected = selected, onClick = null)
        Column(modifier = Modifier.weight(1f)) {
            Text(text = label)
            if (hint != null) {
                Text(
                    text = hint,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

/** A family, named by its co-parent — never by a uid. */
@Composable
private fun familyChoiceLabel(family: FamilyOption): String =
    family.partnerName.takeIf { it.isNotBlank() }
        ?.let { stringResource(R.string.unassigned_records_family_with, it) }
        ?: stringResource(R.string.unassigned_records_family_unnamed)

/** The record's name: a title or a name, or a schedule's pattern. */
@Composable
private fun recordTitle(record: UnassignedRecord): String =
    record.custodyType?.let { stringResource(it.labelRes()) } ?: record.title

/** The record's supporting line: what kind it is, its day, its amount. */
@Composable
private fun recordDetail(record: UnassignedRecord): String? {
    val day = record.date?.format(localizedDate(DATE_SKELETON))
    return when (record.kind) {
        UnassignedKind.CHILD -> stringResource(R.string.unassigned_records_kind_child)
        UnassignedKind.PET -> stringResource(R.string.unassigned_records_kind_pet)
        UnassignedKind.EVENT -> day
        UnassignedKind.EXPENSE -> listOfNotNull(amountText(record), day).joinToString(DETAIL_JOIN).ifEmpty { null }
        UnassignedKind.CUSTODY -> day?.let { stringResource(R.string.unassigned_records_custody_starts, it) }
    }
}

/** An expense's amount in its own currency, formatted for the reader. */
private fun amountText(record: UnassignedRecord): String? {
    val amount = record.amount ?: return null
    return record.currency?.let { currencyFormat(it).format(amount) }
}

/** What [notice] says, for the snackbar; [state] names the family it went to. */
private fun noticeText(context: Context, notice: UnassignedNotice, state: UnassignedRecordsUiState): String =
    when (notice) {
        UnassignedNotice.Kept -> context.getString(R.string.unassigned_records_notice_kept)
        is UnassignedNotice.Assigned -> {
            val name = state.family(notice.familyId)?.partnerName.orEmpty()
            when (notice.outcome) {
                AssignOutcome.ASSIGNED -> named(
                    context,
                    name,
                    R.string.unassigned_records_notice_assigned,
                    R.string.unassigned_records_notice_assigned_unnamed
                )
                AssignOutcome.PROPOSED -> named(
                    context,
                    name,
                    R.string.unassigned_records_notice_proposed,
                    R.string.unassigned_records_notice_proposed_unnamed
                )
                AssignOutcome.PROPOSAL_WAITING ->
                    context.getString(R.string.unassigned_records_notice_proposal_waiting)
                AssignOutcome.NOT_SENT -> context.getString(R.string.unassigned_records_notice_not_sent)
                AssignOutcome.GONE -> context.getString(R.string.unassigned_records_notice_gone)
            }
        }
    }

/** [withName] with [name], or [unnamed] when the co-parent's name is not known. */
private fun named(context: Context, name: String, @StringRes withName: Int, @StringRes unnamed: Int): String =
    if (name.isBlank()) context.getString(unnamed) else context.getString(withName, name)

private fun UnassignedKind.icon(): ImageVector = when (this) {
    UnassignedKind.CHILD -> Icons.Default.ChildCare
    UnassignedKind.PET -> Icons.Default.Pets
    UnassignedKind.EVENT -> Icons.Default.Event
    UnassignedKind.EXPENSE -> Icons.Default.Payments
    UnassignedKind.CUSTODY -> Icons.Default.DateRange
}

@StringRes
private fun UnassignedGroup.labelResource(): Int = when (this) {
    UnassignedGroup.CHILDREN_AND_PETS -> R.string.unassigned_records_group_children_pets
    UnassignedGroup.EVENTS -> R.string.unassigned_records_group_events
    UnassignedGroup.EXPENSES -> R.string.unassigned_records_group_expenses
    UnassignedGroup.CUSTODY -> R.string.unassigned_records_group_custody
}
