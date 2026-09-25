package com.coparently.app.presentation.calendar

import org.junit.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * When a week block's title falls back to one line: only when a line was cut inside a word, as
 * the UI tour found "Plavání" drawn as "Plaván" over "í".
 */
class EventTitleLinesTest {

    @Test
    fun `a word cut across two lines is inside a word`() {
        assertTrue(breaksInsideWord("Plavání", listOf(6, 7)))
    }

    @Test
    fun `a break after a space is a word boundary`() {
        // "Třídní " | "schůzka"
        assertFalse(breaksInsideWord("Třídní schůzka", listOf(7, 14)))
    }

    @Test
    fun `a break before a space is a word boundary`() {
        assertFalse(breaksInsideWord("Třídní schůzka", listOf(6, 14)))
    }

    @Test
    fun `a break after a hyphen is a word boundary`() {
        assertFalse(breaksInsideWord("Pick-up", listOf(5, 7)))
    }

    @Test
    fun `a cut in the second of three lines counts`() {
        // "ze " | "Vyzvedn" | "utí"
        assertTrue(breaksInsideWord("ze Vyzvednutí", listOf(3, 10, 13)))
    }

    @Test
    fun `the last line is never asked about`() {
        // The last line ends where the text ends, or at its ellipsis.
        assertFalse(breaksInsideWord("Třídní schůzka", listOf(7, 11)))
    }

    @Test
    fun `one line breaks nothing`() {
        assertFalse(breaksInsideWord("Plavání", listOf(7)))
        assertFalse(breaksInsideWord("", emptyList()))
    }
}
