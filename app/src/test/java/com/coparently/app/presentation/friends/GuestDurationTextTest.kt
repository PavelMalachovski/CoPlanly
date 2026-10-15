package com.coparently.app.presentation.friends

import org.junit.Test
import kotlin.test.assertEquals

/**
 * The comparison table's "How long" cell for a family friend (phase 2, spec §4.2) is built from
 * the very list the invite's chips offer, so the two cannot disagree when a length is added.
 */
class GuestDurationTextTest {

    private val or: (String, String) -> String = { first, second -> "$first or $second" }

    @Test
    fun `the last two choices are joined by the sentence, the rest by commas`() {
        assertEquals("1 week, 30 days or 90 days", joinChoices(listOf("1 week", "30 days", "90 days"), or))
        assertEquals("30 days or 90 days", joinChoices(listOf("30 days", "90 days"), or))
        assertEquals("90 days", joinChoices(listOf("90 days"), or))
        assertEquals("", joinChoices(emptyList(), or))
    }
}
