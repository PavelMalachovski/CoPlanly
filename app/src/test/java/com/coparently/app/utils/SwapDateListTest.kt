package com.coparently.app.utils

import org.junit.Test
import java.time.LocalDate
import java.util.Locale
import kotlin.test.assertEquals

/** The one wording of a list of swapped days, for the bar, the sheet's chips, the push and the card. */
class SwapDateListTest {

    private val nov6 = LocalDate.of(2026, 11, 6)
    private val nov7 = LocalDate.of(2026, 11, 7)

    @Test
    fun `dates are listed in the reader's language, comma separated`() {
        val format = localizedDate("EEEdMMM", Locale.GERMAN)
        assertEquals("${nov6.format(format)}, ${nov7.format(format)}", swapDateList(listOf(nov6, nov7), Locale.GERMAN))
    }

    @Test
    fun `no dates is no text`() {
        assertEquals("", swapDateList(emptyList(), Locale.US))
    }
}
