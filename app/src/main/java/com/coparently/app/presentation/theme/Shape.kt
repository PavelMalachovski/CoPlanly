package com.coparently.app.presentation.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

/**
 * Expressive shape system for CoPlanly.
 * Generous corner radii give the app a soft, modern look consistent with
 * current Material 3 expressive styling.
 *
 * - extraSmall: chips, small controls
 * - small: text fields, menu surfaces
 * - medium: cards, list items
 * - large: dialogs, bottom sheets, FAB
 * - extraLarge: hero surfaces, full-screen sheets
 */
val CoPlanlyShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(32.dp)
)
