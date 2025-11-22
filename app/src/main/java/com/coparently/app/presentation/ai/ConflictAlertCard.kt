package com.coparently.app.presentation.ai

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.coparently.app.domain.model.ai.ConflictSeverity
import com.coparently.app.domain.model.ai.UpcomingConflict
import java.time.Duration

/**
 * Card displaying an upcoming conflict alert
 * Day 1 - Feature 1.2: Proactive conflict alerts
 */
@Composable
fun ConflictAlertCard(
    conflict: UpcomingConflict,
    onViewSolutions: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        colors = CardDefaults.cardColors(
            containerColor = when (conflict.severity) {
                ConflictSeverity.HIGH -> MaterialTheme.colorScheme.errorContainer
                ConflictSeverity.MEDIUM -> MaterialTheme.colorScheme.tertiaryContainer
                ConflictSeverity.LOW -> MaterialTheme.colorScheme.surfaceVariant
            }
        ),
        border = BorderStroke(
            1.dp,
            when (conflict.severity) {
                ConflictSeverity.HIGH -> MaterialTheme.colorScheme.error
                ConflictSeverity.MEDIUM -> MaterialTheme.colorScheme.tertiary
                ConflictSeverity.LOW -> MaterialTheme.colorScheme.outline
            }
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = conflict.title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )

                    Text(
                        text = conflict.description,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(top = 4.dp)
                    )

                    Text(
                        text = formatTimeUntil(conflict.timeUntil),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }

                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, contentDescription = "Dismiss")
                }
            }

            AnimatedVisibility(visible = conflict.suggestedSolutions.isNotEmpty()) {
                OutlinedButton(
                    onClick = onViewSolutions,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp)
                ) {
                    Text("View Solutions (${conflict.suggestedSolutions.size})")
                }
            }
        }
    }
}

private fun formatTimeUntil(duration: Duration): String {
    val days = duration.toDays()
    val hours = duration.toHours() % 24

    return when {
        days > 0 -> "In $days day${if (days > 1) "s" else ""}"
        hours > 0 -> "In $hours hour${if (hours > 1) "s" else ""}"
        else -> "Shortly"
    }
}
