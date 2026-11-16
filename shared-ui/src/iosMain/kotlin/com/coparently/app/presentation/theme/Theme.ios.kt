package com.coparently.app.presentation.theme

import androidx.compose.runtime.Composable

/**
 * Nothing to do on the iPhone: the window's interface style decides the status bar's style (MON-24
 * phase 3, part D). The shell sets that style from the app's theme choice (`WindowAppearance`), and
 * the hosting controllers keep UIKit's default `preferredStatusBarStyle`, which follows it.
 *
 * @param darkTheme Whether the app is drawing its dark theme
 */
@Composable
internal actual fun SystemBarsAppearance(darkTheme: Boolean) {
    // The window's interface style decides the status bar's style (`WindowAppearance`).
}
