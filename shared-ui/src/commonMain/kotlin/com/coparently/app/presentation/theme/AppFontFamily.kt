package com.coparently.app.presentation.theme

import androidx.compose.runtime.Composable
import androidx.compose.ui.text.font.FontFamily

/**
 * The family the app's text is set in: Onest on Android ([onestFontFamily]), the system's (SF Pro)
 * on the iPhone (MON-24 phase 3, part D). The wordmark is Onest on both (`wordmarkStyle`).
 *
 * @return The body family of the platform
 */
@Composable
internal expect fun appFontFamily(): FontFamily
