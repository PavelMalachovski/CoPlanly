package com.coparently.app.domain.chat

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals

/**
 * What [TextFold] answers, written down from the JDK implementation it replaced (MON-24;
 * `TextFoldParityTest` holds the JVM to that implementation over random text), so the iPhone is
 * held to the same folds: its lower-casing, its NFD and its `\p{Mn}` are the platform's own.
 */
class TextFoldTest {

    @Test
    fun foldsCaseAndDiacritics() {
        assertEquals("cas", TextFold.simple("Čas"))
        assertEquals("prilis zlutoucky kun", TextFold.simple("Příliš žluťoučký kůň"))
        assertEquals("aou ß", TextFold.simple("ÄÖÜ ß"))
        assertEquals("привет, еж", TextFold.simple("Привет, ЁЖ"))
        // Ukrainian yi loses its diaeresis to the Ukrainian i; ghe with upturn has no decomposition.
        assertEquals("\u0456жак \u0456 \u0491анок", TextFold.simple("Їжак і ґанок"))
        assertEquals("i", TextFold.simple("İ"))
    }

    @Test
    fun mapsAPrecomposedAndADecomposedLetterToTheWholeCharacter() {
        val precomposed = TextFold.fold("Čas")
        assertEquals("cas", precomposed.text)
        assertContentEquals(intArrayOf(0, 1, 2), precomposed.starts)
        assertContentEquals(intArrayOf(1, 2, 3), precomposed.ends)

        val decomposed = TextFold.fold("čas")
        assertEquals("cas", decomposed.text)
        assertContentEquals(intArrayOf(0, 2, 3), decomposed.starts)
        assertContentEquals(intArrayOf(2, 3, 4), decomposed.ends)
    }

    @Test
    fun aLeadingMarkFoldsToNothing() {
        val folded = TextFold.fold("́x")
        assertEquals("x", folded.text)
        assertContentEquals(intArrayOf(1), folded.starts)
        assertContentEquals(intArrayOf(2), folded.ends)
    }

    @Test
    fun anEmojiIsOneUnit() {
        val folded = TextFold.fold("😀x")
        assertEquals("😀x", folded.text)
        assertContentEquals(intArrayOf(0, 0, 2), folded.starts)
        assertContentEquals(intArrayOf(2, 2, 3), folded.ends)
    }

    @Test
    fun whitespaceIsWhatCharacterIsWhitespaceSays() {
        assertEquals("a b c d e f g", TextFold.simple("a\tb\nc\u000Bd\u001Fe f　g"))
        assertEquals("a b c", TextFold.simple("a b c"))
        // The three no-break spaces are not whitespace to Character.isWhitespace, so they stay.
        assertEquals("a b c d", TextFold.simple("a b c d"))
    }

    @Test
    fun theMongolianVowelSeparatorIsNotWhitespace() {
        // U+180E has been a format character (Cf), not a space, since Unicode 6.3, and
        // Character.isWhitespace says no. A platform whose table predates that would fold it to a
        // space; this holds the iPhone's to the JVM's answer.
        assertEquals("a\u180Eb", TextFold.simple("a\u180Eb"))
    }
}
