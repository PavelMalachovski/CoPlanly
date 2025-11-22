package com.coparently.app.presentation.common.animations

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.TransformOrigin

/**
 * Utility file for common animations in the app.
 * Provides reusable animation specifications following Material Design guidelines.
 */

/**
 * Standard duration for short animations (150ms).
 */
/**
 * Standard duration for short animations (150ms).
 */
const val ANIMATION_DURATION_SHORT = com.coparently.app.utils.AnimationConstants.FAST

/**
 * Standard duration for medium animations (300ms).
 */
const val ANIMATION_DURATION_MEDIUM = com.coparently.app.utils.AnimationConstants.NORMAL

/**
 * Standard duration for long animations (500ms).
 */
const val ANIMATION_DURATION_LONG = com.coparently.app.utils.AnimationConstants.SLOW

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
 * Creates a spring animation spec with standard dampening.
 */
fun <T> standardSpring(
    dampingRatio: Float = Spring.DampingRatioMediumBouncy,
    stiffness: Float = Spring.StiffnessLow
): SpringSpec<T> = spring(
    dampingRatio = dampingRatio,
    stiffness = stiffness
)

/**
 * Creates a tween animation spec with emphasized easing.
 */
fun <T> emphasizedTween(
    durationMillis: Int = ANIMATION_DURATION_MEDIUM
): TweenSpec<T> = tween(
    durationMillis = durationMillis,
    easing = EmphasizedEasing
)

/**
 * Standard enter transition: Fade in + Slide up.
 */
fun fadeInSlideUp(
    durationMillis: Int = ANIMATION_DURATION_MEDIUM
): EnterTransition = fadeIn(
    animationSpec = tween(durationMillis, easing = EmphasizedEasing)
) + slideInVertically(
    animationSpec = tween(durationMillis, easing = EmphasizedEasing),
    initialOffsetY = { it / 4 }
)

/**
 * Standard exit transition: Fade out + Slide down.
 */
fun fadeOutSlideDown(
    durationMillis: Int = ANIMATION_DURATION_MEDIUM
): ExitTransition = fadeOut(
    animationSpec = tween(durationMillis, easing = EmphasizedEasing)
) + slideOutVertically(
    animationSpec = tween(durationMillis, easing = EmphasizedEasing),
    targetOffsetY = { it / 4 }
)

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

/**
 * Composable modifier for animated visibility with fade.
 */
@Composable
fun Modifier.animatedFade(
    visible: Boolean,
    durationMillis: Int = ANIMATION_DURATION_MEDIUM
): Modifier {
    val alphaState = animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = tween(durationMillis, easing = EmphasizedEasing),
        label = "fade"
    )
    return this.alpha(alphaState.value)
}

/**
 * Returns an infinite transition for shimmer/loading effects.
 */
@Composable
fun rememberInfiniteShimmerTransition(): InfiniteTransition {
    return rememberInfiniteTransition(label = "shimmer")
}

/**
 * Creates a shimmer animation value (0f to 1f).
 */
@Composable
fun rememberShimmerAnimation(): androidx.compose.runtime.State<Float> {
    val infiniteTransition = rememberInfiniteShimmerTransition()
    return infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = 1000,
                easing = LinearEasing
            ),
            repeatMode = RepeatMode.Restart
        ),
        label = "shimmer_value"
    )
}

