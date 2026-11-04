package com.coparently.app.presentation.common

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.tween

/**
 * How long a month takes to change, anywhere in the app.
 *
 * The calendar's pager settles with this and nothing else — the fling velocity decides *which*
 * month wins, never *how* it gets there — and Expenses now changes month over the same duration
 * and easing, so paging feels like one gesture in one app rather than two screens that happen to
 * have months.
 *
 * Deliberately shared rather than copied: the value that matters here is not 500 in particular,
 * it is that both places use the *same* number. Two constants half a codebase apart drift the
 * first time somebody tunes one of them.
 */
const val MONTH_PAGING_MS = com.coparently.app.presentation.theme.Motion.LONG_MS

/** The month-change animation: one calm, direction-independent tween. */
fun <T> monthPagingTween(): FiniteAnimationSpec<T> =
    tween(durationMillis = MONTH_PAGING_MS, easing = FastOutSlowInEasing)
