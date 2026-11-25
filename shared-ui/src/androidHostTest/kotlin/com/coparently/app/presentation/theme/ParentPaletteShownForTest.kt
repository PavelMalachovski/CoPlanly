package com.coparently.app.presentation.theme

import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

/**
 * [ParentPalette.shownFor]: what this parent is *drawn* in, so the colour picker can say when it
 * is not the colour they chose. It must agree with [ParentPalette.of], which is what the calendar
 * reads.
 */
class ParentPaletteShownForTest {

    private val blue = ParentColorChoice.BLUE.storedCode

    @Test
    fun `both blue and me in slot 2 shows me in another colour`() {
        val shown = ParentPalette.shownFor("dad", myCode = blue, otherCode = blue)

        assertNotEquals(ParentColorChoice.BLUE.color, shown)
        assertEquals(ParentPalette.of(blue, blue).of("dad"), shown)
    }

    @Test
    fun `both blue and me in slot 1 keeps my colour`() {
        assertEquals(
            ParentColorChoice.BLUE.color,
            ParentPalette.shownFor("mom", myCode = blue, otherCode = blue)
        )
    }

    @Test
    fun `hues ten degrees apart read as identical`() {
        val mine = ParentColorCode.custom(210)
        val theirs = ParentColorCode.custom(220)

        assertEquals(
            ParentPalette.shownFor("dad", mine, mine),
            ParentPalette.shownFor("dad", mine, theirs)
        )
        assertNotEquals(ParentColor.fromStored(mine), ParentPalette.shownFor("dad", mine, theirs))
    }

    @Test
    fun `different colours are shown as chosen`() {
        val pink = ParentColorChoice.PINK
        assertEquals(pink.color, ParentPalette.shownFor("dad", pink.storedCode, blue))
        assertEquals(pink.color, ParentPalette.shownFor("mom", pink.storedCode, blue))
    }

    @Test
    fun `a co-parent who has not chosen never moves a pick`() {
        val pink = ParentColorChoice.PINK.storedCode
        assertEquals(ParentColorChoice.PINK.color, ParentPalette.shownFor("mom", pink, null))
        assertEquals(ParentColorChoice.BLUE.color, ParentPalette.shownFor("dad", blue, null))
    }
}
