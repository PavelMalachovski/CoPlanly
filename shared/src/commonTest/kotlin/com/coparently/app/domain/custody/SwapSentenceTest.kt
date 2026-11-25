package com.coparently.app.domain.custody

import com.coparently.app.domain.model.ChildInfo
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * A day swap phrased by who has the child on which days (meeting of 25 November 2026, item 3): the
 * sentence's shape, the children it names and the push field that carries their names.
 */
class SwapSentenceTest {

    private val nov13 = LocalDate(2026, 11, 13)
    private val nov14 = LocalDate(2026, 11, 14)
    private val nov20 = LocalDate(2026, 11, 20)

    private val createdAt = LocalDateTime(2026, 1, 1, 9, 0)

    private fun child(id: String, name: String) =
        ChildInfo(id = id, childName = name, dateOfBirth = null, createdAt = createdAt, updatedAt = createdAt)

    private fun override(childId: String) = ChildScheduleOverride(
        childId = childId,
        patternDays = 14,
        momDayIndices = setOf(0, 1),
        startDate = nov13
    )

    @Test
    fun `days each way read as both`() {
        val sentence = SwapSentence(listOf("Lenička"), toYou = listOf(nov13, nov14), toSender = listOf(nov20))
        assertEquals(SwapSentenceShape.BOTH, sentence.shape)
    }

    @Test
    fun `days to the reader alone read as gives`() {
        assertEquals(SwapSentenceShape.GIVES, SwapSentence(emptyList(), listOf(nov13), emptyList()).shape)
    }

    @Test
    fun `days to the sender alone read as asks`() {
        assertEquals(SwapSentenceShape.ASKS, SwapSentence(listOf("Sašík"), emptyList(), listOf(nov20)).shape)
    }

    @Test
    fun `an offer is read from the readers side`() {
        val sentence = SwapSentence.of(SwapSides(toOther = listOf(nov13), toOfferer = listOf(nov20)), listOf("Lenička"))
        assertEquals(listOf(nov13), sentence?.toYou)
        assertEquals(listOf(nov20), sentence?.toSender)
        assertEquals(listOf("Lenička"), sentence?.children)
    }

    @Test
    fun `an offer with no day names no sentence`() {
        assertNull(SwapSentence.of(SwapSides(emptyList(), emptyList()), listOf("Lenička")))
    }

    @Test
    fun `swapChildren keeps first names in order and drops a child with a schedule of their own`() {
        val children = listOf(child("a", "Lenička Nováková"), child("b", "Sašík"), child("c", "  Ema  Marie "))
        assertEquals(listOf("Lenička", "Ema"), swapChildren(children, listOf(override("b"))))
    }

    @Test
    fun `swapChildren is empty when every child has a schedule of their own`() {
        val children = listOf(child("a", "Lenička"), child("b", "Sašík"))
        assertTrue(swapChildren(children, listOf(override("a"), override("b"))).isEmpty())
    }

    @Test
    fun `swapChildren leaves out a child with no name`() {
        assertEquals(listOf("Sašík"), swapChildren(listOf(child("a", "  "), child("b", "Sašík")), emptyList()))
    }

    @Test
    fun `the push field joins the names by a line break`() {
        assertEquals("Lenička\nSašík", swapChildNamesField(listOf(" Lenička ", "Sašík")))
    }

    @Test
    fun `the push field is null with no name`() {
        assertNull(swapChildNamesField(emptyList()))
        assertNull(swapChildNamesField(listOf(" ", "")))
    }

    @Test
    fun `the push field is cut on a name boundary at its bound`() {
        val long = "x".repeat(150)
        val other = "y".repeat(60)
        assertEquals(long, swapChildNamesField(listOf(long, other)))
        val exact = "a".repeat(100) + "\n" + "b".repeat(99)
        assertEquals(exact, swapChildNamesField(listOf("a".repeat(100), "b".repeat(99), "c")))
    }

    @Test
    fun `a single name longer than the bound is cut to it`() {
        assertEquals("z".repeat(SWAP_CHILD_NAMES_MAX_LENGTH), swapChildNamesField(listOf("z".repeat(250))))
    }
}
