package com.coparently.app.presentation.theme

import android.app.UiModeManager
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext

/**
 * The contrast level the system asks for, followed while it changes: the setting can be moved
 * with the app open, and does not recreate the activity.
 *
 * @return [ContrastLevel.STANDARD] before Android 14, otherwise the system's step
 */
@Composable
actual fun rememberSystemContrastLevel(): ContrastLevel =
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
