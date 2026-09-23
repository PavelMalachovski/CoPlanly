package com.coparently.app.presentation.splash

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.coparently.app.R
import com.coparently.app.presentation.theme.CoPlanlyColors
import com.coparently.app.presentation.theme.Motion
import com.coparently.app.presentation.theme.rememberReducedMotion
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Branded startup splash: the brand indigo with the CoPlanly wordmark and a
 * small entrance animation (icon scales/settles in, wordmark fades up, a subtle
 * pulse on the icon). Purely cosmetic — shown briefly over the app on launch.
 *
 * The background is [CoPlanlyColors.BrandPrimary], flat, and not a gradient (UX-14). The system
 * splash before it (`windowSplashScreenBackground` = `@color/brand_primary`) and the launcher
 * icon's background are the same value, so icon → system splash → this screen → the app's own
 * primary read as one colour. It used to be a gradient from `#6750A4` (Material's baseline
 * purple, which the system splash also used) to `#4F46E5`, beside a `#6200EE` launcher: four
 * purples for one brand.
 *
 * @param onFinished Invoked once the splash animation has played, so the host can
 *   fade the splash out and reveal the app.
 */
@Composable
fun SplashScreen(
    onFinished: () -> Unit = {}
) {
    // Entrance animations
    val iconScale = remember { Animatable(0.6f) }
    val iconAlpha = remember { Animatable(0f) }
    val textAlpha = remember { Animatable(0f) }
    val textOffsetY = remember { Animatable(24f) }

    // Subtle continuous pulse on the icon after it settles
    val infinite = rememberInfiniteTransition(label = "splashPulse")
    val pulsing by infinite.animateFloat(
        initialValue = 1f,
        targetValue = 1.06f,
        animationSpec = infiniteRepeatable(
            animation = tween(900, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse"
    )
    val pulse = if (rememberReducedMotion()) 1f else pulsing

    val reducedMotion = rememberReducedMotion()
    LaunchedEffect(Unit) {
        if (reducedMotion) {
            // Animations are off: show the finished frame and get out of the way. The hold is a
            // `delay`, which the system animator scale does not shorten.
            iconAlpha.snapTo(1f)
            iconScale.snapTo(1f)
            textAlpha.snapTo(1f)
            textOffsetY.snapTo(0f)
            onFinished()
            return@LaunchedEffect
        }
        // The icon's fade and settle run together, then the wordmark's fade and rise together.
        // All four used to run one after another, with a 700 ms hold on top: about 2.25 s on
        // every cold start before the app appeared.
        coroutineScope {
            launch { iconAlpha.animateTo(1f, tween(Motion.MEDIUM_MS, easing = LinearOutSlowInEasing)) }
            launch { iconScale.animateTo(1f, tween(SPLASH_SETTLE_MS, easing = FastOutSlowInEasing)) }
        }
        coroutineScope {
            launch { textAlpha.animateTo(1f, tween(SPLASH_SETTLE_MS)) }
            launch { textOffsetY.animateTo(0f, tween(SPLASH_SETTLE_MS, easing = FastOutSlowInEasing)) }
        }
        delay(SPLASH_HOLD_MS)
        onFinished()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(CoPlanlyColors.BrandPrimary),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            // The actual app launcher icon, shown on a rounded brand badge so the
            // splash and the home-screen icon are one and the same.
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
                // The same white calendar as the launcher icon (its day cells knocked
                // out), so the splash badge and the home-screen icon share one shape
                // and the whole thing stays in the single brand-and-white palette.
                Image(
                    painter = painterResource(R.drawable.ic_calendar_splash),
                    contentDescription = null,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(18.dp)
                )
            }

            // Wordmark
            Text(
                text = stringResource(R.string.app_name),
                color = Color.White,
                fontSize = 34.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .graphicsLayer { translationY = textOffsetY.value }
                    .alpha(textAlpha.value)
            )

            // Tagline
            Text(
                text = stringResource(R.string.common_splash_tagline),
                color = Color.White.copy(alpha = 0.85f),
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.alpha(textAlpha.value)
            )
        }
    }
}

/** How long the icon takes to settle and the wordmark to rise. */
private const val SPLASH_SETTLE_MS = 400

/** How long the finished splash stays before the app shows. */
private const val SPLASH_HOLD_MS = 300L
