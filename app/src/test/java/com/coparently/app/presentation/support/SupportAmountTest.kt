package com.coparently.app.presentation.support

import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * The child support amount a parent types (`SupportViewModel.parseAmount`), in minor units since
 * MON-24: it accepts exactly what it accepted as a `BigDecimal` — a number above zero written with
 * at most two places — so "12.300", whose value fits two places but whose text has three, is still
 * refused.
 */
class SupportAmountTest {

    @Test
    fun `a typed amount is its cents`() {
        assertEquals(500_000L, SupportViewModel.parseAmount("5000"))
        assertEquals(500_050L, SupportViewModel.parseAmount("5 000,50"))
        assertEquals(1_250L, SupportViewModel.parseAmount(" 12.5 "))
        assertEquals(100_000L, SupportViewModel.parseAmount("1e3"))
        assertEquals(1L, SupportViewModel.parseAmount("0.01"))
    }

    @Test
    fun `more than two places, nothing, or no more than zero is refused`() {
        assertNull(SupportViewModel.parseAmount("12.345"))
        assertNull(SupportViewModel.parseAmount("12.300"))
        assertNull(SupportViewModel.parseAmount("1E-3"))
        assertNull(SupportViewModel.parseAmount("0"))
        assertNull(SupportViewModel.parseAmount("-5"))
        assertNull(SupportViewModel.parseAmount(""))
        assertNull(SupportViewModel.parseAmount("abc"))
    }
}
