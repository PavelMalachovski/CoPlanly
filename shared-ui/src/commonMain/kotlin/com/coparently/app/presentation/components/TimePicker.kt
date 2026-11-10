package com.coparently.app.presentation.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.coparently.app.resources.Res
import com.coparently.app.resources.common_dialog_cancel
import com.coparently.app.resources.common_dialog_ok
import com.coparently.app.utils.ClockFormat
import kotlinx.datetime.LocalTime
import org.jetbrains.compose.resources.stringResource

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
        // US with 24-hour time switched on expects the dial they chose. `ClockFormat` holds what
        // `DateFormat.is24HourFormat` of the activity answers (`MainActivity` follows it).
        is24Hour = ClockFormat.is24Hour
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                onClick = {
                    val selectedTime = LocalTime(
                        timePickerState.hour,
                        timePickerState.minute
                    )
                    onTimeSelected(selectedTime)
                    onDismiss()
                }
            ) {
                Text(stringResource(Res.string.common_dialog_ok))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(Res.string.common_dialog_cancel))
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
