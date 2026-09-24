package com.coparently.app.presentation.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.coparently.app.R

/**
 * The app's typeface: Onest, in four static weights.
 *
 * It replaced Poppins (docs/AUDIT-2026-10-design.md D-8), which has no Cyrillic: in Russian and
 * Ukrainian — two of the five shipped languages — every Cyrillic letter fell back to Roboto, so a
 * heading like "Связь с Bob" changed typeface in the middle of a line, and ₴ and № came from the
 * fallback too. Onest covers Latin with Czech and German, Russian and Ukrainian Cyrillic (і ї є ґ
 * and the apostrophe ʼ), ₴, № and ẞ, and has tabular figures; its Latin runs about 4 % narrower
 * than Poppins', so nothing that fitted before wraps now.
 *
 * The four files are instances of the variable `Onest[wght].ttf` from the Google Fonts repository
 * (version 2.001), cut at 400/500/600/700 with fontTools' instancer, name tables — and so the
 * copyright and licence notice — intact. © 2021 The Onest Project Authors, SIL Open Font License
 * 1.1: `third_party/fonts/onest/OFL.txt`, and Settings → Data sources and licences.
 *
 * - onest_regular.ttf - Normal weight (400)
 * - onest_medium.ttf - Medium weight (500)
 * - onest_semibold.ttf - SemiBold weight (600)
 * - onest_bold.ttf - Bold weight (700)
 */
private val AppFontFamily = FontFamily(
    Font(R.font.onest_regular, FontWeight.Normal),
    Font(R.font.onest_medium, FontWeight.Medium),
    Font(R.font.onest_semibold, FontWeight.SemiBold),
    Font(R.font.onest_bold, FontWeight.Bold)
)

/**
 * Enhanced Typography for CoPlanly app.
 * Uses full Material 3 type scale with customizations.
 *
 * Material 3 Typography Scale:
 * - Display: Large, prominent text (displayLarge, displayMedium, displaySmall)
 * - Headline: Headings and section titles (headlineLarge, headlineMedium, headlineSmall)
 * - Title: Emphasized text and card headers (titleLarge, titleMedium, titleSmall)
 * - Body: Main content text (bodyLarge, bodyMedium, bodySmall)
 * - Label: Buttons and small text (labelLarge, labelMedium, labelSmall)
 */
val Typography = Typography(
    // Display styles - for large, prominent text
    displayLarge = TextStyle(
        fontFamily = AppFontFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 57.sp,
        lineHeight = 64.sp,
        letterSpacing = (-0.25).sp
    ),
    displayMedium = TextStyle(
        fontFamily = AppFontFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 45.sp,
        lineHeight = 52.sp,
        letterSpacing = 0.sp
    ),
    displaySmall = TextStyle(
        fontFamily = AppFontFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 36.sp,
        lineHeight = 44.sp,
        letterSpacing = 0.sp
    ),

    // Headline styles - for headings and section titles
    headlineLarge = TextStyle(
        fontFamily = AppFontFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 32.sp,
        lineHeight = 40.sp,
        letterSpacing = 0.sp
    ),
    headlineMedium = TextStyle(
        fontFamily = AppFontFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 28.sp,
        lineHeight = 36.sp,
        letterSpacing = 0.sp
    ),
    headlineSmall = TextStyle(
        fontFamily = AppFontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 24.sp,
        lineHeight = 32.sp,
        letterSpacing = 0.sp
    ),

    // Title styles - for emphasized text and card headers
    titleLarge = TextStyle(
        fontFamily = AppFontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 22.sp,
        lineHeight = 28.sp,
        letterSpacing = 0.sp
    ),
    titleMedium = TextStyle(
        fontFamily = AppFontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 16.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.15.sp
    ),
    titleSmall = TextStyle(
        fontFamily = AppFontFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.1.sp
    ),

    // Body styles - for main content text
    bodyLarge = TextStyle(
        fontFamily = AppFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.5.sp
    ),
    bodyMedium = TextStyle(
        fontFamily = AppFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.25.sp
    ),
    bodySmall = TextStyle(
        fontFamily = AppFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.4.sp
    ),

    // Label styles - for buttons and small text
    labelLarge = TextStyle(
        fontFamily = AppFontFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.1.sp
    ),
    labelMedium = TextStyle(
        fontFamily = AppFontFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.5.sp
    ),
    labelSmall = TextStyle(
        fontFamily = AppFontFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 11.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.5.sp
    )
)
