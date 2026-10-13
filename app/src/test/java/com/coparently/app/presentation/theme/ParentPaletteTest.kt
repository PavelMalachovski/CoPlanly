package com.coparently.app.presentation.theme

import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The parent colour, now that it is chosen rather than derived from the slot — from four presets
 * or, since October 2026, anywhere on the wheel.
 *
 * The case that matters most is the collision: the entire purpose of a parent colour is telling
 * the two apart, so a family where both picked blue — or two blues a few degrees apart — must not
 * end up with a calendar in one hue.
 */
class ParentPaletteTest {

    @Test
    fun `an account that has not chosen keeps the colours the app always had`() {
        // An upgrade must change nothing until somebody actually picks something.
        val palette = ParentPalette.of(slot1Code = null, slot2Code = null)

        assertEquals(ParentColorChoice.PINK.color, palette.of("mom"))
        assertEquals(ParentColorChoice.BLUE.color, palette.of("dad"))
    }

    @Test
    fun `the default older builds stored for nobody's choice still reads as nobody's choice`() {
        // Every profile an older build saved carries "#FF4081" whether or not the parent chose.
        // Reading it as a chosen pink would turn every slot-2 parent who never chose pink.
        assertEquals(ParentPalette.Default, ParentPalette.of(LEGACY_DEFAULT, LEGACY_DEFAULT))
        assertNull(ParentColor.fromStored(LEGACY_DEFAULT))
    }

    @Test
    fun `each parent's own choice is what gets drawn`() {
        val palette = ParentPalette.of(
            slot1Code = ParentColorChoice.ORANGE.storedCode,
            slot2Code = ParentColorChoice.PURPLE.storedCode
        )

        assertEquals(ParentColorChoice.ORANGE.color, palette.of("mom"))
        assertEquals(ParentColorChoice.PURPLE.color, palette.of("dad"))
    }

    @Test
    fun `a colour picked on the wheel is drawn in the hue that was picked`() {
        val palette = ParentPalette.of(slot1Code = ParentColorCode.custom(120), slot2Code = null)

        val drawn = palette.of("mom")
        assertEquals(120f, drawn.hue)
        assertNull(drawn.preset)
        assertEquals("hue1|120", drawn.storedCode)
    }

    @Test
    fun `two parents who chose the same colour are still drawn apart`() {
        // Two men both picking blue is the case this exists for — and so is one parent picking
        // the colour the other already had, which needs no coordination between two phones.
        val palette = ParentPalette.of(
            slot1Code = ParentColorChoice.BLUE.storedCode,
            slot2Code = ParentColorChoice.BLUE.storedCode
        )

        assertEquals(ParentColorChoice.BLUE.color, palette.of("mom"))
        assertEquals(ParentColorChoice.PINK.color, palette.of("dad"), "slot 2 moves to a free colour")
    }

    @Test
    fun `two custom hues a few degrees apart count as the same colour`() {
        // A sky blue and a slightly deeper sky blue are one colour to a parent glancing at a month.
        val palette = ParentPalette.of(ParentColorCode.custom(210), ParentColorCode.custom(222))

        assertEquals(210f, palette.of("mom").hue)
        assertEquals(ParentColorChoice.PINK.color, palette.of("dad"))
    }

    @Test
    fun `a custom hue beside a preset counts as that preset`() {
        val palette = ParentPalette.of(ParentColorChoice.PINK.storedCode, ParentColorCode.custom(350))

        assertEquals(ParentColorChoice.PINK.color, palette.of("mom"))
        assertEquals(ParentColorChoice.BLUE.color, palette.of("dad"), "the first preset clear of pink")
    }

    @Test
    fun `no two presets are close enough to collide`() {
        // Pink and orange are the nearest pair (about 35 degrees apart); a family choosing them
        // must see both.
        val presets = ParentColorChoice.entries.map { it.color }
        presets.forEach { a ->
            presets.filter { it != a }.forEach { b -> assertFalse(a.closeTo(b), "${a.preset} and ${b.preset}") }
        }
    }

    @Test
    fun `the collision is resolved the same way on both phones`() {
        // Slot 1 wins rather than "whoever chose first": `colorCode` carries no timestamp, and
        // an order invented from two independently-syncing documents would pick a different
        // winner on each device — which is two parents seeing the calendar differently.
        val onePhone = ParentPalette.of(ParentColorCode.custom(30), ParentColorCode.custom(40))
        val theOther = ParentPalette.of(ParentColorCode.custom(30), ParentColorCode.custom(40))

        assertEquals(onePhone, theOther)
    }

    @Test
    fun `a collision does not overwrite what the parent actually chose`() {
        // Only the drawing moves. Their setting still says purple, so it stops being overridden
        // the moment the other parent changes theirs.
        val stored = ParentColorChoice.PURPLE.storedCode
        val palette = ParentPalette.of(stored, stored)

        assertEquals(ParentColorChoice.PURPLE, ParentColorChoice.fromStored(stored))
        assertEquals(ParentColorChoice.PURPLE.color, palette.of("mom"))
    }

    @Test
    fun `a hue stored inside the calendar friend's band is drawn outside it`() {
        // Only this app's picker writes a custom hue, and it never offers the band — but the
        // field is the co-parent's to write, so the reader holds the rule too.
        val drawn = assertNotNull(ParentColor.fromStored("hue1|173"))

        assertFalse(drawn.hue.toInt() in CustomHueRange.RESERVED, "drawn at ${drawn.hue}")
    }

    @Test
    fun `a stored code is read whatever case it was written in`() {
        assertEquals(
            ParentColorChoice.PINK,
            ParentColorChoice.fromStored(ParentColorChoice.PINK.storedCode.lowercase())
        )
    }

    @Test
    fun `an unrecognised code is not silently mapped onto the default`() {
        // Null is the honest answer, and the caller decides the fallback. Quietly reading an
        // unknown colour as pink would make a co-parent's deliberate choice look like nobody's.
        assertNull(ParentColorChoice.fromStored("#123456"))
        assertNull(ParentColorChoice.fromStored(""))
        assertNull(ParentColorChoice.fromStored(null))
        assertNull(ParentColor.fromStored("#123456"))
    }

    @Test
    fun `every choice has a distinct stored code`() {
        // The code is a stored value two devices compare; a duplicate would make two colours
        // indistinguishable on the wire.
        val codes = ParentColorChoice.entries.map { it.storedCode.uppercase() }

        assertEquals(codes.size, codes.toSet().size)
    }

    @Test
    fun `the stored codes are the ones two phones already compare`() {
        // The tones moved (October 2026, "bright"); the identifiers on the wire must not, or every
        // parent who chose would read as having chosen nothing.
        assertEquals(
            listOf("#E91E63", "#1976D2", "#7B1FA2", "#D84315"),
            ParentColorChoice.entries.map { it.storedCode }
        )
    }

    @Test
    fun `pink and blue are the theme's parent tokens`() {
        assertEquals(CoPlanlyColors.MomPink, ParentColorChoice.PINK.color.fill)
        assertEquals(CoPlanlyColors.MomPinkLight, ParentColorChoice.PINK.color.light)
        assertEquals(CoPlanlyColors.MomPinkDark, ParentColorChoice.PINK.color.dark)
        assertEquals(CoPlanlyColors.DadBlue, ParentColorChoice.BLUE.color.fill)
        assertEquals(CoPlanlyColors.DadBlueLight, ParentColorChoice.BLUE.color.light)
        assertEquals(CoPlanlyColors.DadBlueDark, ParentColorChoice.BLUE.color.dark)
    }

    @Test
    fun `a dark-theme wash is never brighter than its ceiling`() {
        val colours = ParentColorChoice.entries.map { it.color } + (0 until 360).map { ParentColor.custom(it) }

        colours.forEach { colour ->
            val luminance = ParentToneMath.luminance(colour.darkWash.argb())
            assertTrue(luminance <= ParentToneMath.DARK_WASH_MAX_LUMINANCE + 0.005, "${colour.storedCode}: $luminance")
        }
    }

    private companion object {
        const val LEGACY_DEFAULT = "#FF4081"
    }
}
