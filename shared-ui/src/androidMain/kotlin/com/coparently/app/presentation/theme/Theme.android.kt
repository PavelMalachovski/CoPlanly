package com.coparently.app.presentation.theme

import android.app.Activity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

/**
 * Android's system bars, configured through the EdgeToEdge API: transparent bars, and icons light
 * or dark by the app's own theme. This is the modern approach recommended by Google (it replaced
 * Accompanist), and it was `CoPlanlyTheme`'s own code until the theme moved to `:shared-ui`
 * (MON-24 phase 2, Task 9).
 *
 * @param darkTheme Whether the app is drawing its dark theme
 */
@Composable
internal actual fun SystemBarsAppearance(darkTheme: Boolean) {
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window

            // Set transparent status and navigation bars for edge-to-edge experience.
            // Deprecated at API 35, where they are no-ops; on 26–34 they still override the
            // scrim `enableEdgeToEdge()` picks from the *system* theme, which would disagree
            // with an app-level dark/light choice. No replacement covers minSdk 26 without
            // that change, so the calls stay until the scrim is decided as a design question.
            @Suppress("DEPRECATION")
            window.statusBarColor = Color.Transparent.toArgb()
            @Suppress("DEPRECATION")
            window.navigationBarColor = Color.Transparent.toArgb()

            // Configure system bar icons based on theme
            val windowInsetsController = WindowCompat.getInsetsController(window, view)
            windowInsetsController.isAppearanceLightStatusBars = !darkTheme
            windowInsetsController.isAppearanceLightNavigationBars = !darkTheme

            // Note: For full edge-to-edge experience, ensure:
            // 1. enableEdgeToEdge() is called in MainActivity.onCreate()
            // 2. Scaffold with appropriate padding for system bars
        }
    }
}
