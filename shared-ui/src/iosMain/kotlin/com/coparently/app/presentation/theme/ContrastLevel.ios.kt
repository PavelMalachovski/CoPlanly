package com.coparently.app.presentation.theme

import androidx.compose.runtime.Composable

/**
 * [ContrastLevel.STANDARD], until the iPhone app (phase 3) decides which of Material's steps iOS's
 * one "Increase Contrast" switch stands for.
 *
 * @return [ContrastLevel.STANDARD]
 */
@Composable
actual fun rememberSystemContrastLevel(): ContrastLevel = ContrastLevel.STANDARD
