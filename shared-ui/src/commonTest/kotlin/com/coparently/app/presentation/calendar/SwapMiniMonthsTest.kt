package com.coparently.app.presentation.calendar

import kotlinx.datetime.LocalDate
import kotlinx.datetime.YearMonth
import kotlin.test.Test
import kotlin.test.assertEquals

/** The mini months of a day swap (21 October 2026, Zuzana's test). */
class SwapMiniMonthsTest {

    @Test
    fun `one month per month the days fall in - each day ringed in the colour of whom it goes to`() {
        val give = LocalDate(2026, 10, 30)
        val take = LocalDate(2026, 11, 2)
        val agreed: (LocalDate) -> String? = { date -> if (date == give) "mom" else "dad" }

        val months = swapMiniMonths(mapOf(give to "dad", take to "mom"), emptyList(), agreed)

        assertEquals(listOf(YearMonth(2026, 10), YearMonth(2026, 11)), months.map { it.month })
        val october = months.first()
        assertEquals("dad", october.custody[give])
        assertEquals(mapOf(give to MiniMonthMark.SWAP_DAY), october.marks)
        // A day the offer does not touch keeps the agreed schedule.
        assertEquals("dad", october.custody[LocalDate(2026, 10, 29)])
        assertEquals("mom", months[1].custody[take])
    }
}
