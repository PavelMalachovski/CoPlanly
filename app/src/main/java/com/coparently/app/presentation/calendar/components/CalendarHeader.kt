package com.coparently.app.presentation.calendar.components

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
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
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CalendarHeader(
    selectedDate: LocalDate,
    viewMode: CalendarViewMode = CalendarViewMode.MONTH,
    onViewModeChange: (CalendarViewMode) -> Unit = {},
    onNavigateToToday: () -> Unit,
    onSettingsClick: (() -> Unit)? = null
) {
    TopAppBar(
        navigationIcon = {
            // View mode dropdown in navigation icon position (left side)
            ViewModeDropdown(
                selectedMode = viewMode,
                onModeSelected = onViewModeChange,
                modifier = Modifier.padding(start = 8.dp)
            )
        },
        title = {
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
        },
        actions = {
            // Today button
            TodayButton(
                currentDay = LocalDate.now().dayOfMonth,
                onClick = onNavigateToToday
            )

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
