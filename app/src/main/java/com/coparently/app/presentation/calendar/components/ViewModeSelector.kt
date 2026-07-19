package com.coparently.app.presentation.calendar.components

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.coparently.app.presentation.calendar.CalendarViewMode
import com.coparently.app.presentation.theme.dimensions
import com.coparently.app.utils.LightDarkPreviews
import com.coparently.app.utils.PreviewWrapper

/**
 * Animated view mode selector with iOS-style sliding indicator.
 * Allows switching between Day, 3 Days, Week, and Month views.
 *
 * @param selectedMode Currently selected calendar view mode
 * @param onModeSelected Callback when a new mode is selected
 */
@Composable
fun ViewModeSelector(
    selectedMode: CalendarViewMode,
    onModeSelected: (CalendarViewMode) -> Unit
) {
    val dims = dimensions()
    val modes = CalendarViewMode.values()
    val selectedIndex = modes.indexOf(selectedMode)

    // Calculate button width dynamically (total width - padding) / number of modes
    val screenWidth = LocalConfiguration.current.screenWidthDp.dp
    val internalPadding = dims.paddingSmall / 2
    val selectorPadding = (dims.paddingMedium * 2) + (internalPadding * 2) // horizontal padding + internal padding
    val buttonWidth = (screenWidth - selectorPadding) / modes.size.toFloat()

    val indicatorOffset by animateDpAsState(
        targetValue = buttonWidth * selectedIndex.toFloat(),
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "indicatorOffset"
    )

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = dims.paddingMedium, vertical = dims.paddingSmall * 1.5f)
            .height(dims.buttonHeight * 0.86f) // ~48dp for compact
            .background(
                color = MaterialTheme.colorScheme.surfaceVariant,
                shape = RoundedCornerShape(dims.cornerRadius * 2)
            )
            .semantics {
                contentDescription = "Calendar view mode selector. Currently selected: ${
                    when (selectedMode) {
                        CalendarViewMode.MONTH -> "Month view"
                        CalendarViewMode.WEEK -> "Week view"
                        CalendarViewMode.DAY -> "Day view"
                    }
                }"
            }
            .padding(internalPadding)
    ) {
        // Animated background indicator
        Box(
            modifier = Modifier
                .width(buttonWidth)
                .height(dims.buttonHeight * 0.71f) // ~40dp for compact
                .offset(x = indicatorOffset)
                .background(
                    color = MaterialTheme.colorScheme.primaryContainer,
                    shape = RoundedCornerShape(dims.cornerRadius * 1.67f)
                )
                .graphicsLayer {
                    // Subtle shadow effect
                    shadowElevation = 2.dp.toPx()
                }
        )

        // Mode buttons
        Row(
            modifier = Modifier.fillMaxSize(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            modes.forEach { mode ->
                val isSelected = mode == selectedMode
                val modeLabel = when (mode) {
                    CalendarViewMode.MONTH -> "Month"
                    CalendarViewMode.WEEK -> "Week"
                    CalendarViewMode.DAY -> "Day"
                }

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(dims.buttonHeight * 0.71f) // ~40dp for compact
                        .semantics {
                            role = Role.RadioButton
                            selected = isSelected
                            contentDescription = "$modeLabel view mode"
                        }
                        .clickable(
                            onClickLabel = "Select $modeLabel view"
                        ) { onModeSelected(mode) },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = modeLabel,
                        style = MaterialTheme.typography.labelLarge,
                        color = if (isSelected) {
                            MaterialTheme.colorScheme.onPrimaryContainer
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                        modifier = Modifier.graphicsLayer {
                            // Scale animation
                            val scale = if (isSelected) 1.05f else 1f
                            scaleX = scale
                            scaleY = scale
                        }
                    )
                }
            }
        }
    }
}

// ==================== Previews ====================

/**
 * Preview of ViewModeSelector in both light and dark themes.
 */
@LightDarkPreviews
@Composable
private fun ViewModeSelectorPreview() {
    PreviewWrapper {
        ViewModeSelector(
            selectedMode = CalendarViewMode.MONTH,
            onModeSelected = {}
        )
    }
}

/**
 * Preview of ViewModeSelector with different modes selected.
 */
@Preview(name = "Day Mode", showBackground = true)
@Composable
private fun ViewModeSelectorDayPreview() {
    PreviewWrapper {
        ViewModeSelector(
            selectedMode = CalendarViewMode.DAY,
            onModeSelected = {}
        )
    }
}

@Preview(name = "Week Mode", showBackground = true)
@Composable
private fun ViewModeSelectorWeekPreview() {
    PreviewWrapper {
        ViewModeSelector(
            selectedMode = CalendarViewMode.WEEK,
            onModeSelected = {}
        )
    }
}

