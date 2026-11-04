package com.coparently.app.presentation.school

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.LinkOff
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import com.coparently.app.domain.school.SchoolConnectionStatus
import com.coparently.app.presentation.common.ConfirmationDialog
import com.coparently.app.presentation.common.EmptyState
import com.coparently.app.presentation.common.InlineBanner
import com.coparently.app.presentation.common.ListSkeleton
import com.coparently.app.presentation.common.SectionGroup
import com.coparently.app.presentation.common.SectionRow
import com.coparently.app.presentation.common.asString
import com.coparently.app.presentation.common.rememberUiTextResolver
import com.coparently.app.presentation.theme.Spacing
import com.coparently.app.resources.Res
import com.coparently.app.resources.common_back
import com.coparently.app.resources.school_import_connect
import com.coparently.app.resources.school_import_connect_another
import com.coparently.app.resources.school_import_disconnect
import com.coparently.app.resources.school_import_disconnect_message
import com.coparently.app.resources.school_import_disconnect_title
import com.coparently.app.resources.school_import_empty_description
import com.coparently.app.resources.school_import_empty_title
import com.coparently.app.resources.school_import_intro
import com.coparently.app.resources.school_import_last_sync
import com.coparently.app.resources.school_import_never_synced
import com.coparently.app.resources.school_import_reconnect
import com.coparently.app.resources.school_import_status_error
import com.coparently.app.resources.school_import_status_needs_family
import com.coparently.app.resources.school_import_status_needs_password
import com.coparently.app.resources.school_import_title
import com.coparently.app.resources.school_import_update_now
import com.coparently.app.resources.school_import_updating
import com.coparently.app.utils.ReaderDateText
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import kotlin.time.Instant

/**
 * Settings → Sync → the school import (MON-8): one group per connection — the child, the school,
 * when it last updated and whether it still can — with "Update now", "Sign in again" when the
 * school asked for the password, and "Disconnect".
 *
 * @param onNavigateUp Returns to Settings.
 * @param onConnect Opens the connect flow.
 * @param onReconnect Opens the connect flow's "Sign in again" for a connection.
 * @param viewModel Screen state.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SchoolImportScreen(
    onNavigateUp: () -> Unit,
    onConnect: () -> Unit,
    onReconnect: (String) -> Unit,
    viewModel: SchoolImportViewModel = koinViewModel()
) {
    val state by viewModel.state.collectAsState()
    val textResolver = rememberUiTextResolver()
    val snackbarHostState = remember { SnackbarHostState() }
    var disconnecting by rememberSaveable { mutableStateOf<String?>(null) }

    LaunchedEffect(state.message) {
        state.message?.let { message ->
            snackbarHostState.showSnackbar(message.asString(textResolver))
            viewModel.messageShown()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text(stringResource(Res.string.school_import_title)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateUp) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(Res.string.common_back)
                        )
                    }
                }
            )
        }
    ) { padding ->
        SchoolImportBody(
            state = state,
            actions = ConnectionActions(
                onUpdate = viewModel::updateNow,
                onReconnect = onReconnect,
                onDisconnect = { disconnecting = it },
                onConnect = onConnect
            ),
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        )
    }

    disconnecting?.let { connectionId ->
        ConfirmationDialog(
            title = stringResource(Res.string.school_import_disconnect_title),
            message = stringResource(Res.string.school_import_disconnect_message),
            confirmText = stringResource(Res.string.school_import_disconnect),
            onConfirm = {
                viewModel.disconnect(connectionId)
                disconnecting = null
            },
            onDismiss = { disconnecting = null },
            isDestructive = true
        )
    }
}

/** What a connection's rows can do. */
private class ConnectionActions(
    val onUpdate: (String) -> Unit,
    val onReconnect: (String) -> Unit,
    val onDisconnect: (String) -> Unit,
    val onConnect: () -> Unit
)

/** The skeleton, the empty state, or the connections. */
@Composable
private fun SchoolImportBody(state: SchoolImportUiState, actions: ConnectionActions, modifier: Modifier) {
    when {
        state.isLoading -> ListSkeleton(modifier = modifier)
        state.rows.isEmpty() -> EmptyState(
            icon = Icons.Default.School,
            title = stringResource(Res.string.school_import_empty_title),
            description = stringResource(Res.string.school_import_empty_description),
            actionLabel = stringResource(Res.string.school_import_connect),
            onAction = actions.onConnect,
            modifier = modifier
        )
        else -> ConnectionList(state = state, actions = actions, modifier = modifier)
    }
}

@Composable
private fun ConnectionList(state: SchoolImportUiState, actions: ConnectionActions, modifier: Modifier) {
    Column(
        modifier = modifier
            .verticalScroll(rememberScrollState())
            .padding(Spacing.L),
        verticalArrangement = Arrangement.spacedBy(Spacing.L)
    ) {
        InlineBanner(text = stringResource(Res.string.school_import_intro))
        state.rows.forEach { row ->
            ConnectionGroup(row = row, updating = row.connection.id in state.updating, actions = actions)
        }
        SectionGroup {
            SectionRow(
                icon = Icons.Default.Add,
                title = stringResource(Res.string.school_import_connect_another),
                onClick = actions.onConnect
            )
        }
    }
}

/** One connection: who and where, its state, and its three controls. */
@Composable
private fun ConnectionGroup(row: SchoolConnectionRow, updating: Boolean, actions: ConnectionActions) {
    val connection = row.connection
    val id = connection.id
    SectionGroup {
        SectionRow(
            icon = Icons.Default.School,
            title = row.childName,
            supporting = listOf(connection.schoolName.ifBlank { connection.baseUrl }, statusLine(row))
                .joinToString("\n"),
            supportingColor = if (connection.status == SchoolConnectionStatus.OK && !row.needsFamily) {
                MaterialTheme.colorScheme.onSurfaceVariant
            } else {
                MaterialTheme.colorScheme.error
            }
        )
        Divider()
        if (connection.status == SchoolConnectionStatus.NEEDS_PASSWORD) {
            SectionRow(
                icon = Icons.Default.Key,
                title = stringResource(Res.string.school_import_reconnect),
                onClick = { actions.onReconnect(id) }
            )
        } else {
            SectionRow(
                icon = Icons.Default.Sync,
                title = stringResource(
                    if (updating) Res.string.school_import_updating else Res.string.school_import_update_now
                ),
                onClick = if (updating) null else ({ actions.onUpdate(id) })
            )
        }
        Divider()
        SectionRow(
            icon = Icons.Default.LinkOff,
            iconTint = MaterialTheme.colorScheme.error,
            title = stringResource(Res.string.school_import_disconnect),
            titleColor = MaterialTheme.colorScheme.error,
            onClick = { actions.onDisconnect(id) }
        )
    }
}

/** When the connection last updated, or what stops it. */
@Composable
private fun statusLine(row: SchoolConnectionRow): String {
    val connection = row.connection
    if (row.needsFamily && connection.status == SchoolConnectionStatus.OK) {
        return stringResource(Res.string.school_import_status_needs_family)
    }
    return when (connection.status) {
        SchoolConnectionStatus.NEEDS_PASSWORD -> stringResource(Res.string.school_import_status_needs_password)
        SchoolConnectionStatus.ERROR -> stringResource(Res.string.school_import_status_error)
        SchoolConnectionStatus.OK -> connection.lastSuccessAtMillis?.let { millis ->
            // Not remembered: the time follows the reader's 12/24-hour setting, which can change.
            val time = ReaderDateText.dateWithTime(
                Instant.fromEpochMilliseconds(millis).toLocalDateTime(TimeZone.currentSystemDefault()),
                DATE_SKELETON
            )
            stringResource(Res.string.school_import_last_sync, time)
        } ?: stringResource(Res.string.school_import_never_synced)
    }
}

/** Day and month, in the reader's order; the time follows on the reader's clock. */
private const val DATE_SKELETON = "MMMd"
