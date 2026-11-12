package com.coparently.app.presentation.calendar.components

import com.coparently.app.domain.custody.SwapSides
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals

/** The compact swap sheet's rows: the days given away first, then the days taken. */
class SwapSheetRowsTest {

    private fun day(n: Int) = LocalDate(2026, 11, n)

    @Test
    fun `giving and taking make two rows with the given days first`() {
        val rows = swapSheetRows(SwapSides(toOther = listOf(day(14), day(13)), toOfferer = listOf(day(21))), me = "mom")
        assertEquals(
            listOf(SwapSheetRow("dad", listOf(day(13), day(14))), SwapSheetRow("mom", listOf(day(21)))),
            rows
        )
    }

    @Test
    fun `only taking makes one row`() {
        val rows = swapSheetRows(SwapSides(toOther = emptyList(), toOfferer = listOf(day(21))), me = "dad")
        assertEquals(listOf(SwapSheetRow("dad", listOf(day(21)))), rows)
    }

    @Test
    fun `only giving makes one row for the co-parent`() {
        val rows = swapSheetRows(SwapSides(toOther = listOf(day(13)), toOfferer = emptyList()), me = "dad")
        assertEquals(listOf(SwapSheetRow("mom", listOf(day(13)))), rows)
    }
}
