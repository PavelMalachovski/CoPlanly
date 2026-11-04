package com.coparently.app.presentation.common

import androidx.compose.animation.ContentTransform
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import kotlinx.datetime.YearMonth

// MONTH_PAGING_MS and monthPagingTween are in MonthPagingTween.kt (MON-24 phase 2, slice 11c); the
// direction and the transition followed with Expenses (slice 11e), on kotlinx-datetime's YearMonth,
// whose `compareTo` orders months as java.time's did.

/**
 * Which way a month change should travel on screen.
 *
 * A later month arrives from the end edge and the old one leaves towards the start — the
 * direction a finger swiping left would have carried it — and an earlier month does the reverse.
 * Extracted from the composable because it is the one part of a slide transition that can be
 * wrong rather than merely ugly, and the only part worth a test.
 *
 * @return `1` when [target] is later than [from], `-1` when earlier, `0` when they are the same
 *   month and nothing should move.
 */
fun monthPagingDirection(from: YearMonth, target: YearMonth): Int = target.compareTo(from).coerceIn(-1, 1)

/**
 * The transition between two months: a directional slide over [monthPagingTween].
 *
 * A later month arrives from the end edge while the old one leaves towards the start, so the
 * movement matches the finger that asked for it — the same reading as the calendar's pager.
 *
 * When the month has **not** changed the answer is no transition at all. That case is not
 * hypothetical: the content also changes when an expense is added, deleted or restored by Undo,
 * and sliding there would announce a page turn that never happened.
 *
 * Built through the [ContentTransform] constructor rather than the `togetherWith … using` infix
 * pair: `using` is not part of this Compose version's animation API, and naming the size transform
 * as an argument says the same thing without depending on which infix helpers happen to exist.
 */
fun monthPagingTransition(from: YearMonth, target: YearMonth): ContentTransform {
    val direction = monthPagingDirection(from, target)
    if (direction == 0) {
        return ContentTransform(
            targetContentEnter = EnterTransition.None,
            initialContentExit = ExitTransition.None,
            sizeTransform = null
        )
    }

    return ContentTransform(
        targetContentEnter = slideInHorizontally(monthPagingTween()) { width -> direction * width } +
            fadeIn(monthPagingTween()),
        initialContentExit = slideOutHorizontally(monthPagingTween()) { width -> -direction * width } +
            fadeOut(monthPagingTween()),
        // `clip = false` so the month with the shorter list is not cropped to the other's height
        // while the two are side by side. They only overlap for the length of the slide.
        sizeTransform = SizeTransform(clip = false)
    )
}
