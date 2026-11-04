package com.coparently.app.presentation.common

import androidx.compose.runtime.Composable
import kotlinx.datetime.toJavaLocalDate
import kotlinx.datetime.toKotlinLocalDate
import java.time.LocalDate

/**
 * The `java.time` side of [PickerDates], for the app's screens that still hold a
 * `java.time.LocalDate` until their slice moves them to `:shared-ui` (MON-24 phase 2, Task 11). Each
 * converts the day and calls the one shared conversion, so the UTC rule stays in one place.
 */
fun PickerDates.toPickerMillis(date: LocalDate): Long = toPickerMillis(date.toKotlinLocalDate())

/** [PickerDates.fromPickerMillis] as a `java.time.LocalDate`. */
fun PickerDates.fromPickerMillisJava(millis: Long): LocalDate = fromPickerMillis(millis).toJavaLocalDate()

/**
 * [LocalDatePickerDialog] for a `java.time.LocalDate`: the same dialog, the day converted on the
 * way in and out.
 */
@Composable
fun LocalDatePickerDialog(
    initialDate: LocalDate,
    confirmLabel: String,
    dismissLabel: String,
    onConfirm: (LocalDate) -> Unit,
    onDismiss: () -> Unit
) {
    LocalDatePickerDialog(
        initialDate = initialDate.toKotlinLocalDate(),
        confirmLabel = confirmLabel,
        dismissLabel = dismissLabel,
        onConfirm = { onConfirm(it.toJavaLocalDate()) },
        onDismiss = onDismiss
    )
}
