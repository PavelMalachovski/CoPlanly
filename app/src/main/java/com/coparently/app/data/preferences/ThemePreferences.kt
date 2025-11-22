package com.coparently.app.data.preferences

import androidx.compose.ui.graphics.Color

/**
 * Theme preferences for the CoParently app.
 * Supports dark mode, dynamic colors, custom color schemes, and font scaling.
 */
data class ThemePreferences(
    val isDarkMode: Boolean = false,
    val useDynamicColors: Boolean = false,
    val customColors: CustomColorScheme? = null,
    val fontScale: Float = 1.0f
)

/**
 * Custom color scheme for personalization.
 * Allows users to customize primary, secondary, and tertiary colors,
 * as well as parent-specific colors (mom and dad).
 */
data class CustomColorScheme(
    val primary: Color,
    val secondary: Color,
    val tertiary: Color,
    val momColor: Color = Color(0xFFE91E63),
    val dadColor: Color = Color(0xFF1976D2)
)
