package com.coparently.app.presentation.theme

import androidx.compose.runtime.Composable

/**
 * Calculates adaptive dimensions based on:
 * - Window size class (compact, medium, expanded)
 * - Accessibility settings (TalkBack, touch exploration)
 * - Font scale preference
 *
 * This ensures the app is accessible and comfortable to use across different
 * devices and user preferences.
 *
 * This function extends the existing Dimensions from WindowSize.kt with
 * accessibility-aware calculations.
 *
 * Each platform measures for itself (`AdaptiveDimensions.android.kt`: the Activity's window,
 * TalkBack's touch exploration and the configuration's font scale; `AdaptiveDimensions.ios.kt`:
 * the window, VoiceOver and the density's font scale), and both scale through [adaptedDimensions].
 *
 * @return Dimensions object with adaptive values
 */
@Composable
expect fun adaptiveDimensions(): Dimensions

/**
 * [baseDimensions], the ones for the window's size class, adapted to the reader: what
 * [adaptiveDimensions] returns on either platform.
 *
 * @param baseDimensions The dimensions for the window's size class
 * @param fontScale The reader's font scale
 * @param isTouchExplorationEnabled Whether a screen reader explores by touch (TalkBack, VoiceOver)
 */
internal fun adaptedDimensions(
    baseDimensions: Dimensions,
    fontScale: Float,
    isTouchExplorationEnabled: Boolean
): Dimensions = Dimensions(
    // Use existing padding values from WindowSize.kt
    paddingSmall = baseDimensions.paddingSmall,
    paddingMedium = baseDimensions.paddingMedium,
    paddingLarge = baseDimensions.paddingLarge,

    // The hour gutter holds a number, so it grows with the text — within a range, so the
    // seven day columns keep most of the width at the largest font scales.
    hourGutterWidth = baseDimensions.hourGutterWidth * fontScale.coerceIn(0.8f, 1.5f),

    // Scale button height with font scale and increase for accessibility
    // When TalkBack is enabled, use even larger targets for better usability
    buttonHeight = if (isTouchExplorationEnabled) {
        (baseDimensions.buttonHeight * 1.1f) * fontScale.coerceIn(0.9f, 1.3f)
    } else {
        baseDimensions.buttonHeight * fontScale.coerceIn(0.9f, 1.3f)
    },

    // Use existing elevation and corner radius
    cardElevation = baseDimensions.cardElevation,
    cornerRadius = baseDimensions.cornerRadius
)
