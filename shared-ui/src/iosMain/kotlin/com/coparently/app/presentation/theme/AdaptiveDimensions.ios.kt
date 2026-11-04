@file:OptIn(ExperimentalForeignApi::class)

package com.coparently.app.presentation.theme

import androidx.compose.material3.windowsizeclass.ExperimentalMaterial3WindowSizeClassApi
import androidx.compose.material3.windowsizeclass.calculateWindowSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalDensity
import kotlinx.cinterop.ExperimentalForeignApi
import platform.UIKit.UIAccessibilityIsVoiceOverRunning

/**
 * The window's size class, VoiceOver (read once, as TalkBack's touch exploration is on Android)
 * and the density's font scale, which follows the system's text size.
 *
 * @return Dimensions object with adaptive values
 */
@OptIn(ExperimentalMaterial3WindowSizeClassApi::class)
@Composable
actual fun adaptiveDimensions(): Dimensions {
    val windowSizeClass = calculateWindowSizeClass()
    val voiceOver = remember { UIAccessibilityIsVoiceOverRunning() }
    val fontScale = LocalDensity.current.fontScale
    return remember(windowSizeClass, voiceOver, fontScale) {
        adaptedDimensions(windowSizeClass.getDimensions(), fontScale, voiceOver)
    }
}
