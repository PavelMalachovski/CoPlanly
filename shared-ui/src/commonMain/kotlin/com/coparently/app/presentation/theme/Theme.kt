package com.coparently.app.presentation.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.material3.windowsizeclass.WindowSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color

/**
 * Light color scheme for CoPlanly app.
 * Enhanced with better contrast and brand colors.
 *
 * Public, where it used to be internal, because the home-screen widget, which stays in the app,
 * fills its Glance colours from it (MON-24 phase 2, Task 9).
 */
val LightColorScheme = lightColorScheme(
    primary = CoPlanlyColors.BrandPrimary,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE4DEFA),
    onPrimaryContainer = Color(0xFF2A1F70),
    // Neutral, NOT Mom-pink — see CoPlanlyColors.NeutralSecondary. Feeds generic
    // Material selected states (FilterChips); parent pink/blue is applied directly.
    secondary = CoPlanlyColors.NeutralSecondary,
    onSecondary = Color.White,
    secondaryContainer = CoPlanlyColors.NeutralSecondaryContainer,
    onSecondaryContainer = CoPlanlyColors.NeutralOnSecondaryContainer,
    tertiary = CoPlanlyColors.BrandAccent,
    onTertiary = Color.White,
    // Emerald like `tertiary`, which tints an event that belongs to neither parent. Left unset it
    // was Material's baseline pink (#FFD8E4): a parent's hue on the one kind of event that is
    // neither parent's (docs/AUDIT-2026-10-design.md D-25).
    tertiaryContainer = Color(0xFFC1ECD6),
    onTertiaryContainer = Color(0xFF002115),
    background = CoPlanlyColors.LightBackground,
    onBackground = CoPlanlyColors.LightOnBackground,
    surface = CoPlanlyColors.LightSurface,
    onSurface = CoPlanlyColors.LightOnSurface,
    surfaceVariant = Color(0xFFE4E1EC),
    onSurfaceVariant = Color(0xFF47464F),
    error = Color(0xFFBA1A1A),
    onError = Color.White,
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002),
    outline = Color(0xFF777680),
    outlineVariant = Color(0xFFECE8F6),
    scrim = Color.Black,
    inverseSurface = CoPlanlyColors.DarkSurface,
    inverseOnSurface = CoPlanlyColors.DarkOnSurface,
    inversePrimary = Color(0xFFC2C1FF),
    surfaceDim = Color(0xFFDCD9E0),
    surfaceBright = CoPlanlyColors.LightBackground,
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color(0xFFF1EEFA),
    // White cards on the lavender ground (November 2026 redesign).
    surfaceContainer = Color.White,
    surfaceContainerHigh = Color(0xFFEAE6F6),
    surfaceContainerHighest = Color(0xFFE3DFF1)
)

/**
 * Dark color scheme for CoPlanly app.
 * Enhanced with better contrast and brand colors.
 *
 * Public for the widget, as [LightColorScheme] is.
 */
val DarkColorScheme = darkColorScheme(
    primary = Color(0xFFC2C1FF),
    onPrimary = Color(0xFF201F60),
    primaryContainer = Color(0xFF3A3470),
    onPrimaryContainer = CoPlanlyColors.BrandPrimaryContainer,
    // Neutral, NOT Mom-pink — see CoPlanlyColors.NeutralSecondary.
    secondary = CoPlanlyColors.NeutralSecondaryDark,
    onSecondary = CoPlanlyColors.NeutralOnSecondaryDark,
    secondaryContainer = CoPlanlyColors.NeutralSecondaryContainerDark,
    onSecondaryContainer = CoPlanlyColors.NeutralSecondaryContainer,
    tertiary = Color(0xFF6EE7B7),
    onTertiary = Color(0xFF003824),
    // See the light scheme: Material's baseline here was a maroon (#633B48).
    tertiaryContainer = Color(0xFF274E3E),
    onTertiaryContainer = Color(0xFFC1ECD6),
    background = CoPlanlyColors.DarkBackground,
    onBackground = CoPlanlyColors.DarkOnBackground,
    surface = CoPlanlyColors.DarkSurface,
    onSurface = CoPlanlyColors.DarkOnSurface,
    surfaceVariant = Color(0xFF47464F),
    onSurfaceVariant = Color(0xFFC8C5D0),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
    outline = Color(0xFF918F9A),
    outlineVariant = Color(0xFF3A3648),
    scrim = Color.Black,
    inverseSurface = CoPlanlyColors.LightSurface,
    inverseOnSurface = CoPlanlyColors.LightOnSurface,
    inversePrimary = CoPlanlyColors.BrandPrimary,
    surfaceDim = CoPlanlyColors.DarkBackground,
    surfaceBright = Color(0xFF39383F),
    surfaceContainerLowest = Color(0xFF0E0E13),
    surfaceContainerLow = Color(0xFF1B1926),
    surfaceContainer = Color(0xFF211E2C),
    surfaceContainerHigh = Color(0xFF29292F),
    surfaceContainerHighest = Color(0xFF34333A)
)

/**
 * Material 3 theme for CoPlanly app.
 * Supports both light and dark themes with enhanced color schemes,
 * and responsive design based on window size.
 *
 * There is no dynamic (wallpaper) colour, on purpose: the brand colour and the two parents' own
 * colours are the product's identity, and a wallpaper-derived `primary` would sit beside a parent
 * palette it was never checked against. The branch that offered it had no caller and was removed
 * in the October 2026 audit (D-25).
 *
 * The scheme follows the contrast the person asked the system for on Android 14 and later
 * (medium or high, see [ContrastLevel]); before that, and when nobody asked, it is the one below.
 *
 * @param darkTheme Whether to use dark theme (defaults to system setting)
 * @param windowSizeClass Window size class for responsive dimensions (optional)
 * @param contrastLevel A contrast level to use instead of the system's — for previews and the
 *   screenshot tests; null follows the system
 * @param content The composable content to display with this theme
 */
@Composable
fun CoPlanlyTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    windowSizeClass: WindowSizeClass? = null,
    contrastLevel: ContrastLevel? = null,
    content: @Composable () -> Unit
) {
    val colorScheme = colorSchemeFor(darkTheme, contrastLevel ?: rememberSystemContrastLevel())

    // Dimensions come from the caller's window size class when one is given — previews and
    // tests do that — and otherwise from `adaptiveDimensions()`, which works the size class out
    // for itself and folds in font scale and touch exploration.
    //
    // The fallback used to be `compactDimensions`, and **no caller ever passed a size class**:
    // `MainActivity` and `QRScannerActivity` both call `CoPlanlyTheme(darkTheme = …)`. So every
    // device resolved to phone padding — tablets and unfolded folds included — and
    // `adaptiveDimensions()`, then the only code in the project that read `fontScale` or
    // `isTouchExplorationEnabled`, was never called by anything. Two features were switched off
    // by a default argument.
    //
    // Outside an Activity (a Compose preview) it degrades to exactly the old behaviour, because
    // there is no window to measure.
    val dimensions = windowSizeClass?.getDimensions() ?: adaptiveDimensions()

    // The system bars' icons follow the app's theme rather than the system's: Android's window
    // flags (`Theme.android.kt`); on the iPhone the hosting view controller's, from phase 3.
    SystemBarsAppearance(darkTheme)

    CompositionLocalProvider(LocalDimensions provides dimensions) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = rememberAppTypography(),
            shapes = CoPlanlyShapes,
            content = content
        )
    }
}

/**
 * Makes the system bars' icons light or dark to match [darkTheme], and the bars themselves
 * transparent, where the platform lets an app say so.
 *
 * @param darkTheme Whether the app is drawing its dark theme
 */
@Composable
internal expect fun SystemBarsAppearance(darkTheme: Boolean)
