package com.coparently.app.presentation.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import platform.Foundation.NSNotificationCenter
import platform.UIKit.UIAccessibilityDarkerSystemColorsEnabled
import platform.UIKit.UIAccessibilityDarkerSystemColorsStatusDidChangeNotification

/**
 * The contrast level iOS's one switch asks for: Settings → Accessibility → Display & Text Size →
 * Increase Contrast is [ContrastLevel.MEDIUM] (owner, 16 November 2026), never [ContrastLevel.HIGH],
 * and off is [ContrastLevel.STANDARD]. The parent colours, the weekend grey and the holiday reds do
 * not move with it, as on Android (design item 17).
 *
 * @param darkerSystemColors Whether Increase Contrast is on (`UIAccessibilityDarkerSystemColorsEnabled`)
 * @return The level the app draws at
 */
internal fun contrastLevelFor(darkerSystemColors: Boolean): ContrastLevel =
    if (darkerSystemColors) ContrastLevel.MEDIUM else ContrastLevel.STANDARD

/** The level Increase Contrast asks for now ([contrastLevelFor]). */
internal fun currentIosContrastLevel(): ContrastLevel = contrastLevelFor(UIAccessibilityDarkerSystemColorsEnabled())

/**
 * The level Increase Contrast asks for, followed while it changes: the current level first, then the
 * level read again each time `UIAccessibilityDarkerSystemColorsStatusDidChangeNotification` is posted,
 * so the switch can be moved with the app open. A post re-reads the level and emits it even when it
 * did not change; the observer is removed when the collection ends.
 */
internal val iosContrastLevelChanges: Flow<ContrastLevel> = callbackFlow {
    val center = NSNotificationCenter.defaultCenter
    // No queue: the block runs on the posting thread, and `trySend` may be called from any.
    val observer = center.addObserverForName(
        name = UIAccessibilityDarkerSystemColorsStatusDidChangeNotification,
        `object` = null,
        queue = null
    ) { _ -> trySend(currentIosContrastLevel()) }
    trySend(currentIosContrastLevel())
    awaitClose { center.removeObserver(observer) }
}

/**
 * The contrast level Increase Contrast asks for, followed while it changes ([iosContrastLevelChanges]).
 *
 * @return [ContrastLevel.MEDIUM] while Increase Contrast is on, else [ContrastLevel.STANDARD]
 */
@Composable
actual fun rememberSystemContrastLevel(): ContrastLevel =
    iosContrastLevelChanges.collectAsState(initial = currentIosContrastLevel()).value
