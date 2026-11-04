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
 * The Activity's window size class, TalkBack's touch exploration and the configuration's font
 * scale. Outside an Activity (a Compose preview) there is no window to measure, so it is the
 * compact dimensions.
 *
 * @return Dimensions object with adaptive values
 */
@OptIn(ExperimentalMaterial3WindowSizeClassApi::class)
@Composable
actual fun adaptiveDimensions(): Dimensions {
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
        adaptedDimensions(baseDimensions, fontScale, isTouchExplorationEnabled)
    }
}
