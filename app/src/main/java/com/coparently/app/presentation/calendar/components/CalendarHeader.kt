package com.coparently.app.presentation.calendar.components

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ViewList
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.coparently.app.R
import com.coparently.app.presentation.calendar.CalendarViewMode
import com.coparently.app.utils.LightDarkPreviews
import com.coparently.app.utils.PreviewWrapper
import java.time.LocalDate
import java.time.YearMonth

/**
 * Calendar screen header. The month/year title itself is the view-mode selector:
 * tapping "July 2026 ▾" opens a menu to switch between Month, Week and Day. This
 * keeps the top bar compact (no separate selector chip) and the title stays fixed
 * while the calendar body swipes underneath.
 *
 * @param selectedDate Currently selected date to display month/year
 * @param viewMode Current calendar view mode
 * @param onViewModeChange Callback when view mode changes
 * @param onNavigateToToday Callback when user clicks "Today" button
 * @param onSettingsClick Optional callback for settings button, null to hide button
 * @param onChangeRequestsClick Optional callback for the change-requests inbox, null to hide button
 * @param pendingChangeRequests Number of pending incoming change requests (badge on the inbox icon)
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CalendarHeader(
    selectedDate: LocalDate,
    viewMode: CalendarViewMode = CalendarViewMode.MONTH,
    onViewModeChange: (CalendarViewMode) -> Unit = {},
    onNavigateToToday: () -> Unit,
    onSettingsClick: (() -> Unit)? = null,
    onChangeRequestsClick: (() -> Unit)? = null,
    pendingChangeRequests: Int = 0,
    onWeeklySummaryClick: (() -> Unit)? = null
) {
    TopAppBar(
        title = {
            MonthTitleSelector(
                selectedDate = selectedDate,
                viewMode = viewMode,
                onViewModeChange = onViewModeChange
            )
        },
        actions = {
            TodayButton(
                currentDay = LocalDate.now().dayOfMonth,
                onClick = onNavigateToToday
            )

            onWeeklySummaryClick?.let { onClick ->
                IconButton(onClick = onClick) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ViewList,
                        contentDescription = "Weekly summary",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            onChangeRequestsClick?.let { onClick ->
                ChangeRequestsButton(
                    pendingCount = pendingChangeRequests,
                    onClick = onClick
                )
            }

            onSettingsClick?.let { onClick ->
                SettingsButton(onClick = onClick)
            }
        }
    )
}

/**
 * Clickable "Month Year ▾" title that opens the view-mode menu.
 */
@Composable
private fun MonthTitleSelector(
    selectedDate: LocalDate,
    viewMode: CalendarViewMode,
    onViewModeChange: (CalendarViewMode) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val chevronRotation by animateFloatAsState(
        targetValue = if (expanded) 180f else 0f,
        animationSpec = spring(stiffness = Spring.StiffnessMedium),
        label = "chevronRotation"
    )
    val yearMonth = YearMonth.from(selectedDate)
    val monthLabel = "${
        yearMonth.month.getDisplayName(
            java.time.format.TextStyle.FULL_STANDALONE,
            java.util.Locale.getDefault()
        ).replaceFirstChar { it.uppercase() }
    } ${yearMonth.year}"

    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .clickable { expanded = true }
            .padding(horizontal = 6.dp, vertical = 4.dp)
            .semantics {
                role = Role.DropdownList
                contentDescription = "$monthLabel, ${viewModeLabel(viewMode)} view. Tap to change view."
            },
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = monthLabel,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Icon(
            imageVector = Icons.Default.ArrowDropDown,
            contentDescription = null,
            modifier = Modifier.rotate(chevronRotation)
        )

        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            CalendarViewMode.entries.forEach { mode ->
                val isSelected = mode == viewMode
                DropdownMenuItem(
                    text = {
                        Text(
                            text = viewModeLabel(mode),
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                            color = if (isSelected) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurface
                            }
                        )
                    },
                    onClick = {
                        onViewModeChange(mode)
                        expanded = false
                    }
                )
            }
        }
    }
}

/** Display label for a calendar view mode. */
private fun viewModeLabel(mode: CalendarViewMode): String = when (mode) {
    CalendarViewMode.MONTH -> "Month"
    CalendarViewMode.WEEK -> "Week"
    CalendarViewMode.DAY -> "Day"
}

/**
 * Today button showing current day number.
 */
@Composable
private fun TodayButton(
    currentDay: Int,
    onClick: () -> Unit
) {
    TextButton(
        onClick = onClick,
        modifier = Modifier.padding(horizontal = 4.dp)
    ) {
        Text(
            text = currentDay.toString(),
            style = MaterialTheme.typography.titleLarge,
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )
    }
}

/**
 * Change-requests inbox button with a badge for pending incoming requests.
 *
 * @param pendingCount Number of pending incoming requests; badge hidden when zero
 * @param onClick Callback when button is clicked
 */
@Composable
private fun ChangeRequestsButton(
    pendingCount: Int,
    onClick: () -> Unit
) {
    IconButton(onClick = onClick) {
        BadgedBox(
            badge = {
                if (pendingCount > 0) {
                    Badge { Text(pendingCount.toString()) }
                }
            }
        ) {
            Icon(
                imageVector = Icons.Default.SwapHoriz,
                contentDescription = "Change requests",
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/**
 * Settings icon button.
 */
@Composable
private fun SettingsButton(onClick: () -> Unit) {
    IconButton(onClick = onClick) {
        Icon(
            imageVector = Icons.Default.Settings,
            contentDescription = stringResource(R.string.calendar_settings),
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

// ==================== Previews ====================

@LightDarkPreviews
@Composable
private fun CalendarHeaderWithSettingsPreview() {
    PreviewWrapper {
        CalendarHeader(
            selectedDate = LocalDate.now(),
            viewMode = CalendarViewMode.MONTH,
            onViewModeChange = {},
            onNavigateToToday = {},
            onSettingsClick = {}
        )
    }
}

@Preview(name = "Week mode", showBackground = true)
@Composable
private fun CalendarHeaderWeekPreview() {
    PreviewWrapper {
        CalendarHeader(
            selectedDate = LocalDate.of(2026, 7, 20),
            viewMode = CalendarViewMode.WEEK,
            onViewModeChange = {},
            onNavigateToToday = {},
            onSettingsClick = {}
        )
    }
}
