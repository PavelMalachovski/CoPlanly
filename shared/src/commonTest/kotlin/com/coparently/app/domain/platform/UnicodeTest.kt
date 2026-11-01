package com.coparently.app.domain.platform

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * [canonicalDecomposition] is Unicode NFD, which chat search ([com.coparently.app.domain.chat]'s
 * `TextFold`) and the school import fold accents with: "č" must split into "c" and its caron.
 */
class UnicodeTest {

    @Test
    fun aCaronSplitsFromItsLetter() {
        val decomposed = "čas".canonicalDecomposition()

        assertEquals(4, decomposed.length)
        assertEquals("čas", decomposed)
    }

    @Test
    fun plainAsciiIsUnchanged() {
        assertEquals("CoPlanly 2026-10", "CoPlanly 2026-10".canonicalDecomposition())
    }

    @Test
    fun cyrillicShortIDecomposes() {
        // й is и with a breve: the reason chat search cannot use SQLite's unicode61 tokenizer.
        assertEquals("й", "й".canonicalDecomposition())
    }
}
