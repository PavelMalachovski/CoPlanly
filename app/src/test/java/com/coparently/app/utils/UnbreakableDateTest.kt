package com.coparently.app.utils

import org.junit.Test
import kotlin.test.assertEquals

/**
 * A formatted date never breaks between its day and its month, nor after a short weekday or before
 * its time; a full weekday may take a line of its own.
 */
class UnbreakableDateTest {

    @Test
    fun `spaces inside a date become no-break`() {
        assertEquals("4\u00A0октября\u00A02026\u00A0г.", unbreakableDate("4 октября 2026 г."))
    }

    @Test
    fun `the space after a full weekday's comma stays breakable`() {
        assertEquals(
            "суббота, 4\u00A0октября\u00A02026\u00A0г.",
            unbreakableDate("суббота, 4 октября 2026 г.")
        )
    }

    @Test
    fun `a short weekday keeps its date`() {
        assertEquals("Mi.,\u00A013.\u00A0Mai", unbreakableDate("Mi., 13. Mai"))
        assertEquals("сб,\u00A016\u00A0трав.", unbreakableDate("сб, 16 трав."))
        assertEquals("Wed,\u00A0May\u00A013, 2026", unbreakableDate("Wed, May 13, 2026"))
    }

    @Test
    fun `a time keeps its date`() {
        assertEquals("Mi.,\u00A013.\u00A0Mai,\u00A020:00", unbreakableDate("Mi., 13. Mai, 20:00"))
    }

    @Test
    fun `a date without spaces is unchanged`() {
        assertEquals("03.10.2026", unbreakableDate("03.10.2026"))
    }

    @Test
    fun `a pattern keeps a short weekday with its date and a full one apart`() {
        assertEquals("EEE,\u00A0d.\u00A0MMM", noBreakPattern("EEE, d. MMM"))
        assertEquals("EEEE, d.\u00A0MMMM", noBreakPattern("EEEE, d. MMMM"))
        assertEquals("MMM\u00A0d,\u00A0h:mm", noBreakPattern("MMM d, h:mm"))
        assertEquals("MMM\u00A0d, y", noBreakPattern("MMM d, y"))
    }
}
