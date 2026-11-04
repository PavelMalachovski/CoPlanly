package com.coparently.app.presentation.common

import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.remember
import kotlinx.datetime.toJavaLocalDate
import java.time.LocalDate

/**
 * [rememberToday] as a `java.time.LocalDate`, for the app's screens that still hold one until their
 * slice moves them to `:shared-ui` (MON-24 phase 2, Task 11). The same state — the one midnight
 * timer and [LocalFixedToday] — converted, so the two can never disagree about today.
 */
@Composable
fun rememberJavaToday(): State<LocalDate> {
    val today = rememberToday()
    return remember(today) { derivedStateOf { today.value.toJavaLocalDate() } }
}
