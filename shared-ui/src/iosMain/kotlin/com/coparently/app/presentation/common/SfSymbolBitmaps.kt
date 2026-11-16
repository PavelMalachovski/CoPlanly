package com.coparently.app.presentation.common

import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsPropertyReceiver
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import com.coparently.app.presentation.platform.IosImageDecoder
import com.coparently.app.presentation.platform.IosImageMemory
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.useContents
import platform.UIKit.UIFont
import platform.UIKit.UIFontWeight
import platform.UIKit.UIFontWeightRegular
import platform.UIKit.UIFontWeightSemibold
import platform.UIKit.UIImage
import platform.UIKit.UIImageSymbolConfiguration
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * The SF Symbol [name] rasterised at a point size of [sizePx] pixels in [weight], or null when this
 * iOS has no such symbol (`UIImage.systemImageNamed` answers null) or UIKit draws nothing.
 *
 * The symbol is drawn as a template: only its alpha means anything, and the caller tints it with
 * `ColorFilter.tint` ([iconColorFilter]), as Material's `Icon` tints its vector. It is drawn at
 * scale 1 with the point size in pixels, which gives the same pixels as the size in points at the
 * screen's scale. Results are kept in a small LRU keyed on the three arguments ([SymbolBitmaps]).
 * Called on the main thread, where Compose draws.
 */
internal fun sfSymbolBitmap(name: String, sizePx: Int, weight: FontWeight): ImageBitmap? =
    SymbolBitmaps.get(name, sizePx, weight)

/**
 * The symbol [AppIcon] draws for an icon named [vectorName] (its `ImageVector.name`), or null when
 * it draws the Material vector: no row in [table] (a kept-Material icon included), or a symbol this
 * iOS cannot load. Never another symbol.
 */
internal fun sfSymbolNameFor(vectorName: String, table: Map<String, String> = SF_SYMBOLS): String? =
    table[vectorName]?.takeIf { SymbolBitmaps.exists(it) }

/** The colour filter Material's `Icon` applies for [tint]: none for an unspecified tint. */
internal fun iconColorFilter(tint: Color): ColorFilter? =
    if (tint == Color.Unspecified) null else ColorFilter.tint(tint)

/** What Material's `Icon` says of an icon with a description: the description, and that it is an image. */
internal fun SemanticsPropertyReceiver.describeIcon(description: String) {
    contentDescription = description
    role = Role.Image
}

/**
 * UIKit's weight for [weight]: semibold from SemiBold up, regular below. The symbol takes it from a
 * system font of its point size (`configurationWithFont`), which configures the same point size and
 * weight as `UIImageSymbolWeightRegular`/`Semibold` would; a font weight is a plain constant, where
 * `UIImageSymbolWeight` is an enum whose Kotlin/Native form varies.
 */
internal fun symbolWeightOf(weight: FontWeight): UIFontWeight =
    if (weight >= FontWeight.SemiBold) UIFontWeightSemibold else UIFontWeightRegular

/**
 * Draws the symbol [name] the way Material's vector painter draws an icon: [intrinsicSize] is the
 * vector's own (24 dp), so `Modifier.paint` sizes the icon as Material's `Icon` does, and the symbol's
 * point size is [GLYPH_FRACTION] of the box it is drawn in, centred — the 20 dp live area of
 * Material's 24 dp icon. Should UIKit give nothing back at draw time, [fallback] (the Material
 * vector) is drawn instead, so an icon never draws nothing.
 */
internal class SfSymbolPainter(
    private val name: String,
    private val weight: FontWeight,
    override val intrinsicSize: Size,
    private val fallback: Painter,
) : Painter() {

    private var alpha: Float = 1f
    private var colorFilter: ColorFilter? = null

    override fun applyAlpha(alpha: Float): Boolean {
        this.alpha = alpha
        return true
    }

    override fun applyColorFilter(colorFilter: ColorFilter?): Boolean {
        this.colorFilter = colorFilter
        return true
    }

    override fun DrawScope.onDraw() {
        val pointSize = (min(size.width, size.height) * GLYPH_FRACTION).roundToInt()
        val bitmap = if (pointSize > 0) sfSymbolBitmap(name, pointSize, weight) else null
        if (bitmap == null) {
            with(fallback) { draw(size, alpha, colorFilter) }
            return
        }
        // A symbol's image is about its point size; a wide one is scaled down to the box, never up.
        val fit = min(1f, min(size.width / bitmap.width, size.height / bitmap.height))
        val width = (bitmap.width * fit).roundToInt()
        val height = (bitmap.height * fit).roundToInt()
        drawImage(
            image = bitmap,
            srcOffset = IntOffset.Zero,
            srcSize = IntSize(bitmap.width, bitmap.height),
            dstOffset = IntOffset(((size.width - width) / 2f).roundToInt(), ((size.height - height) / 2f).roundToInt()),
            dstSize = IntSize(width, height),
            alpha = alpha,
            colorFilter = colorFilter
        )
    }

    companion object {
        /** Material's live area, 20 dp of a 24 dp icon. */
        const val GLYPH_FRACTION: Float = 20f / 24f
    }
}

/**
 * The rasterised symbols, the most recently used up to [BUDGET_BYTES] decoded bytes, and the names
 * this iOS does not know. Main thread only, as [IosImageMemory] is.
 */
private object SymbolBitmaps {

    /** About a thousand 24 dp icons at 3x. */
    const val BUDGET_BYTES: Long = 8L * 1024L * 1024L

    private val bitmaps = IosImageMemory(BUDGET_BYTES)
    private val known = HashMap<String, Boolean>()

    fun exists(name: String): Boolean = known.getOrPut(name) { UIImage.systemImageNamed(name) != null }

    fun get(name: String, sizePx: Int, weight: FontWeight): ImageBitmap? {
        if (sizePx <= 0 || !exists(name)) return null
        val key = "$name@$sizePx@${weight.weight}"
        return bitmaps[key] ?: render(name, sizePx, weight)?.also { bitmaps[key] = it }
    }

    @OptIn(ExperimentalForeignApi::class)
    private fun render(name: String, sizePx: Int, weight: FontWeight): ImageBitmap? {
        val font = UIFont.systemFontOfSize(sizePx.toDouble(), symbolWeightOf(weight))
        val configuration = UIImageSymbolConfiguration.configurationWithFont(font)
        val image = UIImage.systemImageNamed(name, withConfiguration = configuration) ?: return null
        val (width, height) = image.size.useContents { width to height }
        if (width <= 0.0 || height <= 0.0) return null
        return IosImageDecoder.pngOf(image, width, height)?.let { IosImageDecoder.bitmapOfEncoded(it) }
    }
}
