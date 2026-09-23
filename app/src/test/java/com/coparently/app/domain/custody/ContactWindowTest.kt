package com.coparently.app.domain.custody

import com.coparently.app.domain.model.CustodyModel
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Contact windows (MON-6b): the wire form, and what they do — and do not do — to a pattern.
 *
 * The property the design rests on is the second half: a window is overlaid on the whole-day
 * pattern and never changes whose day it is, so everything that already asks `getCustodyFor`
 * keeps its answer.
 */
class ContactWindowTest {

    // ---- the wire form ------------------------------------------------------

    @Test
    fun `a window round-trips through its wire string`() {
        assertEquals("2|15:00|19:00|dad", ContactWindowCodec.encode(WEDNESDAY))
        assertEquals(WEDNESDAY, ContactWindowCodec.decode("2|15:00|19:00|dad"))
    }

    @Test
    fun `the canonical list is sorted and de-duplicated, so a re-encode cannot change it`() {
        // `firestore.rules` refuses a proposal or swap write that changes the stored list; two
        // devices writing the same set must therefore produce the same list.
        val later = WEDNESDAY.copy(dayIndex = 9)
        val encoded = ContactWindowCodec.encodeAll(listOf(later, WEDNESDAY, later))

        assertEquals(listOf("2|15:00|19:00|dad", "9|15:00|19:00|dad"), encoded)
        assertEquals(encoded, ContactWindowCodec.encodeAll(ContactWindowCodec.decodeAll(encoded)))
    }

    @Test
    fun `an entry this build cannot read is dropped, never guessed into a window`() {
        val decoded = ContactWindowCodec.decodeAll(
            listOf(
                "2|15:00|19:00|dad",
                "2|19:00|15:00|dad", // ends before it starts
                "2|15:00|19:00|grandma", // not a slot
                "-1|15:00|19:00|mom", // not a cycle day
                "2|15:00|19:00", // a field short
                "2|15:00|19:00|dad|v2", // a future format
                "x|15:00|19:00|dad",
                42 // not even a string
            )
        )

        assertEquals(listOf(WEDNESDAY), decoded)
        assertNull(ContactWindowCodec.decode("2|25:00|26:00|dad"))
        assertEquals(emptyList(), ContactWindowCodec.decodeAll(null))
    }

    @Test
    fun `a window that does not end after it starts cannot be built`() {
        assertFailsWith<IllegalArgumentException> {
            ContactWindow(2, LocalTime.of(19, 0), LocalTime.of(19, 0), "dad")
        }
        assertFailsWith<IllegalArgumentException> {
            ContactWindow(2, LocalTime.of(15, 0), LocalTime.of(19, 0), "friend")
        }
    }

    // ---- on the pattern -----------------------------------------------------

    @Test
    fun `a window repeats with the cycle and changes nobody's day`() {
        val model = everyOtherWeekend().copy(contactWindows = listOf(WEDNESDAY))
        val firstWednesday = START.plusDays(2)

        assertEquals(listOf(WEDNESDAY), model.contactWindowsOn(firstWednesday))
        // A fortnight later: the same cycle day.
        assertEquals(listOf(WEDNESDAY), model.contactWindowsOn(firstWednesday.plusDays(14)))
        // Before the anchor too, like `getCustodyFor`.
        assertEquals(listOf(WEDNESDAY), model.contactWindowsOn(firstWednesday.minusDays(14)))
        // The second week's Wednesday has none of its own.
        assertEquals(emptyList(), model.contactWindowsOn(firstWednesday.plusDays(7)))
        // Whose day it is does not move for an afternoon.
        assertEquals("mom", model.getCustodyFor(firstWednesday))
        assertEquals(everyOtherWeekend().getCustodyFor(firstWednesday), model.getCustodyFor(firstWednesday))
    }

    @Test
    fun `windows on one day come back earliest first`() {
        val morning = WEDNESDAY.copy(start = LocalTime.of(8, 0), end = LocalTime.of(9, 0))
        val model = everyOtherWeekend().copy(contactWindows = listOf(WEDNESDAY, morning))

        assertEquals(listOf(morning, WEDNESDAY), model.contactWindowsOn(START.plusDays(2)))
    }

    @Test
    fun `a pattern with no cycle has no windows rather than dividing by zero`() {
        val broken = everyOtherWeekend().copy(patternDays = 0, contactWindows = listOf(WEDNESDAY))

        assertEquals(emptyList(), broken.contactWindowsOn(START))
    }

    @Test
    fun `complementing a pattern flips its windows with its days`() {
        // After a re-slot the afternoon that was the co-parent's must still be the co-parent's.
        val model = everyOtherWeekend().copy(contactWindows = listOf(WEDNESDAY))

        assertEquals(listOf(WEDNESDAY.copy(parent = "mom")), model.complemented().contactWindows)
        assertEquals(model, model.complemented().complemented())
    }

    @Test
    fun `two patterns that differ only in their windows are not equivalent`() {
        // Equivalence decides whether a pairing conflict is shown at all; one that differed only
        // in the contact afternoons would otherwise be settled silently, dropping one side's.
        val plain = everyOtherWeekend()
        val withWindow = plain.copy(contactWindows = listOf(WEDNESDAY))

        assertFalse(plain.isEquivalentTo(withWindow))
        assertTrue(withWindow.isEquivalentTo(withWindow.copy(id = "other")))
    }

    @Test
    fun `equivalence compares windows by date, not by cycle index`() {
        // The same Wednesday afternoon, written from a start date one week later: every index
        // moves by seven, and the schedule is the same.
        val a = everyOtherWeekend().copy(contactWindows = listOf(WEDNESDAY, WEDNESDAY.copy(dayIndex = 9)))
        val b = CustodyModel.everyOtherWeekend("b", START.plusDays(7))
            .copy(momDayIndices = a.momDayIndices.map { (it + 7) % 14 }.toSet())
            .copy(contactWindows = listOf(WEDNESDAY.copy(dayIndex = 9), WEDNESDAY))

        assertTrue(a.isEquivalentTo(b))
    }

    @Test
    fun `a proposal that only moves an afternoon is not described as changing nothing`() {
        val agreed = everyOtherWeekend()
        val proposed = agreed.copy(contactWindows = listOf(WEDNESDAY))

        val diff = CustodyPatternDiff.of(agreed, proposed, START)

        assertTrue(diff.comparable)
        assertTrue(diff.movedDays.isEmpty())
        assertTrue(diff.contactWindowsChanged)
        assertFalse(diff.identical)
        assertFalse(CustodyPatternDiff.of(proposed, proposed, START).contactWindowsChanged)
    }

    private fun everyOtherWeekend() = CustodyModel.everyOtherWeekend(id = "eow", startDate = START)

    private companion object {
        /** A Monday, so day 2 of the cycle is a Wednesday. */
        val START: LocalDate = LocalDate.of(2026, 8, 3)

        val WEDNESDAY = ContactWindow(2, LocalTime.of(15, 0), LocalTime.of(19, 0), "dad")
    }
}
