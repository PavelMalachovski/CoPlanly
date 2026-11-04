package com.coparently.app.presentation.theme

import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.coparently.app.resources.Res
import com.coparently.app.resources.onest_bold
import com.coparently.app.resources.onest_medium
import com.coparently.app.resources.onest_regular
import com.coparently.app.resources.onest_semibold
import org.jetbrains.compose.resources.Font

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
 *
 * They are Compose Multiplatform resources since MON-24 phase 2 (Task 9), in `:shared-ui`'s
 * `composeResources/font/`, so the iPhone draws the same files. On Android each is read from the
 * APK's assets (`composeResources/com.coparently.app.resources/font/`), where it used to be read
 * from `res/font`: the same bytes, through the same `Font.Builder`, with the same weights declared
 * here, so the glyphs are the ones the screenshot baselines hold. Composable because a resource is
 * resolved in composition; the family is remembered, so a recomposition does not hand
 * [MaterialTheme][androidx.compose.material3.MaterialTheme] a new typography.
 *
 * @return Onest, in its four weights
 */
@Composable
fun onestFontFamily(): FontFamily {
    val regular = Font(Res.font.onest_regular, FontWeight.Normal)
    val medium = Font(Res.font.onest_medium, FontWeight.Medium)
    val semiBold = Font(Res.font.onest_semibold, FontWeight.SemiBold)
    val bold = Font(Res.font.onest_bold, FontWeight.Bold)
    return remember(regular, medium, semiBold, bold) { FontFamily(regular, medium, semiBold, bold) }
}

/**
 * The app's type scale: each role's weight, size, line height and spacing, **without a typeface**.
 *
 * A role's typeface is a resource, and a resource is resolved in composition, so the theme sets
 * this scale in Onest there ([rememberAppTypography]) and every screen reads the result from
 * `MaterialTheme.typography`. This value stays a plain one for what is not composed in the theme:
 * the home-screen widget, whose text takes these sizes and weights in the launcher's own font
 * (CLAUDE.md design item 18).
 *
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
        fontWeight = FontWeight.Bold,
        fontSize = 57.sp,
        lineHeight = 64.sp,
        letterSpacing = (-0.25).sp
    ),
    displayMedium = TextStyle(
        fontWeight = FontWeight.Bold,
        fontSize = 45.sp,
        lineHeight = 52.sp,
        letterSpacing = 0.sp
    ),
    displaySmall = TextStyle(
        fontWeight = FontWeight.Bold,
        fontSize = 36.sp,
        lineHeight = 44.sp,
        letterSpacing = 0.sp
    ),

    // Headline styles - for headings and section titles
    headlineLarge = TextStyle(
        fontWeight = FontWeight.Bold,
        fontSize = 32.sp,
        lineHeight = 40.sp,
        letterSpacing = 0.sp
    ),
    headlineMedium = TextStyle(
        fontWeight = FontWeight.Bold,
        fontSize = 28.sp,
        lineHeight = 36.sp,
        letterSpacing = 0.sp
    ),
    headlineSmall = TextStyle(
        fontWeight = FontWeight.SemiBold,
        fontSize = 24.sp,
        lineHeight = 32.sp,
        letterSpacing = 0.sp
    ),

    // Title styles - for emphasized text and card headers
    titleLarge = TextStyle(
        fontWeight = FontWeight.SemiBold,
        fontSize = 22.sp,
        lineHeight = 28.sp,
        letterSpacing = 0.sp
    ),
    titleMedium = TextStyle(
        fontWeight = FontWeight.SemiBold,
        fontSize = 16.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.15.sp
    ),
    titleSmall = TextStyle(
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.1.sp
    ),

    // Body styles - for main content text
    bodyLarge = TextStyle(
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.5.sp
    ),
    bodyMedium = TextStyle(
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.25.sp
    ),
    bodySmall = TextStyle(
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.4.sp
    ),

    // Label styles - for buttons and small text
    labelLarge = TextStyle(
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.1.sp
    ),
    labelMedium = TextStyle(
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.5.sp
    ),
    labelSmall = TextStyle(
        fontWeight = FontWeight.Medium,
        fontSize = 11.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.5.sp
    )
)

/**
 * [Typography] set in Onest: what the theme gives `MaterialTheme`.
 *
 * @return The type scale, every role in [onestFontFamily]
 */
@Composable
internal fun rememberAppTypography(): Typography {
    val family = onestFontFamily()
    return remember(family) { Typography.withFontFamily(family) }
}

/**
 * This scale with every one of its fifteen roles set in [family], built as [Typography] itself is,
 * so the styles Material derives from its own tokens are the ones it always had.
 *
 * @param family The typeface
 */
internal fun Typography.withFontFamily(family: FontFamily): Typography = Typography(
    displayLarge = displayLarge.copy(fontFamily = family),
    displayMedium = displayMedium.copy(fontFamily = family),
    displaySmall = displaySmall.copy(fontFamily = family),
    headlineLarge = headlineLarge.copy(fontFamily = family),
    headlineMedium = headlineMedium.copy(fontFamily = family),
    headlineSmall = headlineSmall.copy(fontFamily = family),
    titleLarge = titleLarge.copy(fontFamily = family),
    titleMedium = titleMedium.copy(fontFamily = family),
    titleSmall = titleSmall.copy(fontFamily = family),
    bodyLarge = bodyLarge.copy(fontFamily = family),
    bodyMedium = bodyMedium.copy(fontFamily = family),
    bodySmall = bodySmall.copy(fontFamily = family),
    labelLarge = labelLarge.copy(fontFamily = family),
    labelMedium = labelMedium.copy(fontFamily = family),
    labelSmall = labelSmall.copy(fontFamily = family)
)

/*
 * Emphasised styles: a role's size and line height one weight step heavier.
 *
 * Screens used to write `style = titleMedium, fontWeight = FontWeight.Bold`: 83 weight overrides,
 * the same role made heavier by different amounts on different screens, and 18 that set the
 * weight the role already had (docs/AUDIT-2026-10-design.md, Typography). A screen now names the
 * emphasis instead, and it is one step for every role. Material 3 Expressive has these as
 * `Typography` members in material3 1.5, which is still alpha; these carry the same names, so
 * moving to it deletes this block.
 */

/** [Typography.headlineSmall], one weight step heavier. */
val Typography.headlineSmallEmphasized: TextStyle get() = headlineSmall.emphasized()

/** [Typography.titleLarge], one weight step heavier. */
val Typography.titleLargeEmphasized: TextStyle get() = titleLarge.emphasized()

/** [Typography.titleMedium], one weight step heavier. */
val Typography.titleMediumEmphasized: TextStyle get() = titleMedium.emphasized()

/** [Typography.titleSmall], one weight step heavier. */
val Typography.titleSmallEmphasized: TextStyle get() = titleSmall.emphasized()

/** [Typography.bodyLarge], one weight step heavier. */
val Typography.bodyLargeEmphasized: TextStyle get() = bodyLarge.emphasized()

/** [Typography.bodyMedium], one weight step heavier. */
val Typography.bodyMediumEmphasized: TextStyle get() = bodyMedium.emphasized()

/** [Typography.bodySmall], one weight step heavier. */
val Typography.bodySmallEmphasized: TextStyle get() = bodySmall.emphasized()

/** [Typography.labelLarge], one weight step heavier. */
val Typography.labelLargeEmphasized: TextStyle get() = labelLarge.emphasized()

/** [Typography.labelMedium], one weight step heavier. */
val Typography.labelMediumEmphasized: TextStyle get() = labelMedium.emphasized()

/** [Typography.labelSmall], one weight step heavier. */
val Typography.labelSmallEmphasized: TextStyle get() = labelSmall.emphasized()

/** One of Onest's four weights up from the style's own, and no further than Bold. */
private fun TextStyle.emphasized(): TextStyle {
    val weight = (fontWeight ?: FontWeight.Normal).weight
    return copy(fontWeight = FontWeight(minOf(weight + WEIGHT_STEP, FontWeight.Bold.weight)))
}

/** The step between two of the typeface's weights: 400, 500, 600, 700. */
private const val WEIGHT_STEP = 100
