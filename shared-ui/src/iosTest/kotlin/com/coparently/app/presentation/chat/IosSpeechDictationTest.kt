package com.coparently.app.presentation.chat

import com.coparently.app.domain.dictation.DictationFailure
import com.coparently.app.domain.dictation.DictationListener
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

/**
 * The iPhone's dictation (phase 4 part E1) over a fake recognizer: no microphone without an
 * on-device model, a language the phone cannot recognize refused before anything starts, the text
 * handed on as partial then final, a new start replacing the running session, and the session
 * ended by the background (CLAUDE.md item 28).
 */
class IosSpeechDictationTest {

    private val source = FakeSource()
    private val listener = RecordingListener()
    private val dictation = IosSpeechDictation(source, languageTag = { "cs" })

    @Test
    fun noMicrophoneWithoutAnOnDeviceModel() {
        source.onDevice = false

        assertFalse(dictation.isAvailable())
        assertEquals(listOf("cs"), source.asked)
    }

    @Test
    fun anUnsupportedLanguageFailsAtStart() {
        source.onDevice = false

        dictation.start("de", listener)

        assertEquals(listOf("failure:LANGUAGE_UNAVAILABLE"), listener.events)
        assertEquals(0, source.begun)
    }

    @Test
    fun textArrivesAsPartialThenFinal() {
        dictation.start(null, listener)
        source.onText("Ahoj", false)
        source.onText("Ahoj Lano", true)
        source.onText("late", false)

        assertEquals(listOf("partial:Ahoj", "final:Ahoj Lano"), listener.events)
        assertEquals("cs", source.lastTag)
    }

    @Test
    fun aSecondStartReplacesTheFirst() {
        val second = RecordingListener()
        dictation.start("cs", listener)

        dictation.start("cs", second)

        assertEquals(2, source.begun)
        assertEquals(1, source.sessions[0].aborted)
        assertEquals(0, source.sessions[1].aborted)
        source.onText("Ahoj", false)
        assertEquals(listOf("partial:Ahoj"), second.events)
        assertEquals(emptyList(), listener.events)
    }

    @Test
    fun aLateCallbackFromAReplacedSessionIsIgnored() {
        val second = RecordingListener()
        dictation.start("cs", listener)
        val firstText = source.onText
        val firstFailure = source.onFailure
        dictation.stop()

        dictation.start("cs", second)
        firstText("late", true)
        firstFailure(DictationFailure.OTHER)

        assertEquals(1, source.sessions[0].finished)
        assertEquals(emptyList(), listener.events)
        assertEquals(emptyList(), second.events)
        source.onText("Ahoj", true)
        assertEquals(listOf("final:Ahoj"), second.events)
    }

    @Test
    fun backgroundAbortsTheSession() {
        dictation.start("cs", listener)
        source.onText("Ahoj", false)

        dictation.onBackground()
        dictation.stop()
        dictation.onBackground()

        assertEquals(1, source.session.aborted)
        assertEquals(0, source.session.finished)
        assertEquals(listOf("partial:Ahoj", "final:Ahoj"), listener.events)
    }

    @Test
    fun backgroundWithNothingHeardEndsSilently() {
        dictation.start("cs", listener)

        dictation.onBackground()

        assertEquals(1, source.session.aborted)
        assertEquals(emptyList(), listener.events)
    }

    @Test
    fun stopFinishesOnce() {
        dictation.start("cs", listener)

        dictation.stop()
        source.onText("Ahoj", true)
        dictation.stop()

        assertEquals(1, source.session.finished)
        assertEquals(0, source.session.aborted)
        assertEquals(listOf("final:Ahoj"), listener.events)
    }

    /** A recognizer that answers [onDevice] and remembers what it was asked. */
    private class FakeSource : SpeechRecognizerSource {
        var onDevice = true
        val asked = mutableListOf<String>()
        var begun = 0
        var lastTag: String? = null
        val sessions = mutableListOf<FakeSession>()
        val session: FakeSession get() = sessions.last()
        var onText: (String, Boolean) -> Unit = { _, _ -> }
        var onFailure: (DictationFailure) -> Unit = {}

        override fun supportsOnDevice(languageTag: String): Boolean {
            asked += languageTag
            return onDevice
        }

        override fun begin(
            languageTag: String,
            onText: (text: String, isFinal: Boolean) -> Unit,
            onFailure: (DictationFailure) -> Unit
        ): SpeechSession {
            begun++
            lastTag = languageTag
            this.onText = onText
            this.onFailure = onFailure
            return FakeSession().also { sessions += it }
        }
    }

    /** Counts its calls. */
    private class FakeSession : SpeechSession {
        var finished = 0
        var aborted = 0

        override fun finish() {
            finished++
        }

        override fun abort() {
            aborted++
        }
    }

    /** The listener's calls, in order, as text. */
    private class RecordingListener : DictationListener {
        val events = mutableListOf<String>()

        override fun onPartial(text: String) {
            events += "partial:$text"
        }

        override fun onFinal(text: String) {
            events += "final:$text"
        }

        override fun onFailure(failure: DictationFailure) {
            events += "failure:${failure.name}"
        }
    }
}
