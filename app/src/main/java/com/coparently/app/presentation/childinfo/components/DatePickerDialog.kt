package com.coparently.app.presentation.childinfo.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.coparently.app.R
import com.coparently.app.presentation.common.LocalDatePickerDialog
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

/**
 * Material3 date picker dialog shared by the child and pet forms, the profile and onboarding —
 * dates of birth and vaccinations.
 *
 * The millis conversion lives in [LocalDatePickerDialog]: this picker used to convert through the
 * system zone, which highlighted the previous day east of Greenwich (all of Central Europe) and
 * saved the previous day west of it (`docs/AUDIT-2026-09.md` §1 item 3).
 *
 * @param onDateSelected Callback when date is selected
 * @param onDismiss Callback when dialog is dismissed
 * @param initialDate Initial date to show in picker
 */
@Composable
fun DatePickerDialog(
    onDateSelected: (LocalDateTime) -> Unit,
    onDismiss: () -> Unit,
    initialDate: LocalDateTime? = null
) {
    LocalDatePickerDialog(
        initialDate = initialDate?.toLocalDate() ?: LocalDate.now(),
        confirmLabel = stringResource(R.string.childinfo_ok),
        dismissLabel = stringResource(R.string.childinfo_cancel),
        onConfirm = { selectedDate ->
            // Keep the time of day the caller already had (usually midnight).
            onDateSelected(LocalDateTime.of(selectedDate, initialDate?.toLocalTime() ?: LocalTime.MIDNIGHT))
        },
        onDismiss = onDismiss
    )
}
