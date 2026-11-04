package com.coparently.app.presentation.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.runtime.Composable

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
 * On Android (`ContrastLevel.android.kt`): [ContrastLevel.STANDARD] before Android 14, otherwise
 * the system's step. On the iPhone [ContrastLevel.STANDARD] for now: iOS has one "Increase
 * Contrast" switch rather than Material's three steps, and which step it means is decided with the
 * iPhone app (phase 3).
 *
 * @return The system's contrast level
 */
@Composable
expect fun rememberSystemContrastLevel(): ContrastLevel
