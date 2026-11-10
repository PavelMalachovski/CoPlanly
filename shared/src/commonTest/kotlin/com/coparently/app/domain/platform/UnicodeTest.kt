package com.coparently.app.domain.platform

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * [canonicalDecomposition] is Unicode NFD, which chat search ([com.coparently.app.domain.chat]'s
 * `TextFold`) and the school import fold accents with: "č" must split into "c" and its caron.
 */
class UnicodeTest {

    // Every non-ASCII value is an escape, not a literal: an editor that normalises source text
    // would otherwise turn an input or an expected value into the other form, unnoticed.

    @Test
    fun aCaronSplitsFromItsLetter() {
        val decomposed = "\u010Das".canonicalDecomposition()

        assertEquals(4, decomposed.length)
        assertEquals("c\u030Cas", decomposed)
    }

    @Test
    fun plainAsciiIsUnchanged() {
        assertEquals("CoPlanly 2026-10", "CoPlanly 2026-10".canonicalDecomposition())
    }

    @Test
    fun cyrillicShortIDecomposes() {
        // The short i is the plain i with a breve: the reason chat search cannot use SQLite's
        // unicode61 tokenizer.
        assertEquals("\u0438\u0306", "\u0439".canonicalDecomposition())
    }
}
