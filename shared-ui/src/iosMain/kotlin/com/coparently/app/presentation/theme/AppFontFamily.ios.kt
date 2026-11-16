package com.coparently.app.presentation.theme

import androidx.compose.runtime.Composable
import androidx.compose.ui.text.font.FontFamily

/**
 * The iPhone's body family: the system's, SF Pro (MON-24 phase 3, part D).
 *
 * @return [iosBodyFontFamily]
 */
@Composable
internal actual fun appFontFamily(): FontFamily = iosBodyFontFamily()

/**
 * [FontFamily.Default], which Compose Multiplatform draws in the system font on the iPhone. Apart
 * from [appFontFamily] so a test can read it without composing.
 */
internal fun iosBodyFontFamily(): FontFamily = FontFamily.Default
