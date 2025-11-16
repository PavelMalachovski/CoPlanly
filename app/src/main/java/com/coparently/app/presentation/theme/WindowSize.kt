package com.coparently.app.presentation.theme

import androidx.compose.material3.windowsizeclass.WindowSizeClass
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Dimensions data class for responsive design.
 * Contains spacing, sizing, and other dimensional values that adapt to screen size.
 *
 * @property paddingSmall Small padding value (e.g., for compact spacing)
 * @property paddingMedium Medium padding value (e.g., for standard spacing)
 * @property paddingLarge Large padding value (e.g., for generous spacing)
 * @property cardElevation Default elevation for cards
 * @property cornerRadius Default corner radius for UI elements
 * @property iconSize Default icon size
 * @property buttonHeight Default button height
 */
data class Dimensions(
    val paddingSmall: Dp,
    val paddingMedium: Dp,
    val paddingLarge: Dp,
    val cardElevation: Dp,
    val cornerRadius: Dp,
    val iconSize: Dp,
    val buttonHeight: Dp
)

/**
 * Compact dimensions for small screens (phones in portrait mode).
 *
 * Optimized for:
 * - Phone screens (width < 600dp)
 * - Single-column layouts
 * - Touch-friendly spacing
 */
val compactDimensions = Dimensions(
    paddingSmall = 8.dp,
    paddingMedium = 16.dp,
    paddingLarge = 24.dp,
    cardElevation = 4.dp,
    cornerRadius = 12.dp,
    iconSize = 24.dp,
    buttonHeight = 56.dp
)

/**
 * Medium dimensions for medium screens (tablets, phones in landscape).
 *
 * Optimized for:
 * - Tablet screens (600dp ≤ width < 840dp)
 * - Two-column layouts
 * - More generous spacing
 */
val mediumDimensions = Dimensions(
    paddingSmall = 12.dp,
    paddingMedium = 20.dp,
    paddingLarge = 32.dp,
    cardElevation = 6.dp,
    cornerRadius = 16.dp,
    iconSize = 28.dp,
    buttonHeight = 64.dp
)

/**
 * Expanded dimensions for large screens (tablets in landscape, foldables).
 *
 * Optimized for:
 * - Large tablet screens (width ≥ 840dp)
 * - Multi-column layouts
 * - Desktop-like spacing
 */
val expandedDimensions = Dimensions(
    paddingSmall = 16.dp,
    paddingMedium = 24.dp,
    paddingLarge = 40.dp,
    cardElevation = 8.dp,
    cornerRadius = 20.dp,
    iconSize = 32.dp,
    buttonHeight = 72.dp
)

/**
 * CompositionLocal for providing dimensions throughout the app.
 * Defaults to compact dimensions for phones.
 */
val LocalDimensions = staticCompositionLocalOf { compactDimensions }

/**
 * Extension function to get dimensions based on window size class.
 *
 * Maps WindowWidthSizeClass to appropriate Dimensions:
 * - Compact (< 600dp) → compactDimensions
 * - Medium (600-840dp) → mediumDimensions
 * - Expanded (≥ 840dp) → expandedDimensions
 *
 * @return Dimensions appropriate for the current window size
 */
fun WindowSizeClass.getDimensions(): Dimensions {
    return when (widthSizeClass) {
        WindowWidthSizeClass.Compact -> compactDimensions
        WindowWidthSizeClass.Medium -> mediumDimensions
        WindowWidthSizeClass.Expanded -> expandedDimensions
        else -> compactDimensions
    }
}

/**
 * Helper composable to access current dimensions.
 *
 * Usage:
 * ```kotlin
 * @Composable
 * fun MyScreen() {
 *     val dims = dimensions()
 *     Column(modifier = Modifier.padding(dims.paddingMedium)) {
 *         // UI content
 *     }
 * }
 * ```
 *
 * @return Current Dimensions from CompositionLocal
 */
@Composable
fun dimensions(): Dimensions = LocalDimensions.current

