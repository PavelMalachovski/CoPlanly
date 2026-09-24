package com.coparently.app.presentation.journal

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
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
import com.coparently.app.presentation.common.ConfirmationDialog
import com.coparently.app.presentation.common.LocalDatePickerDialog
import com.coparently.app.presentation.common.SectionGroup
import com.coparently.app.presentation.common.SectionRow
import com.coparently.app.presentation.common.StickyActionBar
import com.coparently.app.presentation.common.UiText
import com.coparently.app.presentation.common.asString
import com.coparently.app.presentation.common.rememberDiscardGuard
import com.coparently.app.presentation.theme.Spacing

/**
 * Writing or editing one private journal entry (MON-22): the day it is about, the text, and a
 * sticky Save at the bottom like every other form in the app. Goes back once the entry is saved.
 *
 * @param onNavigateUp Back to the journal list.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun JournalEditorScreen(
    onNavigateUp: () -> Unit,
    viewModel: JournalEditorViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    var picking by rememberSaveable { mutableStateOf(false) }
    var confirmingDelete by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(state.saved, state.deleted) {
        if (state.saved || state.deleted) onNavigateUp()
    }
    LaunchedEffect(state.error) {
        state.error?.let {
            snackbarHostState.showSnackbar(it.asString(context))
            viewModel.errorShown()
        }
    }
    if (picking) {
        LocalDatePickerDialog(
            initialDate = state.entryDate,
            confirmLabel = stringResource(R.string.journal_pick_ok),
            dismissLabel = stringResource(R.string.journal_pick_cancel),
            onConfirm = { date ->
                viewModel.setDate(date)
                picking = false
            },
            onDismiss = { picking = false }
        )
    }

    if (confirmingDelete) {
        DeleteEntryDialog(
            onConfirm = {
                confirmingDelete = false
                viewModel.delete()
            },
            onDismiss = { confirmingDelete = false }
        )
    }

    // Asked before what was written is dropped (D-11), on Back and on the arrow alike.
    val leave = rememberDiscardGuard(dirty = state.hasUnsavedEdits && !state.saving, onLeave = onNavigateUp)

    Scaffold(
        topBar = { EditorTopBar(isNew = state.isNew, onNavigateUp = leave) },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar = {
            StickyActionBar(
                label = stringResource(R.string.journal_save),
                onClick = viewModel::save,
                enabled = state.canSave,
                busy = state.saving
            )
        }
    ) { padding ->
        EditorForm(
            state = state,
            onPickDate = { picking = true },
            onTextChange = viewModel::setText,
            onDelete = { confirmingDelete = true },
            modifier = Modifier.padding(padding)
        )
    }
}

/**
 * Asks before deleting. Confirmed rather than undone: the entry exists on this phone alone, and
 * the editor closes as it goes, so there is no list here to offer an Undo from.
 */
@Composable
private fun DeleteEntryDialog(onConfirm: () -> Unit, onDismiss: () -> Unit) {
    ConfirmationDialog(
        title = stringResource(R.string.journal_delete_confirm_title),
        message = stringResource(R.string.journal_delete_confirm_message),
        confirmText = stringResource(R.string.journal_delete),
        onConfirm = onConfirm,
        onDismiss = onDismiss,
        isDestructive = true
    )
}

/** The notice, the day the entry is about, the text, and — for an existing entry — Delete, last. */
@Composable
private fun EditorForm(
    state: JournalEditorState,
    onPickDate: () -> Unit,
    onTextChange: (String) -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    val editable = !state.loading && !state.saving
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = Spacing.L, vertical = Spacing.S),
        verticalArrangement = Arrangement.spacedBy(Spacing.L)
    ) {
        Text(
            text = stringResource(R.string.journal_editor_notice),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        SectionGroup {
            SectionRow(
                icon = Icons.Default.DateRange,
                title = stringResource(R.string.journal_entry_date),
                supporting = UiText.Date(state.entryDate).asString(),
                onClick = onPickDate.takeIf { editable }
            )
        }
        OutlinedTextField(
            value = state.text,
            onValueChange = onTextChange,
            label = { Text(stringResource(R.string.journal_entry_text)) },
            enabled = editable,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = TEXT_MIN_HEIGHT)
        )
        // Last on the screen, per the destructive-action anatomy (design refresh item 8).
        if (!state.isNew) {
            SectionGroup {
                SectionRow(
                    title = stringResource(R.string.journal_delete),
                    icon = Icons.Default.Delete,
                    iconTint = MaterialTheme.colorScheme.error,
                    titleColor = MaterialTheme.colorScheme.error,
                    onClick = onDelete.takeIf { state.canDelete }
                )
            }
        }
    }
}

/** The title says whether this is a new entry, and the arrow goes back without saving. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EditorTopBar(isNew: Boolean, onNavigateUp: () -> Unit) {
    TopAppBar(
        title = {
            Text(stringResource(if (isNew) R.string.journal_editor_new_title else R.string.journal_editor_edit_title))
        },
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

/** Room for a few paragraphs before the field starts to scroll with the page. */
private val TEXT_MIN_HEIGHT = 200.dp
