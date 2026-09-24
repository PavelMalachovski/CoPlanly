package com.coparently.app.presentation.theme

import androidx.compose.ui.graphics.Color
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The contrast decisions behind a label drawn *on* a parent colour (UX-15).
 *
 * Once a parent can choose their colour, "white text on the parent's fill" stops being a
 * decision that was checked once for pink and blue: every choice has to clear AA, and the text
 * colour has to be picked rather than assumed.
 */
class ParentColorsTest {

    @Test
    fun `contrast ratio matches the WCAG extremes and is symmetric`() {
        assertEquals(21f, ParentColors.contrastRatio(Color.White, Color.Black), 0.01f)
        assertEquals(1f, ParentColors.contrastRatio(Color.White, Color.White), 0.001f)
        assertEquals(
            ParentColors.contrastRatio(CoPlanlyColors.MomPink, Color.White),
            ParentColors.contrastRatio(Color.White, CoPlanlyColors.MomPink),
            0.0001f
        )
    }

    @Test
    fun `white on the full-strength pink is under AA - which is why the band does not use it`() {
        // The week view's custody band used to write white on MomPink: 4.35:1.
        val ratio = ParentColors.contrastRatio(Color.White, CoPlanlyColors.MomPink)

        assertTrue(ratio < AA_NORMAL_TEXT, "expected under 4.5:1, was $ratio")
    }

    @Test
    fun `every choice's chip tone carries a label at AA with the colour onFill picks`() {
        ParentColorChoice.entries.forEach { choice ->
            val label = ParentColors.onFill(choice.dark)
            val ratio = ParentColors.contrastRatio(label, choice.dark)

            assertTrue(ratio >= AA_NORMAL_TEXT, "${choice.name}: $ratio")
        }
    }

    @Test
    fun `onFill picks black on a light background and white on a dark one`() {
        assertEquals(Color.Black, ParentColors.onFill(Color.White))
        assertEquals(Color.Black, ParentColors.onFill(ParentColorChoice.PINK.light))
        assertEquals(Color.White, ParentColors.onFill(Color.Black))
        assertEquals(Color.White, ParentColors.onFill(ParentColorChoice.BLUE.dark))
    }

    @Test
    fun `every choice's fill is a visible graphic on every surface it is drawn on, in both themes`() {
        // WCAG 1.4.11: a dot or bar that identifies a parent needs 3:1 against what is under it.
        // Purple was 2.09:1 on the dark surface and 1.74:1 on the dark weekend grey before it got
        // a dark-theme fill (docs/AUDIT-2026-10-design.md D-14).
        ParentColorChoice.entries.forEach { choice ->
            LIGHT_SURFACES.forEach { (name, surface) ->
                val ratio = ParentColors.contrastRatio(choice.fill, surface)
                assertTrue(ratio >= NON_TEXT_MINIMUM, "${choice.name} on light $name: $ratio")
            }
            DARK_SURFACES.forEach { (name, surface) ->
                val ratio = ParentColors.contrastRatio(choice.darkFill, surface)
                assertTrue(ratio >= NON_TEXT_MINIMUM, "${choice.name} on dark $name: $ratio")
            }
        }
    }

    @Test
    fun `a holiday's day number reads at AA on the plain surface and the weekend grey`() {
        LIGHT_SURFACES.filter { (name, _) -> name != "surfaceContainer" }.forEach { (name, surface) ->
            val ratio = ParentColors.contrastRatio(CoPlanlyColors.HolidayRed, surface)
            assertTrue(ratio >= AA_NORMAL_TEXT, "light $name: $ratio")
        }
        DARK_SURFACES.forEach { (name, surface) ->
            val ratio = ParentColors.contrastRatio(CoPlanlyColors.HolidayRedDark, surface)
            assertTrue(ratio >= AA_NORMAL_TEXT, "dark $name: $ratio")
        }
    }

    private companion object {
        /** WCAG 2.x AA minimum for normal-size text. */
        const val AA_NORMAL_TEXT = 4.5f

        /** WCAG 2.1 minimum for a graphical object that carries meaning (1.4.11). */
        const val NON_TEXT_MINIMUM = 3f

        /**
         * What a parent fill is drawn over in the light theme: the surface and background, the
         * weekend base, and `surfaceContainer` (`Theme.kt`'s literal, which the scheme keeps
         * private).
         */
        val LIGHT_SURFACES = listOf(
            "surface" to CoPlanlyColors.LightSurface,
            "background" to CoPlanlyColors.LightBackground,
            "weekend" to CoPlanlyColors.WeekendBackgroundLight,
            "surfaceContainer" to Color(0xFFF0EEF5)
        )

        /** The same four in the dark theme. */
        val DARK_SURFACES = listOf(
            "surface" to CoPlanlyColors.DarkSurface,
            "background" to CoPlanlyColors.DarkBackground,
            "weekend" to CoPlanlyColors.WeekendBackgroundDark,
            "surfaceContainer" to Color(0xFF1F1F25)
        )
    }
}
