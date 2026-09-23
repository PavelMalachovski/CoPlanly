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

    private companion object {
        /** WCAG 2.x AA minimum for normal-size text. */
        const val AA_NORMAL_TEXT = 4.5f
    }
}
