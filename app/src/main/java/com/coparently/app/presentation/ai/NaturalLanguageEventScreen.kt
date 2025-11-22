package com.coparently.app.presentation.ai

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.coparently.app.domain.model.ai.ParsedEvent
import java.time.format.DateTimeFormatter

/**
 * Screen for natural language event creation
 * Day 2 - Feature 2.1: Voice-to-event conversion
 */
@Composable
fun NaturalLanguageEventScreen(
    onEventSaved: () -> Unit,
    onNavigateToManualEdit: (ParsedEvent) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: NaturalLanguageEventViewModel = hiltViewModel()
) {
    val parsingState by viewModel.parsingState.collectAsState()
    var inputText by remember { mutableStateOf("") }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "Create Event from Text",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Input field
        OutlinedTextField(
            value = inputText,
            onValueChange = { inputText = it },
            label = { Text("Describe your event") },
            placeholder = { Text("E.g., Soccer practice tomorrow at 3pm") },
            modifier = Modifier.fillMaxWidth(),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(
                onDone = {
                    viewModel.parseEventFromText(inputText)
                }
            ),
            minLines = 3,
            maxLines = 5
        )

        Spacer(modifier = Modifier.height(16.dp))

        Button(
            onClick = {
                viewModel.parseEventFromText(inputText)
            },
            modifier = Modifier.fillMaxWidth(),
            enabled = inputText.isNotBlank() && parsingState !is ParsingUiState.Parsing
        ) {
            if (parsingState is ParsingUiState.Parsing) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    color = MaterialTheme.colorScheme.onPrimary
                )
                Spacer(modifier = Modifier.width(8.dp))
            }
            Text("Parse Event")
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Parsing result
        when (val state = parsingState) {
            is ParsingUiState.Success -> {
                ParsedEventConfirmation(
                    parsedEvent = state.parsedEvent,
                    confidence = state.confidence,
                    validationIssues = state.validationIssues,
                    onConfirm = {
                        viewModel.confirmAndSaveEvent(state.parsedEvent)
                    },
                    onEdit = {
                        onNavigateToManualEdit(state.parsedEvent)
                    }
                )
            }
            is ParsingUiState.Error -> {
                ErrorCard(
                    message = state.message,
                    onRetry = {
                        viewModel.parseEventFromText(inputText)
                    }
                )
            }
            is ParsingUiState.EventSaved -> {
                LaunchedEffect(Unit) {
                    onEventSaved()
                }
                SuccessCard(message = "Event saved successfully!")
            }
            else -> {}
        }
    }
}

@Composable
fun ParsedEventConfirmation(
    parsedEvent: ParsedEvent,
    confidence: Double,
    validationIssues: List<String>,
    onConfirm: () -> Unit,
    onEdit: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Parsed Event",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )

                ConfidenceBadge(confidence = confidence)
            }

            Spacer(modifier = Modifier.height(12.dp))

            EventDetailRow("Title", parsedEvent.title)
            parsedEvent.dateTime?.let {
                EventDetailRow(
                    "Date & Time",
                    it.format(DateTimeFormatter.ofPattern("MMM dd, yyyy 'at' h:mm a"))
                )
            }
            parsedEvent.duration?.let {
                EventDetailRow("Duration", "${it.toMinutes()} minutes")
            }
            parsedEvent.location?.let {
                EventDetailRow("Location", it)
            }
            parsedEvent.eventType?.let {
                EventDetailRow("Type", it)
            }
            parsedEvent.parentOwner?.let {
                EventDetailRow("Assigned to", it)
            }

            // Validation issues
            AnimatedVisibility(visible = validationIssues.isNotEmpty()) {
                Column(modifier = Modifier.padding(top = 8.dp)) {
                    Text(
                        text = "Issues to review:",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        fontWeight = FontWeight.Bold
                    )
                    validationIssues.forEach { issue ->
                        Text(
                            text = "• $issue",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Action buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = onEdit,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.Edit, contentDescription = null)
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Edit")
                }

                Button(
                    onClick = onConfirm,
                    modifier = Modifier.weight(1f),
                    enabled = validationIssues.isEmpty()
                ) {
                    Icon(Icons.Default.Check, contentDescription = null)
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Confirm")
                }
            }
        }
    }
}

@Composable
fun EventDetailRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f)
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.weight(2f)
        )
    }
}

@Composable
fun ConfidenceBadge(confidence: Double) {
    val (color, label) = when {
        confidence >= 0.8 -> MaterialTheme.colorScheme.primary to "High"
        confidence >= 0.6 -> MaterialTheme.colorScheme.tertiary to "Medium"
        else -> MaterialTheme.colorScheme.error to "Low"
    }

    Surface(
        color = color.copy(alpha = 0.2f),
        shape = MaterialTheme.shapes.small
    ) {
        Text(
            text = "$label confidence",
            style = MaterialTheme.typography.labelSmall,
            color = color,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
        )
    }
}

@Composable
fun ErrorCard(message: String, onRetry: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "Error",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.error
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onErrorContainer
            )
            Spacer(modifier = Modifier.height(12.dp))
            OutlinedButton(onClick = onRetry) {
                Text("Retry")
            }
        }
    }
}

@Composable
fun SuccessCard(message: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = Icons.Default.Check,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(48.dp)
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = message,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
        }
    }
}
