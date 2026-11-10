package com.coparently.app.presentation.childinfo.components

import androidx.compose.runtime.Composable
import com.coparently.app.presentation.common.LocalDatePickerDialog
import com.coparently.app.resources.Res
import com.coparently.app.resources.childinfo_cancel
import com.coparently.app.resources.childinfo_ok
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.todayIn
import org.jetbrains.compose.resources.stringResource
import kotlin.time.Clock

/**
 * Material3 date picker dialog shared by the child and pet forms — dates of birth and
 * vaccinations.
 *
 * The millis conversion lives in [LocalDatePickerDialog]: this picker used to convert through the
 * system zone, which highlighted the previous day east of Greenwich (all of Central Europe) and
 * saved the previous day west of it (`docs/AUDIT-2026-09.md` §1 item 3).
 *
 * @param onDateSelected Callback when date is selected
 * @param onDismiss Callback when dialog is dismissed
 * @param initialDate Initial date to show in picker; today in the device's zone when null
 */
@Composable
fun DatePickerDialog(
    onDateSelected: (LocalDateTime) -> Unit,
    onDismiss: () -> Unit,
    initialDate: LocalDateTime? = null
) {
    LocalDatePickerDialog(
        initialDate = initialDate?.date ?: Clock.System.todayIn(TimeZone.currentSystemDefault()),
        confirmLabel = stringResource(Res.string.childinfo_ok),
        dismissLabel = stringResource(Res.string.childinfo_cancel),
        onConfirm = { selectedDate ->
            // Keep the time of day the caller already had (usually midnight).
            onDateSelected(LocalDateTime(selectedDate, initialDate?.time ?: LocalTime(0, 0)))
        },
        onDismiss = onDismiss
    )
}
