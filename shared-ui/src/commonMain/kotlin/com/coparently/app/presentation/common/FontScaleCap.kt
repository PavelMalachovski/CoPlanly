package com.coparently.app.presentation.common

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit

/**
 * Draws [content] at the reader's font scale, but at most [max].
 *
 * For text that lives inside a shape of a fixed size, which icons are too (design refresh item 1:
 * "Icons do not scale with the font"): an initial on an avatar's disc, a count on a tab icon's
 * badge. Left to grow, the initial filled its disc and ran under the next one, and the badge
 * covered the icon it counts for (the screenshots at 150 % and 200 %). What such text says is said
 * in full by the description around it, so capping it hides nothing from the reader.
 *
 * Never for a sentence, a label or an amount: those grow with the font and the layout makes room.
 *
 * @param max The largest font scale [content] is drawn at.
 */
@Composable
fun CappedFontScale(max: Float, content: @Composable () -> Unit) {
    val density = LocalDensity.current
    if (density.fontScale <= max) {
        content()
    } else {
        CompositionLocalProvider(LocalDensity provides Density(density.density, max)) { content() }
    }
}

/**
 * The font scale at which text of [fontSize] takes [share] of [box]'s side: the cap
 * ([CappedFontScale]) for an initial on a disc of that size. Never below 1, so the default size is
 * always drawn as it was.
 */
fun fontScaleToFit(box: Dp, fontSize: TextUnit, share: Float): Float =
    if (!fontSize.isSp || fontSize.value <= 0f) 1f else (box.value * share / fontSize.value).coerceAtLeast(1f)
