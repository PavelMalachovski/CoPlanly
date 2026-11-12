package com.coparently.app.presentation.export

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Assignment
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.TableChart
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.coparently.app.data.export.ExportedFile
import com.coparently.app.domain.export.ExportFormat
import com.coparently.app.domain.export.JournalLabels
import com.coparently.app.domain.export.PlanLabels
import com.coparently.app.domain.export.RecordActions
import com.coparently.app.domain.export.RecordColumns
import com.coparently.app.domain.export.RecordLabels
import com.coparently.app.domain.export.VerificationLabels
import com.coparently.app.domain.parentingplan.ParentingPlanCatalogue
import com.coparently.app.presentation.common.ExpandableDetails
import com.coparently.app.presentation.common.GroupLabel
import com.coparently.app.presentation.common.LocalDatePickerDialog
import com.coparently.app.presentation.common.SectionGroup
import com.coparently.app.presentation.common.SectionRow
import com.coparently.app.presentation.common.UiText
import com.coparently.app.presentation.common.asString
import com.coparently.app.presentation.common.rememberUiTextResolver
import com.coparently.app.presentation.parentingplan.PlanStrings
import com.coparently.app.presentation.platform.LocalShareSheet
import com.coparently.app.presentation.theme.ScreenContainerColor
import com.coparently.app.presentation.theme.Spacing
import com.coparently.app.resources.Res
import com.coparently.app.resources.export_action_created
import com.coparently.app.resources.export_action_current
import com.coparently.app.resources.export_action_deleted
import com.coparently.app.resources.export_action_not_sent
import com.coparently.app.resources.export_action_recorded
import com.coparently.app.resources.export_action_sent
import com.coparently.app.resources.export_action_server_recorded
import com.coparently.app.resources.export_action_updated
import com.coparently.app.resources.export_back
import com.coparently.app.resources.export_col_action
import com.coparently.app.resources.export_col_amount
import com.coparently.app.resources.export_col_by
import com.coparently.app.resources.export_col_currency
import com.coparently.app.resources.export_col_device_time
import com.coparently.app.resources.export_col_ends
import com.coparently.app.resources.export_col_item
import com.coparently.app.resources.export_col_notes
import com.coparently.app.resources.export_col_parent
import com.coparently.app.resources.export_col_revision
import com.coparently.app.resources.export_col_section
import com.coparently.app.resources.export_col_server_time
import com.coparently.app.resources.export_col_starts
import com.coparently.app.resources.export_col_text
import com.coparently.app.resources.export_csv
import com.coparently.app.resources.export_csv_supporting
import com.coparently.app.resources.export_from
import com.coparently.app.resources.export_include_journal
import com.coparently.app.resources.export_include_journal_supporting
import com.coparently.app.resources.export_include_label
import com.coparently.app.resources.export_include_plan
import com.coparently.app.resources.export_include_plan_supporting
import com.coparently.app.resources.export_intro
import com.coparently.app.resources.export_journal_clock_note
import com.coparently.app.resources.export_journal_edited
import com.coparently.app.resources.export_journal_none
import com.coparently.app.resources.export_journal_private_note
import com.coparently.app.resources.export_journal_written
import com.coparently.app.resources.export_pdf
import com.coparently.app.resources.export_pdf_supporting
import com.coparently.app.resources.export_pick_cancel
import com.coparently.app.resources.export_pick_ok
import com.coparently.app.resources.export_plan_cited
import com.coparently.app.resources.export_plan_current_state
import com.coparently.app.resources.export_plan_last_changed
import com.coparently.app.resources.export_plan_none
import com.coparently.app.resources.export_plan_not_agreed
import com.coparently.app.resources.export_plan_not_from_server
import com.coparently.app.resources.export_plan_retired
import com.coparently.app.resources.export_plan_unsent_here
import com.coparently.app.resources.export_range_label
import com.coparently.app.resources.export_record_generated
import com.coparently.app.resources.export_record_incomplete
import com.coparently.app.resources.export_record_no_server_time
import com.coparently.app.resources.export_record_not_on_server
import com.coparently.app.resources.export_record_nothing
import com.coparently.app.resources.export_record_page
import com.coparently.app.resources.export_record_parents
import com.coparently.app.resources.export_record_period
import com.coparently.app.resources.export_record_revision
import com.coparently.app.resources.export_record_section_events
import com.coparently.app.resources.export_record_section_expenses
import com.coparently.app.resources.export_record_section_messages
import com.coparently.app.resources.export_record_time_zone
import com.coparently.app.resources.export_record_title
import com.coparently.app.resources.export_share_title
import com.coparently.app.resources.export_statement_limits
import com.coparently.app.resources.export_statement_messages
import com.coparently.app.resources.export_statement_revisions
import com.coparently.app.resources.export_statement_what
import com.coparently.app.resources.export_title
import com.coparently.app.resources.export_to
import com.coparently.app.resources.export_unregistered_body
import com.coparently.app.resources.export_unregistered_not_now
import com.coparently.app.resources.export_unregistered_share
import com.coparently.app.resources.export_unregistered_title
import com.coparently.app.resources.export_verify_at
import com.coparently.app.resources.export_verify_explainer
import com.coparently.app.resources.export_verify_instruction
import com.coparently.app.resources.export_verify_instruction_no_url
import com.coparently.app.resources.export_verify_not_registered
import com.coparently.app.resources.export_verify_not_registered_short
import com.coparently.app.resources.export_verify_record_id
import com.coparently.app.resources.export_what_it_says
import com.coparently.app.resources.export_working
import com.coparently.app.resources.journal_title
import com.coparently.app.resources.parent_label_coparent
import com.coparently.app.resources.parent_label_unknown
import com.coparently.app.resources.parent_label_you
import com.coparently.app.resources.parenting_plan_disclaimer
import com.coparently.app.resources.parenting_plan_not_answered
import com.coparently.app.resources.parenting_plan_status_agreed
import com.coparently.app.resources.parenting_plan_title
import kotlinx.coroutines.flow.Flow
import kotlinx.datetime.LocalDate
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

/**
 * Exporting the communication record (MON-3): a period, and a CSV or a PDF of it.
 *
 * A detail screen off Settings → Family, beside the parenting plan — the other document two
 * parents may hand to a court. The statement the file prints about itself is shown here too,
 * before the buttons, so a parent knows what they are about to hand over; a button that promised
 * more than the file says would be design item 8's forbidden affordance.
 *
 * @param onNavigateBack Pops back to Settings.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExportScreen(
    onNavigateBack: () -> Unit,
    viewModel: ExportViewModel = koinViewModel()
) {
    val state by viewModel.state.collectAsState()
    val textResolver = rememberUiTextResolver()
    val snackbarHostState = remember { SnackbarHostState() }
    val labels = rememberRecordLabels()
    val fallbacks = NameFallbacks(
        you = stringResource(Res.string.parent_label_you),
        coParent = stringResource(Res.string.parent_label_coparent),
        unknown = stringResource(Res.string.parent_label_unknown)
    )
    var picking by rememberSaveable { mutableStateOf<RangeEnd?>(null) }
    // A phone that cannot draw the file or hand it on offers no format to export (design item 8;
    // the iPhone's stand-ins for now).
    val shareSheet = LocalShareSheet.current
    val canExport = remember(shareSheet) { viewModel.exportAvailable && shareSheet.available }

    ShareWhenFinished(viewModel.files)
    LaunchedEffect(state.error) {
        state.error?.let {
            snackbarHostState.showSnackbar(it.asString(textResolver))
            viewModel.errorShown()
        }
    }

    picking?.let { end ->
        RangeDatePicker(
            initial = if (end == RangeEnd.FROM) state.from else state.to,
            onPicked = { date ->
                if (end == RangeEnd.FROM) viewModel.setFrom(date) else viewModel.setTo(date)
                picking = null
            },
            onDismiss = { picking = null }
        )
    }

    Scaffold(
        containerColor = ScreenContainerColor,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text(stringResource(Res.string.export_title)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(Res.string.export_back)
                        )
                    }
                }
            )
        }
    ) { padding ->
        ExportContent(
            state = state,
            statement = labels.statement,
            modifier = Modifier.padding(padding),
            actions = ExportActions(
                onPick = { end -> picking = end },
                onIncludePlan = viewModel::setIncludePlan,
                onIncludeJournal = viewModel::setIncludeJournal,
                onExport = { format: ExportFormat -> viewModel.export(format, labels, fallbacks) }.takeIf { canExport }
            )
        )
    }
}

/**
 * What the export screen's controls do, as one value — the screen has more of them than a
 * composable should take one parameter each.
 */
private class ExportActions(
    val onPick: (RangeEnd) -> Unit,
    val onIncludePlan: (Boolean) -> Unit,
    val onIncludeJournal: (Boolean) -> Unit,
    /** Null where the phone cannot export, which leaves the format rows out. */
    val onExport: ((ExportFormat) -> Unit)?
)

/**
 * The range, which optional sections go in, the two formats, and, folded under them, the statement
 * the file makes about itself.
 */
@Composable
private fun ExportContent(
    state: ExportUiState,
    statement: List<String>,
    actions: ExportActions,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = Spacing.L, vertical = Spacing.S),
        verticalArrangement = Arrangement.spacedBy(Spacing.L)
    ) {
        Text(
            text = stringResource(Res.string.export_intro),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        RangeGroup(state = state, onPick = actions.onPick)
        IncludeGroup(state = state, actions = actions)
        // The formats before the statement (UX audit M10): the screen's one action used to sit
        // under five paragraphs a parent had to scroll past. The file prints them first anyway.
        actions.onExport?.let { onExport ->
            SectionGroup {
                ExportRow(format = ExportFormat.PDF, working = state.working, onClick = onExport)
                Divider()
                ExportRow(format = ExportFormat.CSV, working = state.working, onClick = onExport)
            }
        }
        ExpandableDetails(label = stringResource(Res.string.export_what_it_says)) {
            Surface(
                color = MaterialTheme.colorScheme.surfaceContainer,
                shape = MaterialTheme.shapes.large
            ) {
                Column(
                    modifier = Modifier.padding(Spacing.L),
                    verticalArrangement = Arrangement.spacedBy(Spacing.S)
                ) {
                    statement.forEach { paragraph ->
                        Text(text = paragraph, style = MaterialTheme.typography.bodySmall)
                    }
                    Text(
                        text = stringResource(Res.string.export_verify_explainer),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

/** The two ends of the period, each opening a date picker while no export runs. */
@Composable
private fun RangeGroup(state: ExportUiState, onPick: (RangeEnd) -> Unit) {
    Column {
        GroupLabel(stringResource(Res.string.export_range_label))
        SectionGroup {
            SectionRow(
                icon = Icons.Default.DateRange,
                title = stringResource(Res.string.export_from),
                supporting = UiText.Date(state.from).asString(),
                onClick = { onPick(RangeEnd.FROM) }.takeIf { state.working == null }
            )
            Divider()
            SectionRow(
                icon = Icons.Default.DateRange,
                title = stringResource(Res.string.export_to),
                supporting = UiText.Date(state.to).asString(),
                onClick = { onPick(RangeEnd.TO) }.takeIf { state.working == null }
            )
        }
    }
}

/**
 * Which optional sections go into the file.
 *
 * The parenting plan has no period, so its row says it is printed as it stands now, whatever range
 * is picked above. The private journal (MON-22) is off until the parent ticks it, and its row says
 * whose entries go in and how the file labels them — nobody else has ever read them.
 */
@Composable
private fun IncludeGroup(state: ExportUiState, actions: ExportActions) {
    val enabled = state.working == null
    Column {
        GroupLabel(stringResource(Res.string.export_include_label))
        SectionGroup {
            IncludeRow(
                included = state.includePlan,
                enabled = enabled,
                onChange = actions.onIncludePlan,
                icon = Icons.AutoMirrored.Filled.Assignment,
                title = stringResource(Res.string.export_include_plan),
                supporting = stringResource(Res.string.export_include_plan_supporting)
            )
            Divider()
            IncludeRow(
                included = state.includeJournal,
                enabled = enabled,
                onChange = actions.onIncludeJournal,
                icon = Icons.Default.Lock,
                title = stringResource(Res.string.export_include_journal),
                supporting = stringResource(Res.string.export_include_journal_supporting)
            )
        }
    }
}

/** One checkbox row: the whole row toggles, and the box only shows the state. */
@Composable
@Suppress("LongParameterList") // one checkbox row's anatomy, expressed as one parameter list
private fun IncludeRow(
    included: Boolean,
    enabled: Boolean,
    onChange: (Boolean) -> Unit,
    icon: ImageVector,
    title: String,
    supporting: String
) {
    SectionRow(
        modifier = Modifier.toggleable(
            value = included,
            enabled = enabled,
            role = Role.Checkbox,
            onValueChange = onChange
        ),
        icon = icon,
        title = title,
        supporting = supporting,
        trailing = { Checkbox(checked = included, onCheckedChange = null, enabled = enabled) }
    )
}

/**
 * Hands each finished file to the share sheet — at once when it was registered, and after
 * [UnregisteredDialog] when it was not (MON-16): the share sheet alone would hand over a file that
 * cannot be verified without a word about it.
 */
@Composable
private fun ShareWhenFinished(files: Flow<FinishedExport>) {
    val shareSheet = LocalShareSheet.current
    val shareTitle = stringResource(Res.string.export_share_title)
    var unregistered by remember { mutableStateOf<ExportedFile?>(null) }

    LaunchedEffect(files) {
        files.collect { finished ->
            if (finished.recordId != null) {
                shareSheet.shareFile(finished.file.uri, finished.file.mimeType, shareTitle)
            } else {
                unregistered = finished.file
            }
        }
    }
    unregistered?.let { file ->
        UnregisteredDialog(
            onShare = {
                unregistered = null
                shareSheet.shareFile(file.uri, file.mimeType, shareTitle)
            },
            onDismiss = { unregistered = null }
        )
    }
}

/**
 * Said before a file that could not be registered is shared: it cannot be verified, it says so on
 * its face, and the parent may share it anyway or try again online.
 */
@Composable
private fun UnregisteredDialog(onShare: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(Res.string.export_unregistered_title)) },
        text = { Text(stringResource(Res.string.export_unregistered_body)) },
        confirmButton = {
            TextButton(onClick = onShare) { Text(stringResource(Res.string.export_unregistered_share)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(Res.string.export_unregistered_not_now)) }
        }
    )
}

/** Which end of the range a date picker is choosing. */
private enum class RangeEnd { FROM, TO }

/** One format's row: its name, what the file is, and a spinner while it is being made. */
@Composable
private fun ExportRow(format: ExportFormat, working: ExportFormat?, onClick: (ExportFormat) -> Unit) {
    val (title, supporting) = when (format) {
        ExportFormat.PDF -> Res.string.export_pdf to Res.string.export_pdf_supporting
        ExportFormat.CSV -> Res.string.export_csv to Res.string.export_csv_supporting
    }
    val spinner: (@Composable () -> Unit)? = if (working == format) {
        @Composable { CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp) }
    } else {
        null
    }
    SectionRow(
        icon = if (format == ExportFormat.PDF) Icons.Default.PictureAsPdf else Icons.Default.TableChart,
        title = stringResource(title),
        supporting = stringResource(if (working == format) Res.string.export_working else supporting),
        // Disabled while either format runs: two exports at once would clear each other's file.
        onClick = { onClick(format) }.takeIf { working == null },
        trailing = spinner
    )
}

/** A date picker for one end of the range; see [LocalDatePickerDialog] for the millis it speaks. */
@Composable
private fun RangeDatePicker(initial: LocalDate, onPicked: (LocalDate) -> Unit, onDismiss: () -> Unit) {
    LocalDatePickerDialog(
        initialDate = initial,
        confirmLabel = stringResource(Res.string.export_pick_ok),
        dismissLabel = stringResource(Res.string.export_pick_cancel),
        onConfirm = onPicked,
        onDismiss = onDismiss
    )
}

/** Every word the exported file prints, in the reader's language. */
@Composable
private fun rememberRecordLabels(): RecordLabels = RecordLabels(
    title = stringResource(Res.string.export_record_title),
    statement = listOf(
        stringResource(Res.string.export_statement_what),
        stringResource(Res.string.export_statement_messages),
        stringResource(Res.string.export_statement_revisions),
        stringResource(Res.string.export_statement_limits)
    ),
    period = stringResource(Res.string.export_record_period),
    generated = stringResource(Res.string.export_record_generated),
    timeZone = stringResource(Res.string.export_record_time_zone),
    parents = stringResource(Res.string.export_record_parents),
    incomplete = stringResource(Res.string.export_record_incomplete),
    sectionEvents = stringResource(Res.string.export_record_section_events),
    sectionMessages = stringResource(Res.string.export_record_section_messages),
    sectionExpenses = stringResource(Res.string.export_record_section_expenses),
    nothingInPeriod = stringResource(Res.string.export_record_nothing),
    columns = RecordColumns(
        section = stringResource(Res.string.export_col_section),
        item = stringResource(Res.string.export_col_item),
        revision = stringResource(Res.string.export_col_revision),
        action = stringResource(Res.string.export_col_action),
        by = stringResource(Res.string.export_col_by),
        deviceTime = stringResource(Res.string.export_col_device_time),
        serverTime = stringResource(Res.string.export_col_server_time),
        text = stringResource(Res.string.export_col_text),
        starts = stringResource(Res.string.export_col_starts),
        ends = stringResource(Res.string.export_col_ends),
        parent = stringResource(Res.string.export_col_parent),
        notes = stringResource(Res.string.export_col_notes),
        amount = stringResource(Res.string.export_col_amount),
        currency = stringResource(Res.string.export_col_currency)
    ),
    actions = RecordActions(
        created = stringResource(Res.string.export_action_created),
        updated = stringResource(Res.string.export_action_updated),
        deleted = stringResource(Res.string.export_action_deleted),
        currentState = stringResource(Res.string.export_action_current),
        sent = stringResource(Res.string.export_action_sent),
        notSent = stringResource(Res.string.export_action_not_sent),
        recorded = stringResource(Res.string.export_action_recorded),
        serverRecorded = stringResource(Res.string.export_action_server_recorded)
    ),
    notYetOnServer = stringResource(Res.string.export_record_not_on_server),
    noServerTime = stringResource(Res.string.export_record_no_server_time),
    revision = stringResource(Res.string.export_record_revision),
    page = stringResource(Res.string.export_record_page),
    verification = VerificationLabels(
        recordId = stringResource(Res.string.export_verify_record_id),
        verifyAt = stringResource(Res.string.export_verify_at),
        instruction = stringResource(Res.string.export_verify_instruction),
        instructionNoUrl = stringResource(Res.string.export_verify_instruction_no_url),
        notRegistered = stringResource(Res.string.export_verify_not_registered),
        notRegisteredShort = stringResource(Res.string.export_verify_not_registered_short)
    ),
    plan = rememberPlanLabels(),
    journal = rememberJournalLabels()
)

/** The private-journal section's words (MON-22). */
@Composable
private fun rememberJournalLabels(): JournalLabels = JournalLabels(
    section = stringResource(Res.string.journal_title),
    privateNote = stringResource(Res.string.export_journal_private_note),
    clockNote = stringResource(Res.string.export_journal_clock_note),
    none = stringResource(Res.string.export_journal_none),
    written = stringResource(Res.string.export_journal_written),
    edited = stringResource(Res.string.export_journal_edited)
)

/** The parenting-plan section's words; the questions are the plan screen's own wording. */
@Composable
private fun rememberPlanLabels(): PlanLabels = PlanLabels(
    section = stringResource(Res.string.parenting_plan_title),
    disclaimer = stringResource(Res.string.parenting_plan_disclaimer),
    currentState = stringResource(Res.string.export_plan_current_state),
    notFromServer = stringResource(Res.string.export_plan_not_from_server),
    unsentHere = stringResource(Res.string.export_plan_unsent_here),
    noPlan = stringResource(Res.string.export_plan_none),
    lastChanged = stringResource(Res.string.export_plan_last_changed),
    agreed = stringResource(Res.string.parenting_plan_status_agreed),
    notAgreed = stringResource(Res.string.export_plan_not_agreed),
    notAnswered = stringResource(Res.string.parenting_plan_not_answered),
    retired = stringResource(Res.string.export_plan_retired),
    // The catalogue is fixed for the life of the process, so this loop calls the same resources in
    // the same order on every composition.
    questions = ParentingPlanCatalogue.questions.mapNotNull { question ->
        PlanStrings.questionPrompt(question.id)?.let { question.id to stringResource(it) }
    }.toMap(),
    cited = stringResource(Res.string.export_plan_cited)
)
