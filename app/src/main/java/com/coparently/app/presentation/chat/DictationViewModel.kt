package com.coparently.app.presentation.chat

import androidx.lifecycle.ViewModel
import com.coparently.app.domain.dictation.DictationFailure
import com.coparently.app.domain.dictation.DictationListener
import com.coparently.app.domain.dictation.DictationText
import com.coparently.app.domain.dictation.SpeechDictation
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import javax.inject.Inject

/**
 * What the composer's microphone shows.
 *
 * @property available Whether this phone recognises speech on the device; the microphone is not
 *   drawn at all when it does not
 * @property listening A session is running: the microphone reads as "stop"
 * @property failure Why the last session ended without text, shown once under the composer
 */
data class DictationUiState(
    val available: Boolean = false,
    val listening: Boolean = false,
    val failure: DictationFailure? = null
)

/**
 * The composer text dictation produced.
 *
 * @property text The whole composer text: the draft as it was when dictation started, with the
 *   words heard so far appended
 * @property final The session is over — the moment to persist the draft, rather than on every
 *   partial result
 */
data class DictatedText(val text: String, val final: Boolean)

/**
 * Voice dictation in the chat composer (on-device only — see [SpeechDictation]).
 *
 * Separate from `ChatViewModel`, like `ChatAttachmentViewModel`, and it never owns the composer
 * text: `ChatScreen` does, because several things seed it. This class remembers the draft as it
 * was when the parent tapped the microphone and emits that draft with the heard words appended
 * ([DictationText.merge]) on every partial result, so the words appear live and are *added* to what
 * was typed, never put in its place. Whatever was heard stays in the field when a session fails.
 *
 * A session ends by the parent's tap ([stop]), by the recognizer deciding the parent stopped
 * speaking, or by [cancel] — which the screen calls when the parent types or sends, when the
 * screen stops, and which [onCleared] calls too, so the microphone never outlives the thread.
 */
@HiltViewModel
class DictationViewModel @Inject constructor(
    private val dictation: SpeechDictation
) : ViewModel() {

    private val _state = MutableStateFlow(DictationUiState(available = dictation.isAvailable()))

    /** What the microphone shows. */
    val state: StateFlow<DictationUiState> = _state.asStateFlow()

    // Conflated: only the latest text matters, and a slow collector must never replay stale words.
    private val _text = Channel<DictatedText>(Channel.CONFLATED)

    /** Composer text to show, as dictation produces it. */
    val text: Flow<DictatedText> = _text.receiveAsFlow()

    private var draft = ""
    private var heard = ""

    /**
     * Starts listening, appending to [currentDraft]. Called only once the microphone permission
     * is granted.
     *
     * @param languageTag The app's current language, as a BCP 47 tag
     */
    fun start(currentDraft: String, languageTag: String?) {
        if (!_state.value.available) return
        draft = currentDraft
        heard = ""
        _state.update { it.copy(listening = true, failure = null) }
        dictation.start(languageTag, Listener())
    }

    /** Stops listening; what was heard arrives as the final text. */
    fun stop() {
        if (_state.value.listening) dictation.stop()
    }

    /** Abandons a running session, keeping whatever words the field already shows. */
    fun cancel() {
        if (!_state.value.listening) return
        dictation.cancel()
        _state.update { it.copy(listening = false) }
    }

    /** The parent refused the microphone: say why it is needed, and where to allow it. */
    fun onPermissionDenied() {
        _state.update { it.copy(listening = false, failure = DictationFailure.PERMISSION) }
    }

    /** The failure line was read or no longer applies (the parent typed). */
    fun dismissFailure() {
        if (_state.value.failure != null) _state.update { it.copy(failure = null) }
    }

    override fun onCleared() {
        dictation.cancel()
    }

    private inner class Listener : DictationListener {
        override fun onPartial(text: String) {
            if (!_state.value.listening) return
            heard = text
            _text.trySend(DictatedText(DictationText.merge(draft, text), final = false))
        }

        override fun onFinal(text: String) {
            if (!_state.value.listening) return
            val words = text.ifBlank { heard }
            _state.update {
                it.copy(
                    listening = false,
                    failure = DictationFailure.NOTHING_HEARD.takeIf { words.isBlank() }
                )
            }
            _text.trySend(DictatedText(DictationText.merge(draft, words), final = true))
        }

        override fun onFailure(failure: DictationFailure) {
            if (!_state.value.listening) return
            // Words already in the field stay there, and a session that heard something is not
            // reported as a failure — a recognizer often ends a good session with "no match".
            _state.update {
                it.copy(
                    listening = false,
                    available = it.available && failure != DictationFailure.UNAVAILABLE,
                    failure = failure.takeIf { heard.isBlank() }
                )
            }
            if (heard.isNotBlank()) {
                _text.trySend(DictatedText(DictationText.merge(draft, heard), final = true))
            }
        }
    }
}
