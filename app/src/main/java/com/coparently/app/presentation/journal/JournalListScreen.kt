package com.coparently.app.presentation.journal

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.coparently.app.R
import com.coparently.app.domain.journal.JournalEntry
import com.coparently.app.presentation.common.EmptyState
import com.coparently.app.presentation.common.InlineBanner
import com.coparently.app.presentation.common.SectionRow
import com.coparently.app.presentation.common.UiText
import com.coparently.app.presentation.common.asString
import com.coparently.app.presentation.theme.Spacing
import kotlinx.coroutines.launch

/** How much of an entry's first line the list shows under its date. */
private const val PREVIEW_CHARS = 120

/**
 * The private journal (MON-22): a detail screen off Settings → Family.
 *
 * Says before anything else that the entries stay on this phone — not synced, not backed up, not
 * shared with the co-parent — and what that costs: they go with the app, or with a different
 * account signing in here. A journal that let a parent believe otherwise would be design item 8's
 * broken promise in its worst form. Each row swipes away with an Undo snackbar (UX item 8).
 *
 * @param onNavigateUp Back to Settings.
 * @param onOpenEntry Opens the editor on an entry, by id.
 * @param onNewEntry Opens the editor on a new entry.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun JournalListScreen(
    onNavigateUp: () -> Unit,
    onOpenEntry: (String) -> Unit,
    onNewEntry: () -> Unit,
    viewModel: JournalListViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    // Resolved in composable scope: the delete lambda below is not composable.
    val deletedMessage = stringResource(R.string.journal_deleted)
    val undoLabel = stringResource(R.string.journal_undo)
    val deleteWithUndo: (JournalEntry) -> Unit = { entry ->
        viewModel.delete(entry)
        scope.launch {
            val result = snackbarHostState.showSnackbar(
                message = deletedMessage,
                actionLabel = undoLabel,
                duration = SnackbarDuration.Short
            )
            if (result == SnackbarResult.ActionPerformed) viewModel.restore(entry)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.journal_title)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateUp) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.common_back)
                        )
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        floatingActionButton = {
            FloatingActionButton(onClick = onNewEntry) {
                Icon(imageVector = Icons.Default.Add, contentDescription = stringResource(R.string.journal_add))
            }
        }
    ) { padding ->
        when (val current = state) {
            JournalListState.Loading -> Box(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center
            ) { CircularProgressIndicator() }
            is JournalListState.Loaded -> JournalList(
                entries = current.entries,
                padding = padding,
                onOpenEntry = onOpenEntry,
                onNewEntry = onNewEntry,
                onDelete = deleteWithUndo
            )
        }
    }
}

/** The notice, then either the entries or the empty state. */
@Composable
private fun JournalList(
    entries: List<JournalEntry>,
    padding: PaddingValues,
    onOpenEntry: (String) -> Unit,
    onNewEntry: () -> Unit,
    onDelete: (JournalEntry) -> Unit
) {
    if (entries.isEmpty()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = Spacing.L, vertical = Spacing.S)
        ) {
            PrivacyNotice()
            EmptyState(
                icon = Icons.Default.Lock,
                title = stringResource(R.string.journal_empty_title),
                description = stringResource(R.string.journal_empty_description),
                actionLabel = stringResource(R.string.journal_add),
                onAction = onNewEntry,
                modifier = Modifier.weight(1f)
            )
        }
        return
    }
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(padding),
        contentPadding = PaddingValues(horizontal = Spacing.L, vertical = Spacing.S),
        verticalArrangement = Arrangement.spacedBy(Spacing.S)
    ) {
        item { PrivacyNotice() }
        // Keyed by id: a journal entry is stored once, so the id is unique in this list.
        items(entries, key = { it.id }) { entry ->
            SwipeableJournalRow(entry = entry, onOpen = { onOpenEntry(entry.id) }, onDelete = { onDelete(entry) })
        }
    }
}

/** What the journal is and is not, in plain words, above everything else on the screen. */
@Composable
private fun PrivacyNotice() {
    InlineBanner(
        text = stringResource(R.string.journal_private_notice),
        icon = Icons.Default.Lock,
        modifier = Modifier.padding(bottom = Spacing.S)
    )
}

/** One entry: the day it is about and the start of its text; swipes end-to-start to delete. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SwipeableJournalRow(entry: JournalEntry, onOpen: () -> Unit, onDelete: () -> Unit) {
    val dismissState = rememberSwipeToDismissBoxState()
    // The delete runs once the row has settled off-screen. `onDismiss` below captures only this
    // State, so Compose memoizes it and a recomposition cannot re-run the delete on a settled row.
    val currentOnDelete by rememberUpdatedState(onDelete)
    SwipeToDismissBox(
        state = dismissState,
        enableDismissFromStartToEnd = false,
        onDismiss = { value -> if (value == SwipeToDismissBoxValue.EndToStart) currentOnDelete() },
        backgroundContent = {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(color = MaterialTheme.colorScheme.error, shape = MaterialTheme.shapes.large)
                    .padding(horizontal = 20.dp),
                contentAlignment = Alignment.CenterEnd
            ) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = stringResource(R.string.journal_delete),
                    tint = MaterialTheme.colorScheme.onError
                )
            }
        }
    ) {
        // Opaque, so the delete colour shows only where the row has been swiped away from.
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.surfaceContainer
        ) {
            SectionRow(
                title = UiText.Date(entry.entryDate).asString(),
                supporting = entry.text.lineSequence().firstOrNull().orEmpty().take(PREVIEW_CHARS),
                onClick = onOpen
            )
        }
    }
}
