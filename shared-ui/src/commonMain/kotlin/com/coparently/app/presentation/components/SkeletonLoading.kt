package com.coparently.app.presentation.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.SolidColor
import com.coparently.app.presentation.theme.rememberReducedMotion

/**
 * Builds the animated gradient behind the shimmer effect.
 *
 * With animations switched off the placeholder is a flat tint instead: a shimmer is decoration
 * that loops for as long as a screen waits, which is exactly what `rememberReducedMotion` exists
 * to stop (docs/AUDIT-2026-10-design.md D-25). It kept sweeping for anyone who had turned
 * animations off, while the splash and the sign-in logo already stood still.
 *
 * @return A brush carrying an animated horizontal gradient, or a still tint
 */
@Composable
private fun shimmerBrush(): Brush {
    val shimmerColors = listOf(
        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f),
        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
    )
    if (rememberReducedMotion()) {
        return SolidColor(shimmerColors.first())
    }

    val transition = rememberInfiniteTransition(label = "shimmer")
    val translateAnimation by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1000f,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = 1200,
                // Constant speed: an eased sweep visibly speeds up and slows down.
                easing = LinearEasing
            ),
            repeatMode = RepeatMode.Restart
        ),
        label = "shimmer_translate"
    )

    return Brush.linearGradient(
        colors = shimmerColors,
        start = Offset(translateAnimation, translateAnimation),
        end = Offset(translateAnimation + 200f, translateAnimation + 200f)
    )
}

/**
 * A shimmering placeholder of arbitrary size — the one loading skeleton the app uses (Home and
 * `ListSkeleton`). The pre-built event/calendar skeletons that sat beside it had no callers and
 * were removed in the September 2026 audit.
 *
 * @param modifier Sets the size and shape of the placeholder
 */
@Composable
fun SkeletonBox(
    modifier: Modifier = Modifier
) {
    val shimmer = shimmerBrush()

    Box(
        modifier = modifier
            .background(shimmer)
    )
}
