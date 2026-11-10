// The previews' fixture times are literal on purpose, as they were in TimePicker.kt (baselined there).
@file:Suppress("MagicNumber")

package com.coparently.app.presentation.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.coparently.app.utils.LightDarkPreviews
import com.coparently.app.utils.PreviewWrapper
import kotlinx.datetime.LocalTime

/*
 * Previews of `:shared-ui`'s [TimePickerDialog]. Its `java.time` overload, for the screens still in
 * the app, went with the last of them, the change request form (MON-24 phase 2, slice 11d).
 */

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
