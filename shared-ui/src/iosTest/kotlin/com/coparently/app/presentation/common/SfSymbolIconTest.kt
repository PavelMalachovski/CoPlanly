package com.coparently.app.presentation.common

import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.painter.ColorPainter
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsConfiguration
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import platform.UIKit.UIImage
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The iPhone's SF Symbol icons (MON-24 phase 3, part D, Task 5). shared-ui's iosTest has no Compose
 * UI test harness, so the parts `AppIcon` is made of are tested without composing: the symbol's
 * choice, its painter drawn through a [CanvasDrawScope] as `Modifier.paint` draws it, the tint
 * filter `AppIcon` passes, and the semantics it applies. The composed icon is seen in the `ios-app`
 * job's walk and tour.
 */
class SfSymbolIconTest {

    @Test
    fun everySymbolLoads() {
        val missing = SF_SYMBOLS.values.toSet().filter { UIImage.systemImageNamed(it) == null }.sorted()
        assertTrue(missing.isEmpty(), "symbols this iOS cannot load: $missing")
    }

    @Test
    fun missingSymbolFallsBackToMaterial() {
        assertNull(sfSymbolBitmap(NO_SUCH_SYMBOL, SIZE_PX, FontWeight.Normal))
        assertNotNull(sfSymbolBitmap("plus", SIZE_PX, FontWeight.Normal))

        // No row, a kept-Material row, or a symbol iOS cannot load: AppIcon draws the vector.
        assertNull(sfSymbolNameFor("Filled.NoSuchIcon"))
        assertNull(sfSymbolNameFor("Filled.Gavel"))
        assertNull(sfSymbolNameFor("Filled.Add", mapOf("Filled.Add" to NO_SUCH_SYMBOL)))
        assertEquals("plus", sfSymbolNameFor("Filled.Add"))

        // Should the symbol fail at draw time, the painter draws its fallback, never nothing.
        val pixels = render(SfSymbolPainter(NO_SUCH_SYMBOL, FontWeight.Normal, BOX, ColorPainter(Color.Blue)), null)
        assertTrue(pixels.all { alpha(it) == OPAQUE && blue(it) > HIGH }, "the fallback was not drawn")
    }

    @Test
    fun tintIsAppliedToTemplate() {
        val painter = SfSymbolPainter("plus", FontWeight.Normal, BOX, ColorPainter(Color.Transparent))
        val pixels = render(painter, iconColorFilter(Color.Red))

        val opaque = pixels.filter { alpha(it) > HIGH }
        assertTrue(opaque.isNotEmpty(), "the symbol drew nothing")
        assertTrue(
            opaque.all { red(it) > HIGH && green(it) < LOW && blue(it) < LOW },
            "an opaque pixel is not red"
        )
        assertNull(iconColorFilter(Color.Unspecified))
    }

    @Test
    fun semanticsKeepContentDescription() {
        val semantics = SemanticsConfiguration().apply { describeIcon("add") }

        assertEquals(listOf("add"), semantics.getOrNull(SemanticsProperties.ContentDescription))
        assertEquals(Role.Image, semantics.getOrNull(SemanticsProperties.Role))
    }

    /** [painter] drawn over [BOX] as `Modifier.paint` draws it, with [filter], read back as ARGB. */
    private fun render(painter: Painter, filter: ColorFilter?): IntArray {
        val width = BOX.width.toInt()
        val height = BOX.height.toInt()
        val image = ImageBitmap(width, height)
        CanvasDrawScope().draw(
            density = Density(1f),
            layoutDirection = LayoutDirection.Ltr,
            canvas = Canvas(image),
            size = BOX
        ) {
            with(painter) { draw(BOX, colorFilter = filter) }
        }
        return IntArray(width * height).also { image.readPixels(it) }
    }

    private fun alpha(argb: Int): Int = (argb ushr 24) and BYTE
    private fun red(argb: Int): Int = (argb shr 16) and BYTE
    private fun green(argb: Int): Int = (argb shr 8) and BYTE
    private fun blue(argb: Int): Int = argb and BYTE

    private companion object {
        const val NO_SUCH_SYMBOL = "no.such.symbol.coplanly"
        const val SIZE_PX = 48
        val BOX = Size(72f, 72f)
        const val BYTE = 0xFF
        const val OPAQUE = 255
        const val HIGH = 200
        const val LOW = 60
    }
}
