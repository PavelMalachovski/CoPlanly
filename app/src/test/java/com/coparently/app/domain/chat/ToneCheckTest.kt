package com.coparently.app.domain.chat

import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The pause-before-sending hint's three rules (MON-19).
 *
 * The hint never blocks a send, so a false positive costs a line of text — but a hint that fires
 * on every acronym is one a parent learns to ignore, and then it says nothing when it matters.
 * These pin both directions.
 */
class ToneCheckTest {

    private val words = listOf("always", "never", "hloup*", "směšn*", "nie")

    // ---- capitals ---------------------------------------------------------

    @Test
    fun `two capitalised words read as shouting`() {
        assertTrue(ToneCheck.check("WHERE WERE you", words).shouting)
    }

    @Test
    fun `capitals are shouting in Cyrillic too`() {
        assertTrue(ToneCheck.check("ПОЧЕМУ ОПЯТЬ так", words).shouting)
    }

    @Test
    fun `one acronym is not shouting`() {
        assertFalse(ToneCheck.check("Meeting at OSPOD tomorrow", words).shouting)
    }

    @Test
    fun `short capitalised words do not count`() {
        // "OK", "I", "TV": under the letter threshold, however many there are.
        assertFalse(ToneCheck.check("OK I SAW TV", words).shouting)
    }

    @Test
    fun `a word with any lower-case letter is not shouting`() {
        assertFalse(ToneCheck.check("NEVer EVer", words).shouting)
    }

    // ---- punctuation ------------------------------------------------------

    @Test
    fun `three marks in a row are flagged, mixed or not`() {
        assertTrue(ToneCheck.check("Really!!!", words).raisedPunctuation)
        assertTrue(ToneCheck.check("What?!?", words).raisedPunctuation)
    }

    @Test
    fun `two marks, or marks apart, are not`() {
        assertFalse(ToneCheck.check("Thanks!!", words).raisedPunctuation)
        assertFalse(ToneCheck.check("Really? Yes! Ok?", words).raisedPunctuation)
    }

    // ---- the word list ----------------------------------------------------

    @Test
    fun `listed words are reported as typed, in order`() {
        val nudge = ToneCheck.check("You ALWAYS do this, never again", words)

        assertEquals(listOf("ALWAYS", "never"), nudge.words)
    }

    @Test
    fun `a starred entry matches every ending of the word`() {
        assertEquals(listOf("hloupost"), ToneCheck.check("To je hloupost", words).words)
        assertEquals(listOf("hloupý"), ToneCheck.check("hloupý nápad", words).words)
    }

    @Test
    fun `accents and case do not hide a listed word`() {
        assertEquals(listOf("smesne"), ToneCheck.check("to je smesne", words).words)
        assertEquals(listOf("SMĚŠNÉ"), ToneCheck.check("SMĚŠNÉ", words).words)
    }

    @Test
    fun `a starred entry does not match inside another word`() {
        assertTrue(ToneCheck.check("nehloupý", words).words.isEmpty())
    }

    @Test
    fun `an unstarred entry matches only the whole word`() {
        assertTrue(ToneCheck.check("Niemand kommt", words).words.isEmpty())
        assertEquals(listOf("nie"), ToneCheck.check("Das klappt nie", words).words)
    }

    @Test
    fun `a word repeated is reported once`() {
        assertEquals(listOf("never"), ToneCheck.check("never, Never, NEVER", words).words)
    }

    @Test
    fun `blank entries in a list are ignored rather than matching everything`() {
        assertTrue(ToneCheck.check("anything at all", listOf("", " ", "*")).words.isEmpty())
    }

    // ---- nothing to say ---------------------------------------------------

    @Test
    fun `a calm message produces no hint`() {
        assertTrue(ToneCheck.check("Pickup at five works for me, thanks.", words).isEmpty)
    }

    @Test
    fun `an empty draft produces no hint`() {
        assertTrue(ToneCheck.check("   ", words).isEmpty)
    }
}
