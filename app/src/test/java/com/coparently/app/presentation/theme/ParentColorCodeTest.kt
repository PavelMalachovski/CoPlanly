package com.coparently.app.presentation.theme

import org.junit.Test
import kotlin.math.roundToInt
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The stored form of a parent's own colour, and the slider that picks one.
 *
 * `users/{uid}.colorCode` is written by any client of the co-parent's account and read on both
 * phones, so the reader has to survive anything — including an older build's default and text
 * nobody meant as a colour — and both phones have to read the same answer.
 */
class ParentColorCodeTest {

    @Test
    fun `a custom hue round-trips through its stored form`() {
        val stored = ParentColorCode.custom(215)

        assertEquals("hue1|215", stored)
        assertEquals(215, ParentColorCode.customHueOf(stored))
    }

    @Test
    fun `a hue outside one turn is brought back onto the wheel`() {
        assertEquals("hue1|350", ParentColorCode.custom(-10))
        assertEquals("hue1|10", ParentColorCode.custom(370))
    }

    @Test
    fun `a stored value is read whatever case and padding it arrived in`() {
        assertEquals(10, ParentColorCode.customHueOf(" HUE1|10 "))
    }

    @Test
    fun `anything that is not a custom hue reads as none`() {
        listOf(
            null,
            "",
            "hue1|",
            "hue1|abc",
            "hue1|360",
            "hue1|-1",
            "hue1|1.5",
            "hue1|0010",
            "hue2|10",
            "10",
            "#E91E63",
            // The default older builds wrote for "not chosen" must stay "not chosen".
            "#FF4081",
            "hue1|" + "1".repeat(10_000)
        ).forEach { stored ->
            assertNull(ParentColorCode.customHueOf(stored), "read \"${stored?.take(20)}\" as a hue")
        }
    }

    @Test
    fun `the slider never lands on the calendar friend's teal`() {
        (0..SLIDER_STEPS).forEach { step ->
            val hue = CustomHueRange.hueAt(step / SLIDER_STEPS.toFloat())
            assertFalse(hue in CustomHueRange.RESERVED, "position $step/$SLIDER_STEPS gave $hue")
        }
    }

    @Test
    fun `the slider runs the whole wheel from end to end`() {
        assertEquals(0, CustomHueRange.hueAt(0f))
        assertEquals(359, CustomHueRange.hueAt(1f))
        // Out-of-range positions are clamped rather than wrapping round to the other end.
        assertEquals(0, CustomHueRange.hueAt(-0.5f))
        assertEquals(359, CustomHueRange.hueAt(1.5f))
    }

    @Test
    fun `the slider never moves backwards along the wheel`() {
        val hues = (0..SLIDER_STEPS).map { CustomHueRange.hueAt(it / SLIDER_STEPS.toFloat()) }

        assertEquals(hues.sorted(), hues)
    }

    @Test
    fun `a hue's slider position leads back to the same hue`() {
        (0 until FULL_TURN).filterNot { it in CustomHueRange.RESERVED }.forEach { hue ->
            assertEquals(hue, CustomHueRange.hueAt(CustomHueRange.positionOf(hue)), "hue $hue")
        }
    }

    @Test
    fun `the reserved band is the calendar friend's teal`() {
        // Teal identifies a calendar friend and never a parent (CLAUDE.md, item 16); the band
        // exists for that one colour, so it must actually contain it, in both of its tones.
        listOf(CoPlanlyColors.FriendTeal, CoPlanlyColors.FriendTealLight).forEach { teal ->
            val hue = ParentToneMath.hueOf(teal.argb()).roundToInt()
            assertTrue(hue in CustomHueRange.RESERVED, "teal at $hue° is outside ${CustomHueRange.RESERVED}")
        }
    }

    @Test
    fun `a reserved hue is moved to the nearer edge of the band`() {
        val band = CustomHueRange.RESERVED

        assertEquals(band.first - 1, CustomHueRange.allowed(band.first))
        assertEquals(band.last + 1, CustomHueRange.allowed(band.last))
        assertEquals(100, CustomHueRange.allowed(100))
        assertEquals(10, CustomHueRange.allowed(370))
        assertTrue(CustomHueRange.positionOf(band.first + 2) in 0f..1f)
    }

    private companion object {
        const val SLIDER_STEPS = 1_000
        const val FULL_TURN = 360
    }
}
