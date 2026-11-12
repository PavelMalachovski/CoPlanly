package com.coparently.app.presentation.theme

import androidx.compose.foundation.background
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.TopAppBarColors
import androidx.compose.material3.TopAppBarDefaults
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

/**
 * The colours of every screen's top app bar: transparent at rest, so [AppGround] runs up behind
 * the title rather than under an opaque `surface` band, and `surfaceContainer` once content
 * scrolls under it (November 2026 redesign).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun screenTopAppBarColors(): TopAppBarColors = TopAppBarDefaults.topAppBarColors(
    containerColor = Color.Transparent,
    scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainer
)
