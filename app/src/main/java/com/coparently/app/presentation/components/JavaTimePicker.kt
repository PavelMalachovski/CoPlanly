// The previews' fixture times are literal on purpose, as they were in TimePicker.kt (baselined there).
@file:Suppress("MagicNumber")

package com.coparently.app.presentation.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.coparently.app.utils.LightDarkPreviews
import com.coparently.app.utils.PreviewWrapper
import kotlinx.datetime.LocalTime
import kotlinx.datetime.toJavaLocalTime
import kotlinx.datetime.toKotlinLocalTime

/**
 * [TimePickerDialog] for a `java.time.LocalTime`, for the app's screens that still hold one until
 * their slice moves them to `:shared-ui` (MON-24 phase 2, Task 11): the same dialog, the time
 * converted on the way in and out.
 */
@Composable
fun TimePickerDialog(
    initialTime: java.time.LocalTime,
    onTimeSelected: (java.time.LocalTime) -> Unit,
    onDismiss: () -> Unit
) {
    TimePickerDialog(
        initialTime = initialTime.toKotlinLocalTime(),
        onTimeSelected = { onTimeSelected(it.toJavaLocalTime()) },
        onDismiss = onDismiss
    )
}

// ==================== Previews ====================

/**
 * Preview of TimePickerDialog in both light and dark themes.
 */
@LightDarkPreviews
@Composable
private fun TimePickerDialogPreview() {
    PreviewWrapper {
        TimePickerDialog(
            initialTime = LocalTime(14, 30),
            onTimeSelected = {},
            onDismiss = {}
        )
    }
}

/**
 * Preview of TimePickerDialog with morning time.
 */
@Preview(name = "Morning Time", showBackground = true)
@Composable
private fun TimePickerDialogMorningPreview() {
    PreviewWrapper {
        TimePickerDialog(
            initialTime = LocalTime(9, 0),
            onTimeSelected = {},
            onDismiss = {}
        )
    }
}

/**
 * Preview of TimePickerDialog with evening time.
 */
@Preview(name = "Evening Time", showBackground = true)
@Composable
private fun TimePickerDialogEveningPreview() {
    PreviewWrapper {
        TimePickerDialog(
            initialTime = LocalTime(18, 45),
            onTimeSelected = {},
            onDismiss = {}
        )
    }
}
