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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.TableChart
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
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.coparently.app.R
import com.coparently.app.data.export.ExportedFile
import com.coparently.app.domain.export.RecordActions
import com.coparently.app.domain.export.RecordColumns
import com.coparently.app.domain.export.RecordLabels
import com.coparently.app.presentation.common.GroupLabel
import com.coparently.app.presentation.common.LocalDatePickerDialog
import com.coparently.app.presentation.common.SectionGroup
import com.coparently.app.presentation.common.SectionRow
import com.coparently.app.presentation.common.UiText
import com.coparently.app.presentation.common.asString
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
    val shareTitle = stringResource(R.string.export_share_title)
    var picking by rememberSaveable { mutableStateOf<RangeEnd?>(null) }

    LaunchedEffect(Unit) {
        viewModel.files.collect { file -> share(context, file, shareTitle) }
    }
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
            onPick = { end -> picking = end },
            onExport = { format -> viewModel.export(format, labels, fallbacks) }
        )
    }
}

/** The range, the statement the file makes about itself, and the two formats. */
@Composable
private fun ExportContent(
    state: ExportUiState,
    statement: List<String>,
    onPick: (RangeEnd) -> Unit,
    onExport: (ExportFormat) -> Unit,
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
                }
            }
        }
        SectionGroup {
            ExportRow(format = ExportFormat.PDF, working = state.working, onClick = { onExport(ExportFormat.PDF) })
            Divider()
            ExportRow(format = ExportFormat.CSV, working = state.working, onClick = { onExport(ExportFormat.CSV) })
        }
    }
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
    page = stringResource(R.string.export_record_page)
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
