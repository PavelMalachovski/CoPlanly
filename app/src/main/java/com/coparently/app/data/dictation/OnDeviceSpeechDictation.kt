package com.coparently.app.data.dictation

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import com.coparently.app.domain.dictation.DictationFailure
import com.coparently.app.domain.dictation.DictationListener
import com.coparently.app.domain.dictation.SpeechDictation
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * [SpeechDictation] over the platform's **on-device** speech recognizer, and nothing else.
 *
 * Which phones get the microphone, and why: only Android 13 (API 33) and later, and only where
 * `SpeechRecognizer.isOnDeviceRecognitionAvailable` says the phone has an on-device recognition
 * service. The owner's rule is that the audio must not leave the phone, and that is the only
 * platform answer that promises it:
 * - `SpeechRecognizer.createSpeechRecognizer` binds to whichever recognition service the phone
 *   has chosen, and on most phones that is Google's, which may send the audio to its servers.
 *   `RecognizerIntent.EXTRA_PREFER_OFFLINE` is a *preference* the service is free to ignore, so it
 *   cannot carry the promise — it is set below as well, but only as belt and braces.
 * - `createOnDeviceSpeechRecognizer` exists from API 31, but the question "does this phone have
 *   one?" can only be asked from API 33. On 31 and 32 the button would have to appear and then
 *   fail on phones without one, which is design item 8's broken promise.
 *
 * So below API 33, or without an on-device service, [isAvailable] is false and the composer has
 * no microphone. There is deliberately no fallback to the network recognizer; switching to one
 * (or to any cloud speech service) needs an owner decision first — see CLAUDE.md, design item 8
 * and the chat notes, and the privacy policy, which says the audio stays on the phone.
 *
 * Nothing is recorded: the audio buffers the service may offer ([RecognitionListener.onBufferReceived])
 * are ignored, and only the recognised text is handed on. One session at a time; a new [start]
 * cancels the previous one, and callbacks from a recognizer that is no longer current are dropped.
 */
@Singleton
class OnDeviceSpeechDictation @Inject constructor(
    @ApplicationContext private val context: Context
) : SpeechDictation {

    private var recognizer: SpeechRecognizer? = null

    override fun isAvailable(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return false
        return SpeechRecognizer.isOnDeviceRecognitionAvailable(context)
    }

    override fun start(languageTag: String?, listener: DictationListener) {
        cancel()
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            listener.onFailure(DictationFailure.UNAVAILABLE)
            return
        }
        if (!isAvailable()) {
            listener.onFailure(DictationFailure.UNAVAILABLE)
            return
        }
        val created = SpeechRecognizer.createOnDeviceSpeechRecognizer(context)
        recognizer = created
        created.setRecognitionListener(SessionListener(created, listener))
        created.startListening(recognizerIntent(languageTag))
    }

    override fun stop() {
        recognizer?.stopListening()
    }

    override fun cancel() {
        val current = recognizer ?: return
        recognizer = null
        current.cancel()
        current.destroy()
    }

    /** Ends the session of [owner] if it is still the current one; true when it was. */
    private fun finish(owner: SpeechRecognizer): Boolean {
        if (recognizer !== owner) return false
        recognizer = null
        owner.destroy()
        return true
    }

    private inner class SessionListener(
        private val owner: SpeechRecognizer,
        private val listener: DictationListener
    ) : RecognitionListener {
        override fun onPartialResults(partialResults: Bundle?) {
            if (recognizer === owner) listener.onPartial(firstResult(partialResults))
        }

        override fun onResults(results: Bundle?) {
            val text = firstResult(results)
            if (finish(owner)) listener.onFinal(text)
        }

        override fun onError(error: Int) {
            if (finish(owner)) listener.onFailure(SpeechErrors.failureFor(error))
        }

        override fun onReadyForSpeech(params: Bundle?) = Unit
        override fun onBeginningOfSpeech() = Unit
        override fun onRmsChanged(rmsdB: Float) = Unit

        // Audio is never kept: the buffer is not read, copied or stored.
        override fun onBufferReceived(buffer: ByteArray?) = Unit
        override fun onEndOfSpeech() = Unit
        override fun onEvent(eventType: Int, params: Bundle?) = Unit
    }

    private companion object {
        fun recognizerIntent(languageTag: String?): Intent =
            Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
                // Belt and braces only: the on-device recognizer is what keeps the audio here.
                putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
                if (!languageTag.isNullOrBlank()) putExtra(RecognizerIntent.EXTRA_LANGUAGE, languageTag)
            }

        fun firstResult(bundle: Bundle?): String =
            bundle?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull().orEmpty()
    }
}

/**
 * The platform's `SpeechRecognizer.ERROR_*` codes as a [DictationFailure].
 *
 * Literal values rather than the constants, because 10 and up are API 31 and 33 additions and
 * this runs on API 26; the numbers are part of the platform's stable API.
 */
internal object SpeechErrors {
    private const val NETWORK_TIMEOUT = 1
    private const val NETWORK = 2
    private const val AUDIO = 3
    private const val SERVER = 4
    private const val CLIENT = 5
    private const val SPEECH_TIMEOUT = 6
    private const val NO_MATCH = 7
    private const val RECOGNIZER_BUSY = 8
    private const val INSUFFICIENT_PERMISSIONS = 9
    private const val LANGUAGE_NOT_SUPPORTED = 12
    private const val LANGUAGE_UNAVAILABLE = 13

    /** The failure the parent is told about for the platform's [code]. */
    fun failureFor(code: Int): DictationFailure = when (code) {
        SPEECH_TIMEOUT, NO_MATCH -> DictationFailure.NOTHING_HEARD
        LANGUAGE_NOT_SUPPORTED, LANGUAGE_UNAVAILABLE -> DictationFailure.LANGUAGE_UNAVAILABLE
        RECOGNIZER_BUSY -> DictationFailure.BUSY
        INSUFFICIENT_PERMISSIONS -> DictationFailure.PERMISSION
        // A network or server error from an on-device recognizer means it could not do the work
        // locally; it is reported as a failure, never retried through a network recognizer.
        NETWORK_TIMEOUT, NETWORK, SERVER, AUDIO, CLIENT -> DictationFailure.OTHER
        else -> DictationFailure.OTHER
    }
}
