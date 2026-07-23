package com.coparently.app.presentation.splash

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Branded startup splash: a violet background with the CoPlanly wordmark and a
 * small entrance animation (icon scales/settles in, wordmark fades up, a subtle
 * pulse on the icon). Purely cosmetic — shown briefly over the app on launch.
 *
 * @param onFinished Invoked once the splash animation has played, so the host can
 *   fade the splash out and reveal the app.
 */
@Composable
fun SplashScreen(
    onFinished: () -> Unit = {}
) {
    // Violet brand gradient background
    val topColor = Color(0xFF6750A4) // brand primary (violet)
    val bottomColor = Color(0xFF4F46E5) // indigo accent

    // Entrance animations
    val iconScale = remember { Animatable(0.6f) }
    val iconAlpha = remember { Animatable(0f) }
    val textAlpha = remember { Animatable(0f) }
    val textOffsetY = remember { Animatable(24f) }

    // Subtle continuous pulse on the icon after it settles
    val infinite = rememberInfiniteTransition(label = "splashPulse")
    val pulse by infinite.animateFloat(
        initialValue = 1f,
        targetValue = 1.06f,
        animationSpec = infiniteRepeatable(
            animation = tween(900, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse"
    )

    LaunchedEffect(Unit) {
        // Icon pops in
        iconAlpha.animateTo(1f, tween(300, easing = LinearOutSlowInEasing))
        iconScale.animateTo(1f, tween(450, easing = FastOutSlowInEasing))
        // Wordmark fades up shortly after
        textAlpha.animateTo(1f, tween(400))
        textOffsetY.animateTo(0f, tween(400, easing = FastOutSlowInEasing))
        // Hold briefly, then let the host dismiss the splash
        kotlinx.coroutines.delay(700)
        onFinished()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(colors = listOf(topColor, bottomColor))
            ),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            // Rounded app-icon badge with a calendar glyph
            Box(
                modifier = Modifier
                    .size(96.dp)
                    .graphicsLayer {
                        val s = iconScale.value * pulse
                        scaleX = s
                        scaleY = s
                        alpha = iconAlpha.value
                    }
                    .clip(RoundedCornerShape(28.dp))
                    .background(Color.White.copy(alpha = 0.16f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    painter = painterResource(com.coparently.app.R.drawable.ic_launcher_monochrome),
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(56.dp)
                )
            }

            // Wordmark
            Text(
                text = "CoPlanly",
                color = Color.White,
                fontSize = 34.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .graphicsLayer { translationY = textOffsetY.value }
                    .alpha(textAlpha.value)
            )

            // Tagline
            Text(
                text = "Shared calendar for co-parents",
                color = Color.White.copy(alpha = 0.85f),
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.alpha(textAlpha.value)
            )
        }
    }
}
