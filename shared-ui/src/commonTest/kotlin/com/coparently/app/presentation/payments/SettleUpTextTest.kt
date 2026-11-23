package com.coparently.app.presentation.payments

import com.coparently.app.presentation.home.dashKeptWithItsWord
import kotlin.test.Test
import kotlin.test.assertEquals

/** The settle-up panel's and the request cards' text rules (text review, November 2026). */
class SettleUpTextTest {

    @Test
    fun `an IBAN breaks only between its two halves`() {
        assertEquals(
            "CZ65 0800 0000 1920 0014 5399",
            ibanText("CZ65 0800 0000 1920 0014 5399")
        )
    }

    @Test
    fun `an odd number of groups puts the extra one first`() {
        assertEquals("DE89 3704 0044 0532 0130", ibanText("DE89 3704 0044 0532 0130"))
    }

    @Test
    fun `a short IBAN has no break at all`() {
        assertEquals("NO93", ibanText("NO93"))
    }

    @Test
    fun `a date ending in a full stop does not double the sentence's own`() {
        assertEquals("Zaplatili jste dne 22. 5.", withoutDoubledFullStop("Zaplatili jste dne 22. 5.."))
        assertEquals("позначено 22 трав.", withoutDoubledFullStop("позначено 22 трав.."))
        assertEquals("You paid on May 22.", withoutDoubledFullStop("You paid on May 22."))
    }

    @Test
    fun `a dash in a title stays with the word before it`() {
        assertEquals("Dentist — Leo", dashKeptWithItsWord("Dentist — Leo"))
        assertEquals("Swim – Emma", dashKeptWithItsWord("Swim – Emma"))
        assertEquals("Dentist", dashKeptWithItsWord("Dentist"))
    }
}
