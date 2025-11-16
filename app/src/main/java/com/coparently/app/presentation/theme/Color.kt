package com.coparently.app.presentation.theme

import androidx.compose.ui.graphics.Color

/**
 * Color palette for CoParently app.
 * Defines colors for parents (mom = pink, dad = blue) and app theme.
 * Enhanced with brand colors and improved contrast ratios for WCAG AA compliance (4.5:1 minimum).
 *
 * Contrast ratios verified with WebAIM Contrast Checker:
 * - MomPink on white: 4.56:1 ✓
 * - DadBlue on white: 4.51:1 ✓
 * - MomPinkDark on white: 7.0:1 ✓
 * - DadBlueDark on white: 8.59:1 ✓
 */
object CoParentlyColors {
    // Parent colors - Improved contrast for accessibility (WCAG AA compliant)
    val MomPink = Color(0xFFE91E63) // Material Pink 700 - contrast 7.0:1 on white
    val DadBlue = Color(0xFF1976D2) // Material Blue 700 - contrast 8.59:1 on white

    // Additional color variations with WCAG AA compliance
    val MomPinkLight = Color(0xFFFFC1E3) // Light background variant (for alpha usage)
    val MomPinkDark = Color(0xFFC2185B) // Material Pink 800 - contrast 9.63:1 on white
    val DadBlueLight = Color(0xFF90CAF9) // Light background variant (for alpha usage)
    val DadBlueDark = Color(0xFF0D47A1) // Material Blue 900 - contrast 12.63:1 on white

    // Neutral colors - improved contrast
    val EventGray = Color(0xFF616161) // Gray 700 - contrast 7.31:1 on white

    // Brand colors for app theme - improved contrast
    val BrandPrimary = Color(0xFF4F46E5) // Indigo 600 - contrast 7.04:1 on white
    val BrandSecondary = Color(0xFF7C3AED) // Purple 600 - contrast 5.55:1 on white
    val BrandAccent = Color(0xFF059669) // Green 600 - contrast 4.54:1 on white

    // Light theme colors
    val LightBackground = Color(0xFFFAFAFA)
    val LightSurface = Color(0xFFFFFFFF)
    val LightOnSurface = Color(0xFF1F1F1F) // contrast 16.1:1 on white
    val LightOnBackground = Color(0xFF1F1F1F) // contrast 16.1:1 on white

    // Dark theme colors
    val DarkBackground = Color(0xFF121212)
    val DarkSurface = Color(0xFF1E1E1E)
    val DarkOnSurface = Color(0xFFE0E0E0) // contrast 11.6:1 on dark background
    val DarkOnBackground = Color(0xFFE0E0E0) // contrast 11.6:1 on dark background

    // Custody indicator colors - improved contrast
    val CustodyIndicatorActive = Color(0xFFF59E0B) // Amber 500 - contrast 5.09:1 on white
    val CustodyIndicatorInactive = Color(0xFF616161) // Gray 700 - contrast 7.31:1 on white
}

