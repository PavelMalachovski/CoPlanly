package com.coparently.app.presentation.calendar

import org.junit.Test
import java.time.LocalDate
import kotlin.test.assertEquals

/**
 * Which month cells show whom a day would go to (day swap design, 28 October 2026, boards SW-2 and
 * SW-4): a chosen day in the receiver's colour, and a day waiting for an answer in its lighter form.
 */
class SwapMarkTest {

    private val nov6 = LocalDate.of(2026, 11, 6)
    private val nov7 = LocalDate.of(2026, 11, 7)
    private val nov8 = LocalDate.of(2026, 11, 8)
    private val nov9 = LocalDate.of(2026, 11, 9)

    @Test
    fun `a chosen day is marked for whoever does not hold it`() {
        val marks = swapMarks(
            setOf(nov6, nov7),
            holderOf = { if (it == nov6) "mom" else "dad" },
            pendingTo = emptyMap()
        )
        assertEquals(SwapMark(toSlot = "dad", pending = false), marks[nov6])
        assertEquals(SwapMark(toSlot = "mom", pending = false), marks[nov7])
    }

    @Test
    fun `a chosen day nobody holds is not marked`() {
        assertEquals(emptyMap(), swapMarks(setOf(nov8), holderOf = { null }, pendingTo = emptyMap()))
    }

    @Test
    fun `a waiting day is marked for whom it was offered to`() {
        assertEquals(
            mapOf(nov9 to SwapMark(toSlot = "mom", pending = true)),
            swapMarks(emptySet(), holderOf = { "dad" }, pendingTo = mapOf(nov9 to "mom"))
        )
    }

    @Test
    fun `a waiting day chosen again shows the choice`() {
        val marks = swapMarks(setOf(nov9), holderOf = { "mom" }, pendingTo = mapOf(nov9 to "mom"))
        assertEquals(SwapMark(toSlot = "dad", pending = false), marks[nov9])
    }
}
