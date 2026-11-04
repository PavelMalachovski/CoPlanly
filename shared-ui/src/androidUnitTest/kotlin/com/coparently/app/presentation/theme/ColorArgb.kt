package com.coparently.app.presentation.theme

import androidx.compose.ui.graphics.Color
import kotlin.math.roundToInt

/**
 * An sRGB [Color] packed as an ARGB int, from its own components.
 *
 * Not `toArgb()`: that converts through Compose's colour-space connector, which pulls in
 * `androidx.collection` at run time, and these tests only ever hold sRGB colours, whose
 * components need no conversion at all.
 */
internal fun Color.argb(): Int =
    (alpha * CHANNEL_MAX).roundToInt() shl ALPHA_SHIFT or
        ((red * CHANNEL_MAX).roundToInt() shl RED_SHIFT) or
        ((green * CHANNEL_MAX).roundToInt() shl GREEN_SHIFT) or
        (blue * CHANNEL_MAX).roundToInt()

private const val CHANNEL_MAX = 255f
private const val ALPHA_SHIFT = 24
private const val RED_SHIFT = 16
private const val GREEN_SHIFT = 8
