package com.coparently.app.presentation.theme

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.Color
import com.coparently.app.data.preferences.ThemePreferences

/**
 * Animated theme wrapper that provides smooth transitions when theme changes.
 * Uses color animation to create a pleasant visual effect when switching themes.
 *
 * @param themePrefs Theme preferences containing all customization options
 * @param content The composable content to display with this theme
 */
@Composable
fun AnimatedTheme(
    themePrefs: ThemePreferences,
    content: @Composable () -> Unit
) {
    // Use the dynamic theme as base
    CoPlanlyDynamicTheme(themePrefs = themePrefs) {
        // Get the current color scheme from MaterialTheme
        val colorScheme = MaterialTheme.colorScheme

        // Animate all color transitions
        val animatedColorScheme = animateColorScheme(colorScheme)

        // Apply the animated color scheme
        MaterialTheme(
            colorScheme = animatedColorScheme,
            typography = MaterialTheme.typography,
            content = content
        )
    }
}

/**
 * Animates all colors in a ColorScheme for smooth theme transitions.
 *
 * @param targetScheme The target color scheme to animate to
 * @return Animated color scheme
 */
@Composable
private fun animateColorScheme(targetScheme: ColorScheme): ColorScheme {
    val animationSpec = tween<Color>(durationMillis = 500)

    val primary by animateColorAsState(
        targetValue = targetScheme.primary,
        animationSpec = animationSpec,
        label = "primary"
    )
    val onPrimary by animateColorAsState(
        targetValue = targetScheme.onPrimary,
        animationSpec = animationSpec,
        label = "onPrimary"
    )
    val primaryContainer by animateColorAsState(
        targetValue = targetScheme.primaryContainer,
        animationSpec = animationSpec,
        label = "primaryContainer"
    )
    val onPrimaryContainer by animateColorAsState(
        targetValue = targetScheme.onPrimaryContainer,
        animationSpec = animationSpec,
        label = "onPrimaryContainer"
    )
    val secondary by animateColorAsState(
        targetValue = targetScheme.secondary,
        animationSpec = animationSpec,
        label = "secondary"
    )
    val onSecondary by animateColorAsState(
        targetValue = targetScheme.onSecondary,
        animationSpec = animationSpec,
        label = "onSecondary"
    )
    val secondaryContainer by animateColorAsState(
        targetValue = targetScheme.secondaryContainer,
        animationSpec = animationSpec,
        label = "secondaryContainer"
    )
    val onSecondaryContainer by animateColorAsState(
        targetValue = targetScheme.onSecondaryContainer,
        animationSpec = animationSpec,
        label = "onSecondaryContainer"
    )
    val tertiary by animateColorAsState(
        targetValue = targetScheme.tertiary,
        animationSpec = animationSpec,
        label = "tertiary"
    )
    val onTertiary by animateColorAsState(
        targetValue = targetScheme.onTertiary,
        animationSpec = animationSpec,
        label = "onTertiary"
    )
    val tertiaryContainer by animateColorAsState(
        targetValue = targetScheme.tertiaryContainer,
        animationSpec = animationSpec,
        label = "tertiaryContainer"
    )
    val onTertiaryContainer by animateColorAsState(
        targetValue = targetScheme.onTertiaryContainer,
        animationSpec = animationSpec,
        label = "onTertiaryContainer"
    )
    val background by animateColorAsState(
        targetValue = targetScheme.background,
        animationSpec = animationSpec,
        label = "background"
    )
    val onBackground by animateColorAsState(
        targetValue = targetScheme.onBackground,
        animationSpec = animationSpec,
        label = "onBackground"
    )
    val surface by animateColorAsState(
        targetValue = targetScheme.surface,
        animationSpec = animationSpec,
        label = "surface"
    )
    val onSurface by animateColorAsState(
        targetValue = targetScheme.onSurface,
        animationSpec = animationSpec,
        label = "onSurface"
    )
    val surfaceVariant by animateColorAsState(
        targetValue = targetScheme.surfaceVariant,
        animationSpec = animationSpec,
        label = "surfaceVariant"
    )
    val onSurfaceVariant by animateColorAsState(
        targetValue = targetScheme.onSurfaceVariant,
        animationSpec = animationSpec,
        label = "onSurfaceVariant"
    )
    val error by animateColorAsState(
        targetValue = targetScheme.error,
        animationSpec = animationSpec,
        label = "error"
    )
    val onError by animateColorAsState(
        targetValue = targetScheme.onError,
        animationSpec = animationSpec,
        label = "onError"
    )
    val errorContainer by animateColorAsState(
        targetValue = targetScheme.errorContainer,
        animationSpec = animationSpec,
        label = "errorContainer"
    )
    val onErrorContainer by animateColorAsState(
        targetValue = targetScheme.onErrorContainer,
        animationSpec = animationSpec,
        label = "onErrorContainer"
    )

    return ColorScheme(
        primary = primary,
        onPrimary = onPrimary,
        primaryContainer = primaryContainer,
        onPrimaryContainer = onPrimaryContainer,
        inversePrimary = targetScheme.inversePrimary,
        secondary = secondary,
        onSecondary = onSecondary,
        secondaryContainer = secondaryContainer,
        onSecondaryContainer = onSecondaryContainer,
        tertiary = tertiary,
        onTertiary = onTertiary,
        tertiaryContainer = tertiaryContainer,
        onTertiaryContainer = onTertiaryContainer,
        background = background,
        onBackground = onBackground,
        surface = surface,
        onSurface = onSurface,
        surfaceVariant = surfaceVariant,
        onSurfaceVariant = onSurfaceVariant,
        surfaceTint = targetScheme.surfaceTint,
        inverseSurface = targetScheme.inverseSurface,
        inverseOnSurface = targetScheme.inverseOnSurface,
        error = error,
        onError = onError,
        errorContainer = errorContainer,
        onErrorContainer = onErrorContainer,
        outline = targetScheme.outline,
        outlineVariant = targetScheme.outlineVariant,
        scrim = targetScheme.scrim,
        surfaceBright = targetScheme.surfaceBright,
        surfaceDim = targetScheme.surfaceDim,
        surfaceContainer = targetScheme.surfaceContainer,
        surfaceContainerHigh = targetScheme.surfaceContainerHigh,
        surfaceContainerHighest = targetScheme.surfaceContainerHighest,
        surfaceContainerLow = targetScheme.surfaceContainerLow,
        surfaceContainerLowest = targetScheme.surfaceContainerLowest
    )
}
