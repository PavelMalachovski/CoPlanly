package com.coparently.app.presentation.common.animations

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Loading skeleton components with shimmer effect.
 * Provides placeholders for content that is loading.
 */

/**
 * Modifier that adds a shimmer effect to the composable.
 */
fun Modifier.shimmerEffect(): Modifier = composed {
    val transition = rememberInfiniteTransition(label = "shimmer")
    val translateAnimation by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1000f,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = 1000,
                easing = LinearEasing
            ),
            repeatMode = RepeatMode.Restart
        ),
        label = "shimmer_translate"
    )

    val shimmerColors = listOf(
        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f),
        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
    )

    background(
        brush = Brush.linearGradient(
            colors = shimmerColors,
            start = Offset(translateAnimation - 200f, translateAnimation - 200f),
            end = Offset(translateAnimation, translateAnimation)
        )
    )
}

/**
 * Simple box skeleton with shimmer effect.
 *
 * @param modifier Modifier to be applied
 * @param width Width of the skeleton box
 * @param height Height of the skeleton box
 * @param shape Shape of the skeleton box
 */
@Composable
fun SkeletonBox(
    modifier: Modifier = Modifier,
    width: Dp = 100.dp,
    height: Dp = 20.dp,
    shape: Shape = RoundedCornerShape(4.dp)
) {
    Box(
        modifier = modifier
            .width(width)
            .height(height)
            .clip(shape)
            .shimmerEffect()
    )
}

/**
 * Circle skeleton with shimmer effect (e.g., for avatars).
 *
 * @param modifier Modifier to be applied
 * @param size Size of the circle
 */
@Composable
fun SkeletonCircle(
    modifier: Modifier = Modifier,
    size: Dp = 40.dp
) {
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .shimmerEffect()
    )
}

/**
 * Skeleton for an event card (used in calendar/events list).
 */
@Composable
fun SkeletonEventCard(
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Icon placeholder
            SkeletonCircle(size = 48.dp)

            Spacer(modifier = Modifier.width(16.dp))

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Title placeholder
                SkeletonBox(
                    width = 150.dp,
                    height = 20.dp
                )

                // Description placeholder
                SkeletonBox(
                    width = 200.dp,
                    height = 16.dp
                )
            }
        }
    }
}

/**
 * Skeleton for a settings card.
 */
@Composable
fun SkeletonSettingsCard(
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            SkeletonCircle(size = 40.dp)

            Spacer(modifier = Modifier.width(16.dp))

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                SkeletonBox(width = 120.dp, height = 18.dp)
                SkeletonBox(width = 180.dp, height = 14.dp)
            }
        }
    }
}

/**
 * Skeleton for a list of items.
 *
 * @param count Number of skeleton items to display
 * @param itemContent Composable content for each skeleton item
 */
@Composable
fun SkeletonList(
    modifier: Modifier = Modifier,
    count: Int = 5,
    itemContent: @Composable (Int) -> Unit = { SkeletonEventCard() }
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        repeat(count) { index ->
            itemContent(index)
        }
    }
}

