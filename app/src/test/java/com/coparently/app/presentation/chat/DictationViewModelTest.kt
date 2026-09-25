package com.coparently.app.presentation.chat

import com.coparently.app.domain.dictation.DictationFailure
import com.coparently.app.domain.dictation.DictationListener
import com.coparently.app.domain.dictation.SpeechDictation
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Voice dictation in the composer: the heard words are appended to the draft live, a failure keeps
 * what was heard, and a phone without on-device recognition gets no microphone and never listens.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class DictationViewModelTest {

    private class FakeDictation(private val available: Boolean = true) : SpeechDictation {
        var listener: DictationListener? = null
        var language: String? = null
        var starts = 0
        var stops = 0
        var cancels = 0

        override fun isAvailable() = available
        override fun start(languageTag: String?, listener: DictationListener) {
            starts++
            language = languageTag
            this.listener = listener
        }
        override fun stop() {
            stops++
        }
        override fun cancel() {
            cancels++
        }
    }

    @Test
    fun `no on-device recognizer means no microphone and no session`() {
        val fake = FakeDictation(available = false)
        val viewModel = DictationViewModel(fake)

        assertFalse(viewModel.state.value.available)
        viewModel.start("draft", "en")
        assertEquals(0, fake.starts)
        assertFalse(viewModel.state.value.listening)
    }

    @Test
    fun `partial results are appended to the draft live, in the app's language`() = runTest {
        val fake = FakeDictation()
        val viewModel = DictationViewModel(fake)

        viewModel.start("See you at", "cs-CZ")
        assertTrue(viewModel.state.value.listening)
        assertEquals("cs-CZ", fake.language)

        fake.listener!!.onPartial("five")
        assertEquals(DictatedText("See you at five", final = false), viewModel.text.first())
        fake.listener!!.onPartial("five thirty")
        assertEquals(DictatedText("See you at five thirty", final = false), viewModel.text.first())
    }

    @Test
    fun `the final result ends the session and is marked final`() = runTest {
        val fake = FakeDictation()
        val viewModel = DictationViewModel(fake)

        viewModel.start("", "en")
        fake.listener!!.onFinal("pick up at six")

        assertFalse(viewModel.state.value.listening)
        assertNull(viewModel.state.value.failure)
        assertEquals(DictatedText("pick up at six", final = true), viewModel.text.first())
    }

    @Test
    fun `a failure after words were heard keeps them and reports nothing`() = runTest {
        val fake = FakeDictation()
        val viewModel = DictationViewModel(fake)

        viewModel.start("Hi.", "en")
        fake.listener!!.onPartial("running late")
        viewModel.text.first()
        fake.listener!!.onFailure(DictationFailure.NOTHING_HEARD)

        assertFalse(viewModel.state.value.listening)
        assertNull(viewModel.state.value.failure)
        assertEquals(DictatedText("Hi. running late", final = true), viewModel.text.first())
    }

    @Test
    fun `a failure with nothing heard is reported and the draft is untouched`() {
        val fake = FakeDictation()
        val viewModel = DictationViewModel(fake)

        viewModel.start("draft", "de")
        fake.listener!!.onFailure(DictationFailure.LANGUAGE_UNAVAILABLE)

        assertEquals(DictationFailure.LANGUAGE_UNAVAILABLE, viewModel.state.value.failure)
        assertTrue(viewModel.state.value.available)
    }

    @Test
    fun `a blank final result says nothing was heard`() {
        val fake = FakeDictation()
        val viewModel = DictationViewModel(fake)

        viewModel.start("draft", "en")
        fake.listener!!.onFinal("  ")

        assertEquals(DictationFailure.NOTHING_HEARD, viewModel.state.value.failure)
    }

    @Test
    fun `recognition that became unavailable hides the microphone`() {
        val fake = FakeDictation()
        val viewModel = DictationViewModel(fake)

        viewModel.start("", "en")
        fake.listener!!.onFailure(DictationFailure.UNAVAILABLE)

        assertFalse(viewModel.state.value.available)
        assertEquals(DictationFailure.UNAVAILABLE, viewModel.state.value.failure)
    }

    @Test
    fun `stop asks for the final result, cancel drops later callbacks`() {
        val fake = FakeDictation()
        val viewModel = DictationViewModel(fake)

        viewModel.stop()
        assertEquals(0, fake.stops)

        viewModel.start("", "en")
        viewModel.stop()
        assertEquals(1, fake.stops)

        viewModel.cancel()
        assertEquals(1, fake.cancels)
        assertFalse(viewModel.state.value.listening)
        // A late callback from the cancelled session changes nothing.
        fake.listener!!.onFailure(DictationFailure.OTHER)
        assertNull(viewModel.state.value.failure)
    }

    @Test
    fun `a refused microphone is explained, and the next start clears it`() {
        val fake = FakeDictation()
        val viewModel = DictationViewModel(fake)

        viewModel.onPermissionDenied()
        assertEquals(DictationFailure.PERMISSION, viewModel.state.value.failure)

        viewModel.start("", "en")
        assertNull(viewModel.state.value.failure)
    }

    @Test
    fun `dismissFailure clears the line`() {
        val viewModel = DictationViewModel(FakeDictation())
        viewModel.onPermissionDenied()
        viewModel.dismissFailure()
        assertNull(viewModel.state.value.failure)
    }
}
