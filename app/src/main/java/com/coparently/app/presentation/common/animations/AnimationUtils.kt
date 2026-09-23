package com.coparently.app.presentation.common.animations

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.ui.graphics.TransformOrigin

/**
 * Utility file for common animations in the app.
 * Provides reusable animation specifications following Material Design guidelines.
 */

/**
 * Standard duration for short animations (150ms).
 */
const val ANIMATION_DURATION_SHORT = com.coparently.app.presentation.theme.Motion.SHORT_MS

/**
 * Standard duration for medium animations (300ms).
 */
const val ANIMATION_DURATION_MEDIUM = com.coparently.app.presentation.theme.Motion.MEDIUM_MS

/**
 * Standard duration for long animations (500ms).
 */
const val ANIMATION_DURATION_LONG = com.coparently.app.presentation.theme.Motion.LONG_MS

/**
 * Standard easing for emphasized animations (deceleration).
 * Material Design emphasized easing.
 */
val EmphasizedEasing = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1f)

/**
 * Standard easing for emphasized accelerate (deceleration).
 */
val EmphasizedAccelerateEasing = CubicBezierEasing(0.3f, 0.0f, 0.8f, 0.15f)

/**
 * Standard easing for emphasized decelerate.
 */
val EmphasizedDecelerateEasing = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1.0f)

/**
 * Standard enter transition: Fade in + Scale up.
 */
fun fadeInScaleUp(
    durationMillis: Int = ANIMATION_DURATION_MEDIUM
): EnterTransition = fadeIn(
    animationSpec = tween(durationMillis, easing = EmphasizedEasing)
) + scaleIn(
    animationSpec = tween(durationMillis, easing = EmphasizedEasing),
    initialScale = 0.8f,
    transformOrigin = TransformOrigin.Center
)

/**
 * Standard exit transition: Fade out + Scale down.
 */
fun fadeOutScaleDown(
    durationMillis: Int = ANIMATION_DURATION_MEDIUM
): ExitTransition = fadeOut(
    animationSpec = tween(durationMillis, easing = EmphasizedEasing)
) + scaleOut(
    animationSpec = tween(durationMillis, easing = EmphasizedEasing),
    targetScale = 0.8f,
    transformOrigin = TransformOrigin.Center
)

/**
 * Slide in from right transition.
 */
fun slideInFromRight(
    durationMillis: Int = ANIMATION_DURATION_MEDIUM
): EnterTransition = slideInHorizontally(
    animationSpec = tween(durationMillis, easing = EmphasizedDecelerateEasing),
    initialOffsetX = { it }
) + fadeIn(
    animationSpec = tween(durationMillis, easing = LinearEasing)
)

/**
 * Slide out to left transition.
 */
fun slideOutToLeft(
    durationMillis: Int = ANIMATION_DURATION_MEDIUM
): ExitTransition = slideOutHorizontally(
    animationSpec = tween(durationMillis, easing = EmphasizedAccelerateEasing),
    targetOffsetX = { -it }
) + fadeOut(
    animationSpec = tween(durationMillis, easing = LinearEasing)
)

/**
 * Slide in from left transition.
 */
fun slideInFromLeft(
    durationMillis: Int = ANIMATION_DURATION_MEDIUM
): EnterTransition = slideInHorizontally(
    animationSpec = tween(durationMillis, easing = EmphasizedDecelerateEasing),
    initialOffsetX = { -it }
) + fadeIn(
    animationSpec = tween(durationMillis, easing = LinearEasing)
)

/**
 * Slide out to right transition.
 */
fun slideOutToRight(
    durationMillis: Int = ANIMATION_DURATION_MEDIUM
): ExitTransition = slideOutHorizontally(
    animationSpec = tween(durationMillis, easing = EmphasizedAccelerateEasing),
    targetOffsetX = { it }
) + fadeOut(
    animationSpec = tween(durationMillis, easing = LinearEasing)
)
