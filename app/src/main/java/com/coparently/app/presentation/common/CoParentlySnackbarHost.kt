package com.coparently.app.presentation.common

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp

/**
 * Custom SnackbarHost for CoParently app with enhanced visual feedback.
 * Automatically detects notification type based on message content and displays
 * appropriate icon and color.
 *
 * Supported types:
 * - Success: Green checkmark (contains "success", "created", "saved", "updated")
 * - Error: Red error icon (contains "error", "failed", "couldn't")
 * - Warning: Orange warning icon (contains "warning", "caution")
 * - Info: Blue info icon (default)
 *
 * @param snackbarHostState State for managing snackbar queue
 * @param modifier Modifier for the host
 */
@Composable
fun CoParentlySnackbarHost(
    snackbarHostState: SnackbarHostState,
    modifier: Modifier = Modifier
) {
    SnackbarHost(
        hostState = snackbarHostState,
        modifier = modifier
    ) { snackbarData ->
        val message = snackbarData.visuals.message.lowercase()

        // Determine snackbar type based on message content
        val (icon, iconColor) = when {
            message.contains("success") ||
            message.contains("created") ||
            message.contains("saved") ||
            message.contains("updated") ||
            message.contains("deleted") -> {
                Icons.Default.CheckCircle to Color(0xFF4CAF50) // Green
            }
            message.contains("error") ||
            message.contains("failed") ||
            message.contains("couldn't") ||
            message.contains("unable") -> {
                Icons.Default.Error to Color(0xFFF44336) // Red
            }
            message.contains("warning") ||
            message.contains("caution") ||
            message.contains("attention") -> {
                Icons.Default.Warning to Color(0xFFFF9800) // Orange
            }
            else -> {
                Icons.Default.Info to MaterialTheme.colorScheme.primary // Blue
            }
        }

        Snackbar(
            modifier = Modifier.padding(16.dp),
            shape = RoundedCornerShape(16.dp),
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
            contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
            action = {
                snackbarData.visuals.actionLabel?.let { actionLabel ->
                    TextButton(
                        onClick = { snackbarData.performAction() }
                    ) {
                        Text(actionLabel)
                    }
                } ?: run {
                    TextButton(onClick = { snackbarData.dismiss() }) {
                        Text("Dismiss")
                    }
                }
            }
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = iconColor,
                    modifier = Modifier.size(24.dp)
                )

                Text(
                    text = snackbarData.visuals.message,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }
    }
}

/**
 * Helper data class for snackbar types.
 * Can be used to explicitly specify snackbar type instead of auto-detection.
 */
data class SnackbarType(
    val icon: ImageVector,
    val color: Color
) {
    companion object {
        val Success = SnackbarType(
            icon = Icons.Default.CheckCircle,
            color = Color(0xFF4CAF50)
        )

        val Error = SnackbarType(
            icon = Icons.Default.Error,
            color = Color(0xFFF44336)
        )

        val Warning = SnackbarType(
            icon = Icons.Default.Warning,
            color = Color(0xFFFF9800)
        )

        val Info = SnackbarType(
            icon = Icons.Default.Info,
            color = Color(0xFF2196F3)
        )
    }
}
