package com.coparently.app.presentation.export

import android.content.ClipData
import android.content.Context
import android.content.Intent
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.coparently.app.R
import com.coparently.app.data.export.ExportedFile
import com.coparently.app.domain.export.ExportFormat
import com.coparently.app.domain.export.PlanLabels
import com.coparently.app.domain.export.RecordActions
import com.coparently.app.domain.export.RecordColumns
import com.coparently.app.domain.export.RecordLabels
import com.coparently.app.domain.export.VerificationLabels
import com.coparently.app.domain.parentingplan.ParentingPlanCatalogue
import com.coparently.app.presentation.common.GroupLabel
import com.coparently.app.presentation.common.LocalDatePickerDialog
import com.coparently.app.presentation.common.SectionGroup
import com.coparently.app.presentation.common.SectionRow
import com.coparently.app.presentation.common.UiText
import com.coparently.app.presentation.common.asString
import com.coparently.app.presentation.parentingplan.PlanStrings
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

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
    viewModel: ExportViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    val labels = rememberRecordLabels()
    val fallbacks = NameFallbacks(
        you = stringResource(R.string.parent_label_you),
        coParent = stringResource(R.string.parent_label_coparent),
        unknown = stringResource(R.string.parent_label_unknown)
    )
    var picking by rememberSaveable { mutableStateOf<RangeEnd?>(null) }

    ShareWhenFinished(viewModel.files)
    LaunchedEffect(state.error) {
        state.error?.let {
            snackbarHostState.showSnackbar(it.asString(context))
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
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.export_title)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.export_back)
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
                onExport = { format -> viewModel.export(format, labels, fallbacks) }
            )
        )
    }
}

/** What the export screen's controls do, passed as one value so the content takes few parameters. */
private class ExportActions(
    val onPick: (RangeEnd) -> Unit,
    val onIncludePlan: (Boolean) -> Unit,
    val onExport: (ExportFormat) -> Unit
)

/** The range, whether the plan goes in, the statement the file makes about itself, and the two formats. */
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
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        Text(
            text = stringResource(R.string.export_intro),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        RangeGroup(state = state, onPick = actions.onPick)
        IncludePlanGroup(
            included = state.includePlan,
            enabled = state.working == null,
            onChange = actions.onIncludePlan
        )
        Column {
            GroupLabel(stringResource(R.string.export_what_it_says))
            Surface(
                color = MaterialTheme.colorScheme.surfaceContainer,
                shape = MaterialTheme.shapes.large
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    statement.forEach { paragraph ->
                        Text(text = paragraph, style = MaterialTheme.typography.bodySmall)
                    }
                    Text(
                        text = stringResource(R.string.export_verify_explainer),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
        SectionGroup {
            ExportRow(
                format = ExportFormat.PDF,
                working = state.working,
                onClick = { actions.onExport(ExportFormat.PDF) }
            )
            Divider()
            ExportRow(
                format = ExportFormat.CSV,
                working = state.working,
                onClick = { actions.onExport(ExportFormat.CSV) }
            )
        }
    }
}

/** The two ends of the period, each opening a date picker while no export runs. */
@Composable
private fun RangeGroup(state: ExportUiState, onPick: (RangeEnd) -> Unit) {
    Column {
        GroupLabel(stringResource(R.string.export_range_label))
        SectionGroup {
            SectionRow(
                icon = Icons.Default.DateRange,
                title = stringResource(R.string.export_from),
                supporting = UiText.Date(state.from).asString(),
                onClick = { onPick(RangeEnd.FROM) }.takeIf { state.working == null }
            )
            Divider()
            SectionRow(
                icon = Icons.Default.DateRange,
                title = stringResource(R.string.export_to),
                supporting = UiText.Date(state.to).asString(),
                onClick = { onPick(RangeEnd.TO) }.takeIf { state.working == null }
            )
        }
    }
}

/**
 * Whether the family's parenting plan goes into the file. The plan has no period, so the row says
 * it is printed as it stands now, whatever range is picked above.
 */
@Composable
private fun IncludePlanGroup(included: Boolean, enabled: Boolean, onChange: (Boolean) -> Unit) {
    Column {
        GroupLabel(stringResource(R.string.export_include_label))
        SectionGroup {
            SectionRow(
                modifier = Modifier.toggleable(
                    value = included,
                    enabled = enabled,
                    role = Role.Checkbox,
                    onValueChange = onChange
                ),
                icon = Icons.AutoMirrored.Filled.Assignment,
                title = stringResource(R.string.export_include_plan),
                supporting = stringResource(R.string.export_include_plan_supporting),
                trailing = { Checkbox(checked = included, onCheckedChange = null, enabled = enabled) }
            )
        }
    }
}

/**
 * Hands each finished file to the share sheet — at once when it was registered, and after
 * [UnregisteredDialog] when it was not (MON-16): the share sheet alone would hand over a file that
 * cannot be verified without a word about it.
 */
@Composable
private fun ShareWhenFinished(files: Flow<FinishedExport>) {
    val context = LocalContext.current
    val shareTitle = stringResource(R.string.export_share_title)
    var unregistered by remember { mutableStateOf<ExportedFile?>(null) }

    LaunchedEffect(files) {
        files.collect { finished ->
            if (finished.recordId != null) share(context, finished.file, shareTitle) else unregistered = finished.file
        }
    }
    unregistered?.let { file ->
        UnregisteredDialog(
            onShare = {
                unregistered = null
                share(context, file, shareTitle)
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
        title = { Text(stringResource(R.string.export_unregistered_title)) },
        text = { Text(stringResource(R.string.export_unregistered_body)) },
        confirmButton = {
            TextButton(onClick = onShare) { Text(stringResource(R.string.export_unregistered_share)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.export_unregistered_not_now)) }
        }
    )
}

/** Which end of the range a date picker is choosing. */
private enum class RangeEnd { FROM, TO }

/** One format's row: its name, what the file is, and a spinner while it is being made. */
@Composable
private fun ExportRow(format: ExportFormat, working: ExportFormat?, onClick: () -> Unit) {
    val (title, supporting) = when (format) {
        ExportFormat.PDF -> R.string.export_pdf to R.string.export_pdf_supporting
        ExportFormat.CSV -> R.string.export_csv to R.string.export_csv_supporting
    }
    val spinner: (@Composable () -> Unit)? = if (working == format) {
        @Composable { CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp) }
    } else {
        null
    }
    SectionRow(
        icon = if (format == ExportFormat.PDF) Icons.Default.PictureAsPdf else Icons.Default.TableChart,
        title = stringResource(title),
        supporting = stringResource(if (working == format) R.string.export_working else supporting),
        // Disabled while either format runs: two exports at once would clear each other's file.
        onClick = onClick.takeIf { working == null },
        trailing = spinner
    )
}

/** A date picker for one end of the range; see [LocalDatePickerDialog] for the millis it speaks. */
@Composable
private fun RangeDatePicker(initial: LocalDate, onPicked: (LocalDate) -> Unit, onDismiss: () -> Unit) {
    LocalDatePickerDialog(
        initialDate = initial,
        confirmLabel = stringResource(R.string.export_pick_ok),
        dismissLabel = stringResource(R.string.export_pick_cancel),
        onConfirm = onPicked,
        onDismiss = onDismiss
    )
}

/** Every word the exported file prints, in the reader's language. */
@Composable
private fun rememberRecordLabels(): RecordLabels = RecordLabels(
    title = stringResource(R.string.export_record_title),
    statement = listOf(
        stringResource(R.string.export_statement_what),
        stringResource(R.string.export_statement_messages),
        stringResource(R.string.export_statement_revisions),
        stringResource(R.string.export_statement_limits)
    ),
    period = stringResource(R.string.export_record_period),
    generated = stringResource(R.string.export_record_generated),
    timeZone = stringResource(R.string.export_record_time_zone),
    parents = stringResource(R.string.export_record_parents),
    incomplete = stringResource(R.string.export_record_incomplete),
    sectionEvents = stringResource(R.string.export_record_section_events),
    sectionMessages = stringResource(R.string.export_record_section_messages),
    sectionExpenses = stringResource(R.string.export_record_section_expenses),
    nothingInPeriod = stringResource(R.string.export_record_nothing),
    columns = RecordColumns(
        section = stringResource(R.string.export_col_section),
        item = stringResource(R.string.export_col_item),
        revision = stringResource(R.string.export_col_revision),
        action = stringResource(R.string.export_col_action),
        by = stringResource(R.string.export_col_by),
        deviceTime = stringResource(R.string.export_col_device_time),
        serverTime = stringResource(R.string.export_col_server_time),
        text = stringResource(R.string.export_col_text),
        starts = stringResource(R.string.export_col_starts),
        ends = stringResource(R.string.export_col_ends),
        parent = stringResource(R.string.export_col_parent),
        notes = stringResource(R.string.export_col_notes),
        amount = stringResource(R.string.export_col_amount),
        currency = stringResource(R.string.export_col_currency)
    ),
    actions = RecordActions(
        created = stringResource(R.string.export_action_created),
        updated = stringResource(R.string.export_action_updated),
        deleted = stringResource(R.string.export_action_deleted),
        currentState = stringResource(R.string.export_action_current),
        sent = stringResource(R.string.export_action_sent),
        notSent = stringResource(R.string.export_action_not_sent),
        recorded = stringResource(R.string.export_action_recorded)
    ),
    notYetOnServer = stringResource(R.string.export_record_not_on_server),
    noServerTime = stringResource(R.string.export_record_no_server_time),
    revision = stringResource(R.string.export_record_revision),
    page = stringResource(R.string.export_record_page),
    verification = VerificationLabels(
        recordId = stringResource(R.string.export_verify_record_id),
        verifyAt = stringResource(R.string.export_verify_at),
        instruction = stringResource(R.string.export_verify_instruction),
        instructionNoUrl = stringResource(R.string.export_verify_instruction_no_url),
        notRegistered = stringResource(R.string.export_verify_not_registered),
        notRegisteredShort = stringResource(R.string.export_verify_not_registered_short)
    ),
    plan = rememberPlanLabels()
)

/** The parenting-plan section's words; the questions are the plan screen's own wording. */
@Composable
private fun rememberPlanLabels(): PlanLabels = PlanLabels(
    section = stringResource(R.string.parenting_plan_title),
    disclaimer = stringResource(R.string.parenting_plan_disclaimer),
    currentState = stringResource(R.string.export_plan_current_state),
    notFromServer = stringResource(R.string.export_plan_not_from_server),
    unsentHere = stringResource(R.string.export_plan_unsent_here),
    noPlan = stringResource(R.string.export_plan_none),
    lastChanged = stringResource(R.string.export_plan_last_changed),
    agreed = stringResource(R.string.parenting_plan_status_agreed),
    notAgreed = stringResource(R.string.export_plan_not_agreed),
    notAnswered = stringResource(R.string.parenting_plan_not_answered),
    retired = stringResource(R.string.export_plan_retired),
    // The catalogue is fixed for the life of the process, so this loop calls the same resources in
    // the same order on every composition.
    questions = ParentingPlanCatalogue.questions.mapNotNull { question ->
        PlanStrings.questionPrompt(question.id)?.let { question.id to stringResource(it) }
    }.toMap(),
    cited = stringResource(R.string.export_plan_cited)
)

/** Hands [file] to the share sheet, through [recordShareIntent]. */
private fun share(context: Context, file: ExportedFile, title: String) {
    context.startActivity(Intent.createChooser(recordShareIntent(file), title))
}

/**
 * The `ACTION_SEND` intent an exported record is shared with: the file's `FileProvider` URI and a
 * one-off read grant, and nothing else.
 *
 * The grant is on the intent *and* its `ClipData`, because some targets read the URI from the clip
 * and the chooser only forwards the grant it can see there. Public so the instrumented export test
 * can check the intent the share sheet receives without driving the chooser.
 */
fun recordShareIntent(file: ExportedFile): Intent = Intent(Intent.ACTION_SEND).apply {
    type = file.mimeType
    putExtra(Intent.EXTRA_STREAM, file.uri)
    clipData = ClipData.newRawUri(null, file.uri)
    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
}
