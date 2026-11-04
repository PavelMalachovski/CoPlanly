package com.coparently.app.presentation.theme

import androidx.compose.runtime.Composable

/**
 * Nothing yet: on the iPhone the status bar's style belongs to the view controller that hosts the
 * Compose view, which the iPhone app creates in phase 3 (docs/superpowers/plans/
 * 2026-11-01-iphone-phase-2-multiplatform-app.md).
 *
 * @param darkTheme Whether the app is drawing its dark theme
 */
@Composable
internal actual fun SystemBarsAppearance(darkTheme: Boolean) {
    // The hosting view controller decides the status bar's style (phase 3).
}
