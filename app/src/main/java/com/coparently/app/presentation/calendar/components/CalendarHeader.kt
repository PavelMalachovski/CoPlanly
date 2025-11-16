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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.coparently.app.R
import java.time.LocalDate
import java.time.YearMonth

/**
 * Calendar screen header with month/year title and action buttons.
 *
 * @param selectedDate Currently selected date to display month/year
 * @param onNavigateToToday Callback when user clicks "Today" button
 * @param onSettingsClick Optional callback for settings button, null to hide button
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CalendarHeader(
    selectedDate: LocalDate,
    onNavigateToToday: () -> Unit,
    onSettingsClick: (() -> Unit)? = null
) {
    TopAppBar(
        title = {
            Text(
                text = "${
                    YearMonth.from(selectedDate).month.getDisplayName(
                        java.time.format.TextStyle.SHORT,
                        java.util.Locale.getDefault()
                    ).take(3).uppercase()
                } ${YearMonth.from(selectedDate).year}",
                style = MaterialTheme.typography.headlineMedium,
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

