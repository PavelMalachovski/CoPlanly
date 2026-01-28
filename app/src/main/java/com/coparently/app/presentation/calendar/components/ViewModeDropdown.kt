package com.coparently.app.presentation.calendar.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.coparently.app.presentation.calendar.CalendarViewMode
import com.coparently.app.presentation.theme.dimensions
import com.coparently.app.utils.LightDarkPreviews
import com.coparently.app.utils.PreviewWrapper

/**
 * Collapsible dropdown selector for calendar view modes.
 * Shows current mode as a compact button, expands to show all options on click.
 *
 * @param selectedMode Currently selected calendar view mode
 * @param onModeSelected Callback when a new mode is selected
 * @param modifier Modifier for the component
 */
@Composable
fun ViewModeDropdown(
    selectedMode: CalendarViewMode,
    onModeSelected: (CalendarViewMode) -> Unit,
    modifier: Modifier = Modifier
) {
    val dims = dimensions()
    var expanded by remember { mutableStateOf(false) }

    // Rotate arrow when expanded
    val arrowRotation by animateFloatAsState(
        targetValue = if (expanded) 180f else 0f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "arrowRotation"
    )

    Box(modifier = modifier) {
        // Current mode button (always visible)
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(dims.cornerRadius))
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .clickable { expanded = !expanded }
                .padding(horizontal = dims.paddingMedium, vertical = dims.paddingSmall)
                .semantics {
                    role = Role.DropdownList
                    contentDescription = "Calendar view mode: ${getModeLabel(selectedMode)}. Tap to change."
                },
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = getModeLabel(selectedMode),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.width(4.dp))
            Icon(
                imageVector = Icons.Default.KeyboardArrowDown,
                contentDescription = if (expanded) "Collapse" else "Expand",
                modifier = Modifier
                    .size(20.dp)
                    .rotate(arrowRotation),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        // Dropdown menu
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier
                .background(MaterialTheme.colorScheme.surface)
        ) {
            CalendarViewMode.values().forEach { mode ->
                val isSelected = mode == selectedMode
                DropdownMenuItem(
                    text = {
                        Text(
                            text = getModeLabel(mode),
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                            color = if (isSelected) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurface
                            }
                        )
                    },
                    onClick = {
                        onModeSelected(mode)
                        expanded = false
                    },
                    modifier = Modifier.semantics {
                        role = Role.RadioButton
                        contentDescription = "${getModeLabel(mode)} view mode"
                    }
                )
            }
        }
    }
}

/**
 * Gets display label for calendar view mode.
 */
private fun getModeLabel(mode: CalendarViewMode): String = when (mode) {
    CalendarViewMode.DAY -> "Day"
    CalendarViewMode.THREE_DAYS -> "3 Days"
    CalendarViewMode.WEEK -> "Week"
    CalendarViewMode.MONTH -> "Month"
}

// ==================== Previews ====================

/**
 * Preview of ViewModeDropdown in collapsed state.
 */
@LightDarkPreviews
@Composable
private fun ViewModeDropdownPreview() {
    PreviewWrapper {
        ViewModeDropdown(
            selectedMode = CalendarViewMode.MONTH,
            onModeSelected = {}
        )
    }
}

/**
 * Preview of ViewModeDropdown with different modes.
 */
@Preview(name = "Day Mode", showBackground = true)
@Composable
private fun ViewModeDropdownDayPreview() {
    PreviewWrapper {
        ViewModeDropdown(
            selectedMode = CalendarViewMode.DAY,
            onModeSelected = {}
        )
    }
}

@Preview(name = "Week Mode", showBackground = true)
@Composable
private fun ViewModeDropdownWeekPreview() {
    PreviewWrapper {
        ViewModeDropdown(
            selectedMode = CalendarViewMode.WEEK,
            onModeSelected = {}
        )
    }
}
