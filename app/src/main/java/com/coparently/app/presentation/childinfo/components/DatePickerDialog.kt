package com.coparently.app.presentation.childinfo.components

import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.res.stringResource
import com.coparently.app.R
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneOffset

/**
 * Material3 Date Picker Dialog for selecting child's date of birth.
 *
 * @param onDateSelected Callback when date is selected
 * @param onDismiss Callback when dialog is dismissed
 * @param initialDate Initial date to show in picker
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DatePickerDialog(
    onDateSelected: (LocalDateTime) -> Unit,
    onDismiss: () -> Unit,
    initialDate: LocalDateTime? = null
) {
    // Material3's DatePickerState speaks UTC-midnight millis on both sides. Converting through
    // the system zone highlighted the previous day east of Greenwich (all of Central Europe) and
    // saved the previous day west of it — the bug CustodySetupScreen had already fixed.
    val datePickerState = rememberDatePickerState(
        initialSelectedDateMillis = (initialDate?.toLocalDate() ?: LocalDate.now())
            .atStartOfDay(ZoneOffset.UTC)
            .toInstant()
            .toEpochMilli()
    )

    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                onClick = {
                    datePickerState.selectedDateMillis?.let { millis ->
                        val selectedDate = Instant.ofEpochMilli(millis)
                            .atZone(ZoneOffset.UTC)
                            .toLocalDate()
                        // Keep the time of day the caller already had (usually midnight).
                        val time = initialDate?.toLocalTime() ?: java.time.LocalTime.MIDNIGHT
                        onDateSelected(LocalDateTime.of(selectedDate, time))
                    }
                    onDismiss()
                }
            ) {
                Text(stringResource(R.string.childinfo_ok))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.childinfo_cancel))
            }
        }
    ) {
        DatePicker(
            state = datePickerState,
            showModeToggle = true
        )
    }
}

