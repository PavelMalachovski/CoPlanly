package com.coparently.app.presentation.calendar.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ViewList
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
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
 * Calendar screen header with view mode selector, month/year title and action buttons.
 * This component is static and does not animate with page swipes.
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
            // Month/year title with the view-mode selector placed right next to it,
            // so the current view is chosen from within the "July 2026" header.
            // This title stays fixed while the calendar body swipes underneath.
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                val yearMonth = YearMonth.from(selectedDate)
                Text(
                    text = "${
                        yearMonth.month.getDisplayName(
                            java.time.format.TextStyle.FULL_STANDALONE,
                            java.util.Locale.getDefault()
                        ).replaceFirstChar { it.uppercase() }
                    } ${yearMonth.year}",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                ViewModeDropdown(
                    selectedMode = viewMode,
                    onModeSelected = onViewModeChange
                )
            }
        },
        actions = {
            // Today button
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
 * Today button showing current day number.
 *
 * @param currentDay Current day of month (1-31)
 * @param onClick Callback when button is clicked
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
 *
 * @param onClick Callback when button is clicked
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

/**
 * Preview of CalendarHeader with settings button.
 */
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

/**
 * Preview of CalendarHeader without settings button.
 */
@Preview(name = "No Settings Button", showBackground = true)
@Composable
private fun CalendarHeaderNoSettingsPreview() {
    PreviewWrapper {
        CalendarHeader(
            selectedDate = LocalDate.now(),
            viewMode = CalendarViewMode.WEEK,
            onViewModeChange = {},
            onNavigateToToday = {},
            onSettingsClick = null
        )
    }
}

/**
 * Preview of CalendarHeader with different date.
 */
@Preview(name = "Different Date", showBackground = true)
@Composable
private fun CalendarHeaderDifferentDatePreview() {
    PreviewWrapper {
        CalendarHeader(
            selectedDate = LocalDate.of(2025, 12, 25),
            viewMode = CalendarViewMode.DAY,
            onViewModeChange = {},
            onNavigateToToday = {},
            onSettingsClick = {}
        )
    }
}
