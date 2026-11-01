package com.coparently.app.domain.payment

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals

/** [mod97] is the ISO 13616 check an IBAN passes, over digits longer than a Long holds. */
class Mod97Test {

    @Test
    fun aValidCzechIbanLeavesOne() {
        // CZ6508000000192000145399: the account first, then C=12, Z=35 and the check digits 65.
        assertEquals(1, mod97("08000000192000145399123565"))
    }

    @Test
    fun aOneDigitChangeDoesNotLeaveOne() {
        assertNotEquals(1, mod97("08000000192000145398123565"))
    }

    @Test
    fun smallNumbersAreTheirOwnRemainder() {
        assertEquals(0, mod97("0"))
        assertEquals(96, mod97("96"))
        assertEquals(0, mod97("97"))
        assertEquals(1, mod97("98"))
    }

    @Test
    fun refusesWhatIsNotDigits() {
        assertFailsWith<IllegalArgumentException> { mod97("") }
        assertFailsWith<IllegalArgumentException> { mod97("12a4") }
    }
}
