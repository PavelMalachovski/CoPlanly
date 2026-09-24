package com.coparently.app.presentation.journal

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import com.coparently.app.presentation.common.LocalDatePickerDialog
import com.coparently.app.presentation.common.SectionGroup
import com.coparently.app.presentation.common.SectionRow
import com.coparently.app.presentation.common.UiText
import com.coparently.app.presentation.common.asString

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

    LaunchedEffect(state.saved) {
        if (state.saved) onNavigateUp()
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

    Scaffold(
        topBar = { EditorTopBar(isNew = state.isNew, onNavigateUp = onNavigateUp) },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar = { SaveBar(saving = state.saving, enabled = state.canSave, onSave = viewModel::save) }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
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
                    onClick = { picking = true }.takeIf { !state.loading && !state.saving }
                )
            }
            OutlinedTextField(
                value = state.text,
                onValueChange = viewModel::setText,
                label = { Text(stringResource(R.string.journal_entry_text)) },
                enabled = !state.loading && !state.saving,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = TEXT_MIN_HEIGHT)
            )
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

/** The sticky Save button, with a spinner while the entry is written. */
@Composable
private fun SaveBar(saving: Boolean, enabled: Boolean, onSave: () -> Unit) {
    Surface(shadowElevation = 8.dp) {
        Button(
            onClick = onSave,
            enabled = enabled,
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
                .height(52.dp)
        ) {
            if (saving) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.onPrimary
                )
            } else {
                Text(stringResource(R.string.journal_save))
            }
        }
    }
}

/** Room for a few paragraphs before the field starts to scroll with the page. */
private val TEXT_MIN_HEIGHT = 200.dp
