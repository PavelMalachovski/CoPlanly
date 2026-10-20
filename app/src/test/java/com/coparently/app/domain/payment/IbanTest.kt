package com.coparently.app.domain.payment

import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** An IBAN is accepted as people type it and refused when a digit is wrong. */
class IbanTest {

    @Test
    fun `spaces and lower case are tidied away`() {
        assertEquals("CZ6508000000192000145399", Iban.normalize(" cz65 0800 0000 1920 0014 5399 "))
        assertEquals("SK3112000000198742637541", Iban.normalize("SK31 1200 0000 1987 4263 7541"))
        assertEquals("DE89370400440532013000", Iban.normalize("DE89 3704 0044 0532 0130 00"))
    }

    @Test
    fun `a wrong check digit, a bad shape and nothing are refused`() {
        listOf("CZ6508000000192000145398", "CZ65", "6508000000192000145399", "CZ65 0800 0000 1920 0014 539!", "", null)
            .forEach { assertNull(Iban.normalize(it), it.toString()) }
    }

    @Test
    fun `an IBAN is grouped by four and knows its country`() {
        assertEquals("SK31 1200 0000 1987 4263 7541", Iban.grouped("SK3112000000198742637541"))
        assertEquals("SK", Iban.countryOf("SK3112000000198742637541"))
    }
}
