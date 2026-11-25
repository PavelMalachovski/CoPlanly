package com.coparently.app.presentation.settings

import com.coparently.app.presentation.theme.ParentColor
import com.coparently.app.presentation.theme.ParentColorChoice
import com.coparently.app.presentation.theme.ParentColorCode
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull

/** When the picker's notice appears and what it names. */
class ColourClashTest {

    private fun coParent(code: String?, name: String = "Zuzana Nováková", mySlot: String = "dad") =
        CoParentColour(name = name, slot = if (mySlot == "dad") "mom" else "dad", colorCode = code, mySlot = mySlot)

    @Test
    fun `the same preset names both colours and takes the first name only`() {
        val clash = coParent(ParentColorChoice.BLUE.storedCode).clashWith(ParentColorChoice.BLUE.color)

        assertIs<ColourClash.Same>(clash)
        assertEquals("Zuzana", clash.firstName)
        assertEquals(ParentColorChoice.BLUE, clash.theirs)
    }

    @Test
    fun `no notice while the calendar draws the choice as it is`() {
        assertNull(coParent(ParentColorChoice.BLUE.storedCode).clashWith(ParentColorChoice.PINK.color))
        assertNull(
            coParent(ParentColorChoice.BLUE.storedCode, mySlot = "mom").clashWith(ParentColorChoice.BLUE.color)
        )
    }

    @Test
    fun `a colour from the wheel close to theirs gets the unnamed notice`() {
        val clash = coParent(ParentColorCode.custom(215)).clashWith(ParentColor.custom(210))

        assertIs<ColourClash.Close>(clash)
    }

    @Test
    fun `a co-parent with a colour of their own names no swatch`() {
        assertNull(coParent(ParentColorCode.custom(215)).preset)
        assertEquals(ParentColorChoice.BLUE, coParent(ParentColorChoice.BLUE.storedCode).preset)
    }
}
