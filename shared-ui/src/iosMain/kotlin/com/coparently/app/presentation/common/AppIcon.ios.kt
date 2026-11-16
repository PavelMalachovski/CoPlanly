package com.coparently.app.presentation.common

import androidx.compose.foundation.layout.Box
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.paint
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight

/**
 * The iPhone's icon (MON-24 phase 3, part D, Task 5): the icon's SF Symbol ([SF_SYMBOLS]), drawn
 * inside Compose as Material's `Icon` draws its vector — the same size (the vector's own 24 dp unless
 * the modifier says otherwise, through `Modifier.paint`'s intrinsic sizing), the same tint (a
 * `ColorFilter.tint` over the template, [iconColorFilter]) and the same semantics (the description
 * and `Role.Image`, none for a decorative icon). No native view draws it (design refresh item 1).
 *
 * An icon with no row, one the approved table keeps Material, or a symbol this iOS cannot load
 * draws Material's `Icon` with the vector, exactly as on Android.
 */
@Composable
actual fun AppIcon(
    imageVector: ImageVector,
    contentDescription: String?,
    modifier: Modifier,
    tint: Color
) {
    val symbol = remember(imageVector.name) { sfSymbolNameFor(imageVector.name) }
    if (symbol == null) {
        Icon(imageVector = imageVector, contentDescription = contentDescription, modifier = modifier, tint = tint)
        return
    }
    val fallback = rememberVectorPainter(imageVector)
    val density = LocalDensity.current
    val painter = remember(symbol, imageVector, density, fallback) {
        val intrinsic = with(density) { Size(imageVector.defaultWidth.toPx(), imageVector.defaultHeight.toPx()) }
        SfSymbolPainter(symbol, FontWeight.Normal, intrinsic, fallback)
    }
    val colorFilter = remember(tint) { iconColorFilter(tint) }
    val semantics = if (contentDescription != null) {
        Modifier.semantics { describeIcon(contentDescription) }
    } else {
        Modifier
    }
    Box(modifier.paint(painter, colorFilter = colorFilter, contentScale = ContentScale.Fit).then(semantics))
}
