package com.coparently.app.presentation.common

import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

/**
 * The one conversion between a [LocalDate] and the millis Material3's `DatePickerState` speaks.
 *
 * `DatePickerState` works in **UTC-midnight** millis on both sides: it opens on the UTC day of
 * `initialSelectedDateMillis` and reports a picked day as that day's UTC midnight. Converting
 * through `ZoneId.systemDefault()` instead is the September 2026 audit's off-by-one
 * (`docs/AUDIT-2026-09.md` §1 item 3): east of Greenwich the picker highlighted the previous day,
 * west of it the form saved the previous day. Every picker goes through here so that bug has one
 * place to come back in, and one place the instrumented test pins in four time zones.
 *
 * `atZone(...).toLocalDate()` rather than `LocalDate.ofInstant`, which is API 34 (minSdk is 26).
 */
object PickerDates {

    /** The millis a `DatePickerState` must be given to open on [date]. */
    fun toPickerMillis(date: LocalDate): Long = date.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()

    /** The day a `DatePickerState` means by [millis]. */
    fun fromPickerMillis(millis: Long): LocalDate = Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate()
}

/**
 * A Material3 date picker in a dialog that takes and returns a [LocalDate].
 *
 * The event form, change requests, custody setup, expenses, the export range and the shared
 * child/pet picker all open this, so none of them handles picker millis itself (see
 * [PickerDates]). Confirming with a day selected reports it through [onConfirm]; confirming
 * without one, cancelling or dismissing only calls [onDismiss].
 *
 * @param initialDate The day the picker opens on, highlighted.
 * @param confirmLabel The confirm button's text, from the calling feature's strings.
 * @param dismissLabel The cancel button's text.
 * @param onConfirm Receives the picked day. [onDismiss] is called right after it.
 * @param onDismiss Closes the dialog.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LocalDatePickerDialog(
    initialDate: LocalDate,
    confirmLabel: String,
    dismissLabel: String,
    onConfirm: (LocalDate) -> Unit,
    onDismiss: () -> Unit
) {
    val pickerState = rememberDatePickerState(
        initialSelectedDateMillis = PickerDates.toPickerMillis(initialDate)
    )
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                onClick = {
                    pickerState.selectedDateMillis?.let { onConfirm(PickerDates.fromPickerMillis(it)) }
                    onDismiss()
                }
            ) {
                Text(confirmLabel)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(dismissLabel)
            }
        }
    ) {
        DatePicker(state = pickerState)
    }
}
