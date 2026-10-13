package com.coparently.app.presentation.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import org.junit.Test
import kotlin.math.abs
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The tones a parent's own colour is drawn in, derived from nothing but a hue.
 *
 * A custom colour is any hue on the wheel, so the guarantees the four presets were checked for by
 * hand have to hold for all 360 of them by construction: a dot that shows on every surface, text
 * that reads in both themes, a chip whose label clears AA. These tests walk the whole wheel.
 */
class ParentToneMathTest {

    @Test
    fun `luminance agrees with Compose's own`() {
        // The palette is tested with Compose's `luminance()` elsewhere; two formulas that disagreed
        // would let a tone pass here and fail there.
        listOf(0xFFE91E63.toInt(), 0xFF2979FF.toInt(), 0xFF808080.toInt(), 0xFF000000.toInt(), 0xFFFFFFFF.toInt())
            .forEach { argb ->
                assertEquals(Color(argb).luminance().toDouble(), ParentToneMath.luminance(argb), 1e-4)
            }
    }

    @Test
    fun `contrast is the WCAG ratio and symmetric`() {
        assertEquals(21.0, ParentToneMath.contrast(WHITE, BLACK), 0.01)
        assertEquals(1.0, ParentToneMath.contrast(WHITE, WHITE), 1e-9)
        assertEquals(
            ParentToneMath.contrast(0xFFE91E63.toInt(), WHITE),
            ParentToneMath.contrast(WHITE, 0xFFE91E63.toInt()),
            1e-12
        )
    }

    @Test
    fun `the hue of a colour is read back in degrees`() {
        assertEquals(0f, ParentToneMath.hueOf(0xFFFF0000.toInt()), 0.5f)
        assertEquals(120f, ParentToneMath.hueOf(0xFF00FF00.toInt()), 0.5f)
        assertEquals(240f, ParentToneMath.hueOf(0xFF0000FF.toInt()), 0.5f)
        assertEquals(217.6f, ParentToneMath.hueOf(0xFF2979FF.toInt()), 0.5f)
        // A grey has no hue; it reads as 0 rather than as NaN.
        assertEquals(0f, ParentToneMath.hueOf(0xFF808080.toInt()))
    }

    @Test
    fun `every hue's fill is a visible graphic on the light theme's surfaces`() {
        forEveryHue { hue, tones ->
            LIGHT_SURFACES.forEach { (name, surface) ->
                val ratio = ParentToneMath.contrast(tones.fill, surface)
                assertTrue(ratio >= NON_TEXT_MINIMUM, "hue $hue fill on light $name: $ratio")
            }
        }
    }

    @Test
    fun `every hue's dark fill is a visible graphic on the dark theme's surfaces`() {
        forEveryHue { hue, tones ->
            DARK_SURFACES.forEach { (name, surface) ->
                val ratio = ParentToneMath.contrast(tones.darkFill, surface)
                assertTrue(ratio >= NON_TEXT_MINIMUM, "hue $hue dark fill on dark $name: $ratio")
            }
        }
    }

    @Test
    fun `every hue's text tones read at AA on their theme's surfaces`() {
        forEveryHue { hue, tones ->
            LIGHT_SURFACES.forEach { (name, surface) ->
                val ratio = ParentToneMath.contrast(tones.dark, surface)
                assertTrue(ratio >= AA_NORMAL_TEXT, "hue $hue text on light $name: $ratio")
            }
            DARK_SURFACES.forEach { (name, surface) ->
                val ratio = ParentToneMath.contrast(tones.light, surface)
                assertTrue(ratio >= AA_NORMAL_TEXT, "hue $hue text on dark $name: $ratio")
            }
        }
    }

    @Test
    fun `every hue's chip tone carries a white label at AA`() {
        forEveryHue { hue, tones ->
            val ratio = ParentToneMath.contrast(WHITE, tones.dark)
            assertTrue(ratio >= AA_NORMAL_TEXT, "hue $hue white on chip: $ratio")
        }
    }

    @Test
    fun `every tone keeps the hue it was asked for`() {
        // A parent who picks teal-blue must not be drawn turquoise in one theme and blue in the other.
        forEveryHue { hue, tones ->
            listOf(tones.fill, tones.darkFill, tones.light, tones.dark).forEach { tone ->
                val drawn = ParentToneMath.hueOf(tone)
                assertTrue(hueDistance(hue.toFloat(), drawn) <= HUE_TOLERANCE, "hue $hue drifted to $drawn")
            }
        }
    }

    @Test
    fun `the derivation is the same on every call`() {
        // Both phones draw the co-parent from the stored hue alone; the answer must not vary.
        assertEquals(ParentToneMath.deriveTones(215f), ParentToneMath.deriveTones(215f))
    }

    @Test
    fun `a wash tone is capped at its luminance ceiling and keeps its hue`() {
        val bright = ParentToneMath.deriveTones(55f).darkFill // a yellow, the brightest dark fills
        val capped = ParentToneMath.capLuminance(bright, ParentToneMath.DARK_WASH_MAX_LUMINANCE)

        assertTrue(ParentToneMath.luminance(bright) > ParentToneMath.DARK_WASH_MAX_LUMINANCE)
        assertTrue(ParentToneMath.luminance(capped) <= ParentToneMath.DARK_WASH_MAX_LUMINANCE + LUMINANCE_ROUNDING)
        assertTrue(hueDistance(ParentToneMath.hueOf(bright), ParentToneMath.hueOf(capped)) <= HUE_TOLERANCE)
    }

    @Test
    fun `a tone already under the ceiling is left exactly as it is`() {
        val blue = 0xFF448AFF.toInt()

        assertEquals(blue, ParentToneMath.capLuminance(blue, ParentToneMath.DARK_WASH_MAX_LUMINANCE))
    }

    private fun forEveryHue(check: (hue: Int, tones: ParentTones) -> Unit) {
        (0 until FULL_TURN).forEach { hue -> check(hue, ParentToneMath.deriveTones(hue.toFloat())) }
    }

    private fun hueDistance(a: Float, b: Float): Float {
        val d = abs(a - b) % FULL_TURN
        return if (d > FULL_TURN / 2) FULL_TURN - d else d
    }

    private companion object {
        const val AA_NORMAL_TEXT = 4.5
        const val NON_TEXT_MINIMUM = 3.0
        const val FULL_TURN = 360
        const val HUE_TOLERANCE = 3f

        /** One step of an 8-bit channel can lift a tone's luminance a hair over its target. */
        const val LUMINANCE_ROUNDING = 0.005

        val WHITE = Color.White.argb()
        val BLACK = Color.Black.argb()

        /** The surfaces a parent colour is drawn over in the light theme (see `ParentColorsTest`). */
        val LIGHT_SURFACES = listOf(
            "surface" to CoPlanlyColors.LightSurface.argb(),
            "background" to CoPlanlyColors.LightBackground.argb(),
            "weekend" to CoPlanlyColors.WeekendBackgroundLight.argb(),
            "surfaceContainer" to 0xFFF0EEF5.toInt()
        )

        /** The same four in the dark theme. */
        val DARK_SURFACES = listOf(
            "surface" to CoPlanlyColors.DarkSurface.argb(),
            "background" to CoPlanlyColors.DarkBackground.argb(),
            "weekend" to CoPlanlyColors.WeekendBackgroundDark.argb(),
            "surfaceContainer" to 0xFF1F1F25.toInt()
        )
    }
}
