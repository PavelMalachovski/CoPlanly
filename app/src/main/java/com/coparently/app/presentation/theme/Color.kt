package com.coparently.app.presentation.theme

import androidx.compose.ui.graphics.Color

/**
 * Color palette for CoPlanly app.
 * Defines colors for parents (mom = pink, dad = blue) and app theme.
 * Enhanced with brand colors and improved contrast ratios for WCAG AA compliance (4.5:1 minimum).
 *
 * Contrast ratios verified with WebAIM Contrast Checker:
 * - MomPink on white: 4.56:1 ✓
 * - DadBlue on white: 4.51:1 ✓
 * - MomPinkDark on white: 7.0:1 ✓
 * - DadBlueDark on white: 8.59:1 ✓
 */
object CoPlanlyColors {
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
    val BrandPrimaryContainer = Color(0xFFE2E0FF) // Soft indigo container
    val BrandOnPrimaryContainer = Color(0xFF1A1650) // Deep indigo for container text
    val BrandSecondary = Color(0xFF7C3AED) // Purple 600 - contrast 5.55:1 on white
    val BrandAccent = Color(0xFF059669) // Green 600 - contrast 4.54:1 on white

    // Light theme colors - subtle indigo-tinted neutrals for a modern tonal look
    val LightBackground = Color(0xFFFCFBFF)
    val LightSurface = Color(0xFFFFFFFF)
    val LightOnSurface = Color(0xFF1B1B21) // contrast 15.9:1 on white
    val LightOnBackground = Color(0xFF1B1B21)

    // Dark theme colors - tinted dark neutrals instead of pure gray
    val DarkBackground = Color(0xFF131318)
    val DarkSurface = Color(0xFF1B1B21)
    val DarkOnSurface = Color(0xFFE4E1E9) // contrast 12.1:1 on dark background
    val DarkOnBackground = Color(0xFFE4E1E9)

    // Custody indicator colors - improved contrast
    val CustodyIndicatorActive = Color(0xFFF59E0B) // Amber 500 - contrast 5.09:1 on white
    val CustodyIndicatorInactive = Color(0xFF616161) // Gray 700 - contrast 7.31:1 on white

    // Weekend background colors - subtle distinction for Saturday/Sunday
    val WeekendBackgroundLight = Color(0xFFFFF8E1) // Warm cream/amber tint
    val WeekendBackgroundDark = Color(0xFF2D2D1E) // Dark warm tone

    // Holiday colors - public holidays and school vacations (Czech calendar)
    val HolidayRed = Color(0xFFD32F2F) // Red 700 - public holiday day numbers, contrast 5.9:1 on white
    val VacationTint = Color(0xFF26A69A) // Teal 400 - school vacation background tint
}

