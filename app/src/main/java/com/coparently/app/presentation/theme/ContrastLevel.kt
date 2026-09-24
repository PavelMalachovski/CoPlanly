package com.coparently.app.presentation.theme

import android.app.UiModeManager
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.material3.ColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext

/**
 * How much contrast the person asked the system for: Material's three steps.
 *
 * Android 14 added the choice (Settings → Accessibility → Colour and motion → Contrast) and
 * reports it as a number between -1 and 1, where 0 is the standard contrast, 0.5 medium and 1
 * high. Before Android 14 there is nothing to read and every device is [STANDARD].
 */
enum class ContrastLevel {
    /** The scheme as designed. */
    STANDARD,

    /** Foregrounds at Material's medium-contrast targets: body text at 11:1, secondary text at 7:1. */
    MEDIUM,

    /** Foregrounds at Material's high-contrast targets: text at its extreme, outlines at 7:1. */
    HIGH;

    companion object {
        /** The step a system contrast value falls in; a reduced contrast (below 0) is [STANDARD]. */
        fun of(systemContrast: Float): ContrastLevel = when {
            systemContrast >= HIGH_FROM -> HIGH
            systemContrast >= MEDIUM_FROM -> MEDIUM
            else -> STANDARD
        }

        /** Halfway between the settings' medium (0.5) and high (1.0) values. */
        private const val HIGH_FROM = 0.75f

        /** Halfway between the settings' standard (0) and medium (0.5) values. */
        private const val MEDIUM_FROM = 0.25f
    }
}

/**
 * The colour scheme for a theme and a contrast level (docs/AUDIT-2026-10-design.md D-25).
 *
 * The medium and high schemes are generated from the standard ones by
 * `tools/generate-contrast-schemes.py`: every foreground moves along its own lightness to
 * Material's target for the level, and every background stays as it is, so any pairing a screen
 * makes can only gain contrast. `ContrastSchemesTest` holds the targets.
 *
 * @param darkTheme Whether the dark scheme is wanted
 * @param contrast The contrast level
 * @return The scheme to draw with
 */
internal fun colorSchemeFor(darkTheme: Boolean, contrast: ContrastLevel): ColorScheme = when (contrast) {
    ContrastLevel.STANDARD -> if (darkTheme) DarkColorScheme else LightColorScheme
    ContrastLevel.MEDIUM -> if (darkTheme) DarkMediumContrastColorScheme else LightMediumContrastColorScheme
    ContrastLevel.HIGH -> if (darkTheme) DarkHighContrastColorScheme else LightHighContrastColorScheme
}

/**
 * The contrast level the system asks for, followed while it changes: the setting can be moved
 * with the app open, and does not recreate the activity.
 *
 * @return [ContrastLevel.STANDARD] before Android 14, otherwise the system's step
 */
@Composable
fun rememberSystemContrastLevel(): ContrastLevel =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
        ContrastLevel.of(rememberSystemContrast())
    } else {
        ContrastLevel.STANDARD
    }

/**
 * The system's contrast value, and a listener that keeps it current.
 *
 * Read defensively: a device (or a test environment) without the service answers standard
 * contrast rather than failing the whole theme.
 */
@RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
@Composable
private fun rememberSystemContrast(): Float {
    val context = LocalContext.current
    val uiModeManager = remember(context) { context.getSystemService(UiModeManager::class.java) }
    var contrast by remember(uiModeManager) {
        mutableFloatStateOf(runCatching { uiModeManager?.contrast }.getOrNull() ?: 0f)
    }
    DisposableEffect(uiModeManager) {
        val listener = UiModeManager.ContrastChangeListener { contrast = it }
        val registered = uiModeManager != null &&
            runCatching { uiModeManager.addContrastChangeListener(context.mainExecutor, listener) }.isSuccess
        onDispose {
            if (registered) runCatching { uiModeManager?.removeContrastChangeListener(listener) }
        }
    }
    return contrast
}
