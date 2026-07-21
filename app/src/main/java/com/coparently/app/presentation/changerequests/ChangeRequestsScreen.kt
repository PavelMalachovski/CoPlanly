package com.coparently.app.presentation.changerequests

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.coparently.app.domain.model.ChangeRequest
import com.coparently.app.domain.model.ChangeRequestStatus
import java.time.format.DateTimeFormatter

private val requestDateFormatter = DateTimeFormatter.ofPattern("EEE, MMM d · HH:mm")

/**
 * Inbox of event change requests: incoming ones the user must respond to,
 * and outgoing ones the user sent to the co-parent.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChangeRequestsScreen(
    onBack: () -> Unit,
    onOpenEvent: (String) -> Unit,
    viewModel: ChangeRequestViewModel = hiltViewModel()
) {
    val requests by viewModel.changeRequests.collectAsState()
    val currentUserId by viewModel.currentUserId.collectAsState()
    val errorMessage by viewModel.errorMessage.collectAsState()
    val context = LocalContext.current

    LaunchedEffect(errorMessage) {
        errorMessage?.let {
            Toast.makeText(context, it, Toast.LENGTH_LONG).show()
            viewModel.clearError()
        }
    }

    val incoming = requests.filter { it.requestedTo == currentUserId }
    val outgoing = requests.filter { it.requestedBy == currentUserId }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Change Requests") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                }
            )
        }
    ) { padding ->
        if (incoming.isEmpty() && outgoing.isEmpty()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = "No change requests yet",
                    style = MaterialTheme.typography.titleMedium
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Open an event and tap \"Request change\" to propose a new time to your co-parent.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (incoming.isNotEmpty()) {
                    item { SectionHeader("Incoming") }
                    items(incoming, key = { it.id }) { request ->
                        ChangeRequestCard(
                            request = request,
                            isIncoming = true,
                            onOpenEvent = onOpenEvent,
                            onAccept = { viewModel.accept(request.id) },
                            onDecline = { viewModel.decline(request.id) },
                            onCancel = {}
                        )
                    }
                }
                if (outgoing.isNotEmpty()) {
                    item { SectionHeader("Outgoing") }
                    items(outgoing, key = { it.id }) { request ->
                        ChangeRequestCard(
                            request = request,
                            isIncoming = false,
                            onOpenEvent = onOpenEvent,
                            onAccept = {},
                            onDecline = {},
                            onCancel = { viewModel.cancel(request.id) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        fontWeight = FontWeight.Bold
    )
}

/**
 * One change request: event title, current -> proposed time, optional note,
 * status chip and the actions available for it.
 */
@Composable
fun ChangeRequestCard(
    request: ChangeRequest,
    isIncoming: Boolean,
    onOpenEvent: (String) -> Unit,
    onAccept: () -> Unit,
    onDecline: () -> Unit,
    onCancel: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        onClick = { onOpenEvent(request.eventId) }
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = request.eventTitle,
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f)
                )
                AssistChip(
                    onClick = {},
                    enabled = false,
                    label = { Text(request.status.displayName) }
                )
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Current",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = request.currentStartDateTime.format(requestDateFormatter),
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
                Icon(
                    Icons.AutoMirrored.Filled.ArrowForward,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Column(
                    modifier = Modifier.weight(1f),
                    horizontalAlignment = Alignment.End
                ) {
                    Text(
                        text = "Proposed",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = request.proposedStartDateTime.format(requestDateFormatter),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            request.note?.let { note ->
                Text(
                    text = "“$note”",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (request.status == ChangeRequestStatus.PENDING) {
                if (isIncoming) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(onClick = onAccept, modifier = Modifier.weight(1f)) {
                            Text("Accept")
                        }
                        OutlinedButton(onClick = onDecline, modifier = Modifier.weight(1f)) {
                            Text("Decline")
                        }
                    }
                } else {
                    OutlinedButton(
                        onClick = onCancel,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Withdraw Request")
                    }
                }
            }
        }
    }
}
