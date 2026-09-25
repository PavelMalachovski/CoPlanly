package com.coparently.app.domain.dictation

import com.coparently.app.data.dictation.SpeechErrors
import org.junit.Test
import kotlin.test.assertEquals

/** Dictation appends to the draft, and the platform's error codes map to the right sentence. */
class DictationTextTest {

    @Test
    fun `dictated words are appended with one space`() {
        assertEquals("See you at five", DictationText.merge("See you at", "five"))
        assertEquals("See you at five", DictationText.merge("See you at ", " five "))
        assertEquals("five", DictationText.merge("", "five"))
        assertEquals("Line\nfive", DictationText.merge("Line\n", "five"))
    }

    @Test
    fun `nothing heard leaves the draft exactly as it was`() {
        assertEquals("draft ", DictationText.merge("draft ", "   "))
        assertEquals("", DictationText.merge("", ""))
    }

    @Test
    fun `platform error codes map to the failure the parent is told`() {
        assertEquals(DictationFailure.NOTHING_HEARD, SpeechErrors.failureFor(6))
        assertEquals(DictationFailure.NOTHING_HEARD, SpeechErrors.failureFor(7))
        assertEquals(DictationFailure.BUSY, SpeechErrors.failureFor(8))
        assertEquals(DictationFailure.PERMISSION, SpeechErrors.failureFor(9))
        assertEquals(DictationFailure.LANGUAGE_UNAVAILABLE, SpeechErrors.failureFor(12))
        assertEquals(DictationFailure.LANGUAGE_UNAVAILABLE, SpeechErrors.failureFor(13))
        // A network error is a failure, never a reason to try a network recognizer.
        assertEquals(DictationFailure.OTHER, SpeechErrors.failureFor(2))
        assertEquals(DictationFailure.OTHER, SpeechErrors.failureFor(99))
    }
}
