package com.coparently.app.presentation.calendar

import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth
import kotlin.test.assertEquals

/** The mini months of a day swap (21 October 2026, Zuzana's test). */
class SwapMiniMonthsTest {

    @Test
    fun `one month per month the days fall in, each day ringed in the colour of whom it goes to`() {
        val give = LocalDate.of(2026, 10, 30)
        val take = LocalDate.of(2026, 11, 2)
        val agreed: (LocalDate) -> String? = { date -> if (date == give) "mom" else "dad" }

        val months = swapMiniMonths(mapOf(give to "dad", take to "mom"), emptyList(), agreed)

        assertEquals(listOf(YearMonth.of(2026, 10), YearMonth.of(2026, 11)), months.map { it.month })
        val october = months.first()
        assertEquals("dad", october.custody[give])
        assertEquals(mapOf(give to MiniMonthMark.SWAP_DAY), october.marks)
        // A day the offer does not touch keeps the agreed schedule.
        assertEquals("dad", october.custody[LocalDate.of(2026, 10, 29)])
        assertEquals("mom", months[1].custody[take])
    }
}
