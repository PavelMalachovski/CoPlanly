package com.coparently.app.presentation.common

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Today
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.coparently.app.utils.LightDarkPreviews
import com.coparently.app.utils.PreviewWrapper

/**
 * Quick Actions Bottom Sheet providing fast access to common calendar actions.
 *
 * Features:
 * - Create new event
 * - Jump to today
 * - Open settings
 *
 * @param onEventCreate Callback when user wants to create a new event
 * @param onNavigateToToday Callback when user wants to jump to today
 * @param onShowSettings Callback when user wants to open settings
 * @param onDismiss Callback when bottom sheet should be dismissed
 * @param sheetState State of the bottom sheet for controlling visibility
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuickActionsBottomSheet(
    onEventCreate: () -> Unit,
    onNavigateToToday: () -> Unit,
    onShowSettings: () -> Unit,
    onDismiss: () -> Unit,
    sheetState: SheetState = rememberModalBottomSheetState()
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Header
            Text(
                text = "Quick Actions",
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            // New Event Action
            QuickActionItem(
                icon = Icons.Default.Add,
                title = "New Event",
                subtitle = "Create a new calendar event",
                onClick = {
                    onEventCreate()
                    onDismiss()
                }
            )

            // Jump to Today Action
            QuickActionItem(
                icon = Icons.Default.Today,
                title = "Jump to Today",
                subtitle = "Navigate to current date",
                onClick = {
                    onNavigateToToday()
                    onDismiss()
                }
            )

            // Settings Action
            QuickActionItem(
                icon = Icons.Default.Settings,
                title = "Settings",
                subtitle = "App preferences",
                onClick = {
                    onShowSettings()
                    onDismiss()
                }
            )
        }
    }
}

/**
 * Individual quick action item with icon, title, and subtitle.
 *
 * @param icon Icon to display
 * @param title Main title text
 * @param subtitle Descriptive subtitle text
 * @param onClick Callback when item is clicked
 * @param modifier Modifier for the item container
 */
@Composable
fun QuickActionItem(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Start
        ) {
            // Icon
            Icon(
                imageVector = icon,
                contentDescription = title,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(24.dp)
            )

            Spacer(modifier = Modifier.width(16.dp))

            // Text content
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )

                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

// ==================== Previews ====================

/**
 * Preview of QuickActionItem.
 */
@LightDarkPreviews
@Composable
private fun QuickActionItemPreview() {
    PreviewWrapper {
        QuickActionItem(
            icon = Icons.Default.Add,
            title = "New Event",
            subtitle = "Create a new calendar event",
            onClick = {}
        )
    }
}

/**
 * Preview of multiple QuickActionItems.
 */
@Preview(name = "Quick Actions List", showBackground = true)
@Composable
private fun QuickActionsListPreview() {
    PreviewWrapper {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = "Quick Actions",
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            QuickActionItem(
                icon = Icons.Default.Add,
                title = "New Event",
                subtitle = "Create a new calendar event",
                onClick = {}
            )

            QuickActionItem(
                icon = Icons.Default.Today,
                title = "Jump to Today",
                subtitle = "Navigate to current date",
                onClick = {}
            )

            QuickActionItem(
                icon = Icons.Default.Settings,
                title = "Settings",
                subtitle = "App preferences",
                onClick = {}
            )
        }
    }
}
