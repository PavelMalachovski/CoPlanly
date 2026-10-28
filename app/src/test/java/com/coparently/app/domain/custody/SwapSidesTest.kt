package com.coparently.app.domain.custody

import org.junit.Test
import java.time.LocalDate
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Which way each day of a swap goes, decided once for the bar, the sheet, the grid, the push and
 * the answer card (day swap design, 28 October 2026).
 */
class SwapSidesTest {

    private val nov6 = LocalDate.of(2026, 11, 6)
    private val nov7 = LocalDate.of(2026, 11, 7)
    private val nov8 = LocalDate.of(2026, 11, 8)
    private val nov14 = LocalDate.of(2026, 11, 14)
    private val nov30 = LocalDate.of(2026, 11, 30)
    private val dec2 = LocalDate.of(2026, 12, 2)

    @Test
    fun `a day the offerer holds goes to the other parent and theirs comes back`() {
        val sides = SwapSides.of(
            listOf(nov14, nov7, nov6),
            holderOf = { if (it == nov14) "dad" else "mom" },
            offererSlot = "mom"
        )
        assertEquals(listOf(nov6, nov7), sides.toOther)
        assertEquals(listOf(nov14), sides.toOfferer)
        assertTrue(sides.unknown.isEmpty())
    }

    @Test
    fun unknownDaysAreLeftOut() {
        val sides = SwapSides.of(
            listOf(nov6, nov8),
            holderOf = { if (it == nov8) null else "mom" },
            offererSlot = "mom"
        )
        assertEquals(listOf(nov8), sides.unknown)
        assertEquals(listOf(nov6), sides.offerable)
    }

    @Test
    fun `two months in one selection stay sorted`() {
        val sides = SwapSides.of(listOf(dec2, nov30, dec2), holderOf = { "dad" }, offererSlot = "dad")
        assertEquals(listOf(nov30, dec2), sides.toOther)
    }

    @Test
    fun `an offer read back splits by whom each day goes to`() {
        val group = DaySwapGroup(
            listOf(
                DaySwap(nov6, override(toParent = "dad")),
                DaySwap(nov14, override(toParent = "mom"))
            )
        )
        val sides = SwapSides.ofOffer(group, offererSlot = "mom")
        assertEquals(listOf(nov6), sides.toOther)
        assertEquals(listOf(nov14), sides.toOfferer)
    }

    @Test
    fun `a day goes to whoever does not hold it`() {
        assertEquals("dad", receiverOf("mom"))
        assertEquals("mom", receiverOf("dad"))
        assertNull(receiverOf(null))
        assertNull(receiverOf("friend"))
    }

    @Test
    fun `nothing chosen is empty`() {
        assertTrue(SwapSides.of(emptyList(), holderOf = { "mom" }, offererSlot = "mom").isEmpty)
    }

    private fun override(toParent: String) = DayOverride(
        toParent = toParent,
        requestedBy = "uid-mom",
        requestedAt = "2026-10-28T10:00:00"
    )
}
