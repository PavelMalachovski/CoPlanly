package com.coparently.app.presentation.theme

import android.app.Activity
import android.content.Context
import android.view.accessibility.AccessibilityManager
import androidx.compose.material3.windowsizeclass.ExperimentalMaterial3WindowSizeClassApi
import androidx.compose.material3.windowsizeclass.calculateWindowSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext

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
 * @return Dimensions object with adaptive values
 */
@OptIn(ExperimentalMaterial3WindowSizeClassApi::class)
@Composable
fun adaptiveDimensions(): Dimensions {
    val context = LocalContext.current
    val activity = context as? Activity

    val windowSizeClass = if (activity != null) {
        calculateWindowSizeClass(activity)
    } else {
        null
    }

    val accessibilityManager = context.getSystemService(Context.ACCESSIBILITY_SERVICE) as AccessibilityManager
    val isTouchExplorationEnabled = accessibilityManager.isTouchExplorationEnabled
    val fontScale = LocalConfiguration.current.fontScale

    // Get base dimensions from window size
    val baseDimensions = windowSizeClass?.getDimensions() ?: compactDimensions

    return remember(windowSizeClass, isTouchExplorationEnabled, fontScale) {
        Dimensions(
            // Use existing padding values from WindowSize.kt
            paddingSmall = baseDimensions.paddingSmall,
            paddingMedium = baseDimensions.paddingMedium,
            paddingLarge = baseDimensions.paddingLarge,

            // Scale icons with font scale, but limit the range to prevent extreme sizes
            iconSize = baseDimensions.iconSize * fontScale.coerceIn(0.8f, 1.5f),

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
    }
}

/**
 * Minimum touch target size for accessibility.
 * WCAG 2.1 AA requires 44x44dp minimum, we use 48dp for better usability.
 */
const val MIN_TOUCH_TARGET_DP = 48

/**
 * Minimum touch target size when TalkBack is enabled.
 * Larger targets improve usability for screen reader users.
 */
const val MIN_TOUCH_TARGET_ACCESSIBILITY_DP = 56
