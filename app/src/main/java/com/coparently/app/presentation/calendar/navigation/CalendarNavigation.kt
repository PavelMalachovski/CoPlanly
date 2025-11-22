package com.coparently.app.presentation.calendar.navigation

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import com.coparently.app.presentation.calendar.CalendarViewMode
import com.coparently.app.utils.AnimationConstants
import java.time.LocalDate
import kotlin.math.abs

/**
 * Calendar navigation wrapper that adds swipe gesture support to navigate between dates.
 *
 * Features:
 * - Horizontal swipe gestures with visual preview
 * - Threshold-based navigation (20% of screen width)
 * - Support for all calendar view modes
 * - Smooth animated transitions
 *
 * @param currentDate Currently displayed date
 * @param viewMode Current calendar view mode (DAY, THREE_DAYS, WEEK, MONTH)
 * @param onDateChange Callback when date should change
 * @param modifier Modifier for the navigation container
 * @param content Content to display (typically CalendarHeader)
 */
@Composable
fun CalendarNavigation(
    currentDate: LocalDate,
    viewMode: CalendarViewMode,
    onDateChange: (LocalDate) -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable (LocalDate) -> Unit
) {
    // Preview state for swipe gesture
    var previewOffset by remember { mutableFloatStateOf(0f) }

    // Get screen width in pixels for threshold calculation
    val density = LocalDensity.current

    Box(
        modifier = modifier
            .fillMaxWidth()
            .pointerInput(viewMode) {
                detectHorizontalDragGestures(
                    onDragStart = { },
                    onHorizontalDrag = { change, dragAmount ->
                        change.consume()
                        // Apply dampening factor for smooth preview
                        previewOffset += dragAmount * 0.3f
                    },
                    onDragEnd = {
                        // Calculate threshold (20% of screen width)
                        val threshold = size.width * 0.2f

                        if (abs(previewOffset) > threshold) {
                            // Determine direction: positive offset = swipe right = go to previous
                            val direction = if (previewOffset > 0) -1 else 1

                            // Calculate new date based on view mode
                            val newDate = when (viewMode) {
                                CalendarViewMode.DAY -> currentDate.plusDays(direction.toLong())
                                CalendarViewMode.THREE_DAYS -> currentDate.plusDays((direction * 3).toLong())
                                CalendarViewMode.WEEK -> currentDate.plusWeeks(direction.toLong())
                                CalendarViewMode.MONTH -> currentDate.plusMonths(direction.toLong())
                            }

                            onDateChange(newDate)
                        }

                        // Reset preview offset
                        previewOffset = 0f
                    },
                    onDragCancel = {
                        // Reset preview offset on cancel
                        previewOffset = 0f
                    }
                )
            }
    ) {
        // Animated content with preview offset
        AnimatedContent(
            targetState = currentDate,
            modifier = Modifier.graphicsLayer {
                translationX = previewOffset
            },
            transitionSpec = {
                // Determine slide direction based on date comparison
                val slideDirection = if (targetState.isAfter(initialState)) 1 else -1

                slideInHorizontally(
                    animationSpec = tween(AnimationConstants.NORMAL),
                    initialOffsetX = { fullWidth -> slideDirection * fullWidth }
                ) togetherWith slideOutHorizontally(
                    animationSpec = tween(AnimationConstants.NORMAL),
                    targetOffsetX = { fullWidth -> -slideDirection * fullWidth }
                )
            },
            label = "calendar_navigation_animation"
        ) { date ->
            content(date)
        }
    }
}
