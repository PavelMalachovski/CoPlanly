package com.coparently.app.presentation.calendar

import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth
import kotlin.test.assertEquals

/**
 * What a tap on a month cell does (day swap design, 28 October 2026). The owner's report: while
 * choosing days, a tap on a day of the next month opened Day view instead of choosing it.
 */
class SwapTapTest {

    private val november = YearMonth.of(2026, 11)
    private val nov6 = LocalDate.of(2026, 11, 6)
    private val dec2 = LocalDate.of(2026, 12, 2)
    private val oct31 = LocalDate.of(2026, 10, 31)

    @Test
    fun `while choosing, a day of the month on screen is chosen or unchosen`() {
        assertEquals(SwapTap.Toggle(nov6), swapTapFor(nov6, november, selecting = true))
    }

    @Test
    fun `while choosing, a day borrowed from another month pages there and is chosen`() {
        assertEquals(
            SwapTap.PageAndToggle(YearMonth.of(2026, 12), dec2),
            swapTapFor(dec2, november, selecting = true)
        )
        assertEquals(
            SwapTap.PageAndToggle(YearMonth.of(2026, 10), oct31),
            swapTapFor(oct31, november, selecting = true)
        )
    }

    @Test
    fun `not choosing, any day opens Day view`() {
        assertEquals(SwapTap.OpenDay(nov6), swapTapFor(nov6, november, selecting = false))
        assertEquals(SwapTap.OpenDay(dec2), swapTapFor(dec2, november, selecting = false))
    }

    @Test
    fun `a selection across two months survives being saved`() {
        val saved = LocalDateSetSaver.save(setOf(LocalDate.of(2026, 11, 30), dec2))
        assertEquals(setOf(LocalDate.of(2026, 11, 30), dec2), LocalDateSetSaver.restore(saved))
        assertEquals(emptySet(), LocalDateSetSaver.restore(listOf("not a date")))
    }
}
