package com.coparently.app.utils

import org.junit.Test
import kotlin.test.assertEquals

/** A formatted date never breaks between its day and its month; its weekday may. */
class UnbreakableDateTest {

    @Test
    fun `spaces inside a date become no-break`() {
        assertEquals("4 октября 2026 г.", unbreakableDate("4 октября 2026 г."))
    }

    @Test
    fun `the space after the weekday's comma stays breakable`() {
        assertEquals(
            "суббота, 4 октября 2026 г.",
            unbreakableDate("суббота, 4 октября 2026 г.")
        )
    }

    @Test
    fun `a date without spaces is unchanged`() {
        assertEquals("03.10.2026", unbreakableDate("03.10.2026"))
    }
}
