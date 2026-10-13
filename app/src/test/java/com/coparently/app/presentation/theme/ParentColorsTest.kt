package com.coparently.app.presentation.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The contrast decisions behind a parent colour, wherever it is drawn (UX-15, October 2026).
 *
 * Once a parent can choose their colour — any hue on the wheel since the custom colour — "white
 * text on the parent's fill" stops being a decision that was checked once for pink and blue:
 * every choice has to clear AA, and the text colour has to be picked rather than assumed. And
 * since the custody wash got brighter (the owner's "bright colours", October 2026), what sits *on*
 * the wash — the day number, a holiday's red number, an event block's title, the colour's own
 * text — has to be held to AA on it too, for every preset and every hue a parent can pick.
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
    fun `every colour's chip tone carries a label at AA with the colour onFill picks`() {
        everyColour.forEach { colour ->
            val label = ParentColors.onFill(colour.dark)
            val ratio = ParentColors.contrastRatio(label, colour.dark)

            assertTrue(ratio >= AA_NORMAL_TEXT, "${colour.storedCode}: $ratio")
        }
    }

    @Test
    fun `onFill picks black on a light background and white on a dark one`() {
        assertEquals(Color.Black, ParentColors.onFill(Color.White))
        assertEquals(Color.Black, ParentColors.onFill(ParentColorChoice.PINK.color.light))
        assertEquals(Color.White, ParentColors.onFill(Color.Black))
        assertEquals(Color.White, ParentColors.onFill(ParentColorChoice.BLUE.color.dark))
    }

    @Test
    fun `every colour's fill is a visible graphic on every surface it is drawn on, in both themes`() {
        // WCAG 1.4.11: a dot or bar that identifies a parent needs 3:1 against what is under it.
        // Purple was 2.09:1 on the dark surface and 1.74:1 on the dark weekend grey before it got
        // a dark-theme fill (docs/AUDIT-2026-10-design.md D-14). A month cell's event dot sits in
        // a ring of the cell's own base (MonthView), so this is also what the dot is measured on.
        everyColour.forEach { colour ->
            surfaces(dark = false).forEach { (name, surface) ->
                val ratio = ParentColors.contrastRatio(colour.fillIn(darkTheme = false), surface)
                assertTrue(ratio >= NON_TEXT_MINIMUM, "${colour.storedCode} on light $name: $ratio")
            }
            surfaces(dark = true).forEach { (name, surface) ->
                val ratio = ParentColors.contrastRatio(colour.fillIn(darkTheme = true), surface)
                assertTrue(ratio >= NON_TEXT_MINIMUM, "${colour.storedCode} on dark $name: $ratio")
            }
        }
    }

    @Test
    fun `every colour's text tone reads at AA on every surface of its theme`() {
        everyColour.forEach { colour ->
            listOf(false, true).forEach { dark ->
                surfaces(dark).forEach { (name, surface) ->
                    val ratio = ParentColors.contrastRatio(colour.textIn(dark), surface)
                    assertTrue(ratio >= AA_NORMAL_TEXT, "${colour.storedCode} text on ${theme(dark)} $name: $ratio")
                }
            }
        }
    }

    @Test
    fun `the custody wash is stronger in the dark theme, and a proposal is weaker than an agreed day`() {
        // The owner's "bright" (October 2026): 0.14 in both themes read as a murky maroon on the
        // dark surface. A proposal must stay visibly less than the thing it only asks for.
        listOf(false, true).forEach { dark ->
            assertTrue(CoPlanlyColors.proposalTintAlpha(dark) < CoPlanlyColors.custodyTintAlpha(dark))
        }
        assertTrue(CoPlanlyColors.custodyTintAlpha(darkTheme = true) > CoPlanlyColors.custodyTintAlpha(darkTheme = false))
    }

    @Test
    fun `a day number reads at AA on every colour's custody wash`() {
        forEveryWash { colour, dark, base, wash ->
            val ratio = ParentColors.contrastRatio(onSurface(dark), wash)
            assertTrue(ratio >= AA_NORMAL_TEXT, "${colour.storedCode} ${theme(dark)} $base: $ratio")
        }
    }

    @Test
    fun `a holiday's red number reads at AA on every colour's custody wash`() {
        // Custody wins over the holiday tint, so on a holiday it is the wash the red number sits on.
        forEveryWash { colour, dark, base, wash ->
            val ratio = ParentColors.contrastRatio(holidayRed(dark), wash)
            assertTrue(ratio >= AA_NORMAL_TEXT, "${colour.storedCode} ${theme(dark)} $base: $ratio")
        }
    }

    @Test
    fun `a colour's own text reads at AA on its own wash`() {
        // A custody preview cell and the custody setup grid write the parent's text tone on the
        // parent's own wash.
        forEveryWash { colour, dark, base, wash ->
            val ratio = ParentColors.contrastRatio(colour.textIn(dark), wash)
            assertTrue(ratio >= AA_NORMAL_TEXT, "${colour.storedCode} ${theme(dark)} $base: $ratio")
        }
    }

    @Test
    fun `an event block's title reads at AA over any parent's day`() {
        // Week and Day view: a block tinted in its owner's colour, laid over a column washed in
        // whoever's day it is, carries the title in the theme's onSurface.
        forEveryWash { colour, dark, base, wash ->
            presetColours.forEach { owner ->
                val block = owner.washIn(dark)
                    .copy(alpha = CoPlanlyColors.EVENT_BLOCK_TINT_ALPHA)
                    .compositeOver(wash)
                val ratio = ParentColors.contrastRatio(onSurface(dark), block)
                val where = "${owner.storedCode} on ${colour.storedCode} ${theme(dark)} $base"
                assertTrue(ratio >= AA_NORMAL_TEXT, "$where: $ratio")
            }
        }
    }

    @Test
    fun `a day number reads at AA under a pending proposal`() {
        forEveryWash { colour, dark, base, wash ->
            presetColours.forEach { proposed ->
                val previewed = proposed.washIn(dark)
                    .copy(alpha = CoPlanlyColors.proposalTintAlpha(dark))
                    .compositeOver(wash)
                val ratio = ParentColors.contrastRatio(onSurface(dark), previewed)
                val where = "${proposed.storedCode} over ${colour.storedCode} ${theme(dark)} $base"
                assertTrue(ratio >= AA_NORMAL_TEXT, "$where: $ratio")
            }
        }
    }

    @Test
    fun `a holiday's day number reads at AA on the plain surface and the weekend grey`() {
        surfaces(dark = false).filter { (name, _) -> name != "surfaceContainer" }.forEach { (name, surface) ->
            val ratio = ParentColors.contrastRatio(CoPlanlyColors.HolidayRed, surface)
            assertTrue(ratio >= AA_NORMAL_TEXT, "light $name: $ratio")
        }
        surfaces(dark = true).forEach { (name, surface) ->
            val ratio = ParentColors.contrastRatio(CoPlanlyColors.HolidayRedDark, surface)
            assertTrue(ratio >= AA_NORMAL_TEXT, "dark $name: $ratio")
        }
    }

    /** Calls [check] with every colour's custody wash, over both day bases, in both themes. */
    private fun forEveryWash(check: (colour: ParentColor, dark: Boolean, base: String, wash: Color) -> Unit) {
        everyColour.forEach { colour ->
            listOf(false, true).forEach { dark ->
                dayBases(dark).forEach { (name, base) ->
                    val wash = colour.washIn(dark)
                        .copy(alpha = CoPlanlyColors.custodyTintAlpha(dark))
                        .compositeOver(base)
                    check(colour, dark, name, wash)
                }
            }
        }
    }

    private companion object {
        /** WCAG 2.x AA minimum for normal-size text. */
        const val AA_NORMAL_TEXT = 4.5f

        /** WCAG 2.1 minimum for a graphical object that carries meaning (1.4.11). */
        const val NON_TEXT_MINIMUM = 3f

        val presetColours = ParentColorChoice.entries.map { it.color }

        /** The four presets and every hue a parent can pick on the wheel. */
        val everyColour = presetColours + (0 until 360).map { ParentColor.custom(it) }

        fun theme(dark: Boolean) = if (dark) "dark" else "light"

        fun onSurface(dark: Boolean) = if (dark) CoPlanlyColors.DarkOnSurface else CoPlanlyColors.LightOnSurface

        fun holidayRed(dark: Boolean) = if (dark) CoPlanlyColors.HolidayRedDark else CoPlanlyColors.HolidayRed

        /**
         * What a parent fill is drawn over: the surface and background, the weekend base, and
         * `surfaceContainer` (`Theme.kt`'s literal, which the scheme keeps private).
         */
        fun surfaces(dark: Boolean) = if (dark) {
            listOf(
                "surface" to CoPlanlyColors.DarkSurface,
                "background" to CoPlanlyColors.DarkBackground,
                "weekend" to CoPlanlyColors.WeekendBackgroundDark,
                "surfaceContainer" to Color(0xFF1F1F25)
            )
        } else {
            listOf(
                "surface" to CoPlanlyColors.LightSurface,
                "background" to CoPlanlyColors.LightBackground,
                "weekend" to CoPlanlyColors.WeekendBackgroundLight,
                "surfaceContainer" to Color(0xFFF0EEF5)
            )
        }

        /** The two bases a calendar day is drawn on before its custody wash (`DayCellFills`). */
        fun dayBases(dark: Boolean) = surfaces(dark).filter { (name, _) -> name == "surface" || name == "weekend" }
    }
}
