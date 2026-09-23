package com.coparently.app.presentation.components

import android.text.format.DateFormat
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import com.coparently.app.R
import com.coparently.app.utils.LightDarkPreviews
import com.coparently.app.utils.PreviewWrapper
import java.time.LocalTime

/**
 * Time picker dialog component for selecting time.
 * Based on Material 3 design guidelines.
 *
 * @param initialTime Initial time to display
 * @param onTimeSelected Callback when time is selected
 * @param onDismiss Callback when dialog is dismissed
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimePickerDialog(
    initialTime: LocalTime,
    onTimeSelected: (LocalTime) -> Unit,
    onDismiss: () -> Unit
) {
    val timePickerState = rememberTimePickerState(
        initialHour = initialTime.hour,
        initialMinute = initialTime.minute,
        // The device's own 12/24-hour setting, not a guess from the locale: a parent in the
        // US with 24-hour time switched on expects the dial they chose.
        is24Hour = DateFormat.is24HourFormat(LocalContext.current)
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                onClick = {
                    val selectedTime = LocalTime.of(
                        timePickerState.hour,
                        timePickerState.minute
                    )
                    onTimeSelected(selectedTime)
                    onDismiss()
                }
            ) {
                Text(stringResource(R.string.common_dialog_ok))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.common_dialog_cancel))
            }
        },
        text = {
            TimePicker(
                state = timePickerState,
                modifier = Modifier.fillMaxWidth()
            )
        }
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
            initialTime = LocalTime.of(14, 30),
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
            initialTime = LocalTime.of(9, 0),
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
            initialTime = LocalTime.of(18, 45),
            onTimeSelected = {},
            onDismiss = {}
        )
    }
}

