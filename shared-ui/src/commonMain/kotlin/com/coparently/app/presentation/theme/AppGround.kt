package com.coparently.app.presentation.theme

import androidx.compose.foundation.background
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance

/**
 * The app's ground: a soft vertical gradient from the theme's `background` to a slightly deeper
 * lavender (dark: a deeper night), drawn once under the whole navigation graph
 * (November 2026 redesign). Screens' Scaffolds are transparent ([ScreenContainerColor]) so it
 * shows through; cards on it are `surfaceContainer`.
 */
object AppGround {
    /** The ground's brush for the current theme. */
    @Composable
    fun brush(): Brush {
        val top = MaterialTheme.colorScheme.background
        val dark = top.luminance() < CoPlanlyColors.DARK_LUMINANCE_THRESHOLD
        val bottom = if (dark) CoPlanlyColors.DarkGroundEnd else CoPlanlyColors.LightGroundEnd
        return Brush.verticalGradient(listOf(top, bottom))
    }
}

/** Paints [AppGround] behind this element. */
@Composable
fun Modifier.appGround(): Modifier {
    val brush = AppGround.brush()
    return this.background(brush)
}

/**
 * The `containerColor` of every screen's Scaffold: transparent, so [AppGround] shows through.
 * A screen that must paint its own surface says so with a reason.
 */
val ScreenContainerColor: Color = Color.Transparent
