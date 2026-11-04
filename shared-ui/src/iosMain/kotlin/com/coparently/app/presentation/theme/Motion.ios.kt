@file:OptIn(ExperimentalForeignApi::class)

package com.coparently.app.presentation.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import kotlinx.cinterop.ExperimentalForeignApi
import platform.UIKit.UIAccessibilityIsReduceMotionEnabled

/**
 * True when Settings → Accessibility → Motion → Reduce Motion is on, read once per composition as
 * Android's animator scale is.
 *
 * @return Whether the person asked for reduced motion
 */
@Composable
actual fun rememberReducedMotion(): Boolean = remember { UIAccessibilityIsReduceMotionEnabled() }
