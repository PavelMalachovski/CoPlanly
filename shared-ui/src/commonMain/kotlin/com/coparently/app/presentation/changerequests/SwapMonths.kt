package com.coparently.app.presentation.changerequests

import androidx.compose.runtime.Composable
import com.coparently.app.presentation.calendar.MiniMonthModel
import com.coparently.app.presentation.calendar.components.MiniMonth
import com.coparently.app.presentation.common.ParentNames

/**
 * The months an offer's days fall in, as [com.coparently.app.presentation.calendar.swapMiniMonths]
 * built them: the calendar's swap sheet, the inbox's swap card and Home's swap dialog draw them alike.
 * Out of the app's `SwapDayAnswers.kt` since the sheet moved to `:shared-ui` (MON-24 phase 2, slice
 * 11c).
 */
@Composable
fun SwapMonths(months: List<MiniMonthModel>, parentNames: ParentNames) {
    months.forEach { MiniMonth(model = it, parentNames = parentNames) }
}
