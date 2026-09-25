package com.coparently.app.domain.dictation

/**
 * Voice dictation for the chat composer: the parent speaks and the words appear in the field.
 *
 * **On the phone, and only on the phone** (owner decision, September 2026). An implementation
 * must turn speech into text without the audio leaving the device, and must say it is unavailable
 * rather than fall back to a recognizer that may send the audio to a server. Nothing here records,
 * stores or uploads audio; the text goes nowhere until the parent sends it as an ordinary message.
 * It is not a premium feature and it is not "AI" — it is the phone's own speech service.
 *
 * Calls are made on the main thread, and the listener is called back on it.
 */
interface SpeechDictation {

    /**
     * True when this phone can recognise speech on the device. When it is false the composer
     * shows no microphone at all: a button that could not keep the promise is design item 8.
     */
    fun isAvailable(): Boolean

    /**
     * Starts listening, replacing any session still running.
     *
     * @param languageTag BCP 47 tag of the language to recognise (the app's own language), or
     *   null for the recognizer's default
     * @param listener Receives the partial and final text, or the reason it stopped
     */
    fun start(languageTag: String?, listener: DictationListener)

    /** Stops listening and delivers whatever was heard as the final result. */
    fun stop()

    /** Abandons the session: no further callback arrives for it. */
    fun cancel()
}

/** Callbacks of one [SpeechDictation] session, on the main thread. */
interface DictationListener {

    /** The words heard so far; replaced by the next call, not appended to. */
    fun onPartial(text: String)

    /** The session ended with [text] (possibly blank). No further callback follows. */
    fun onFinal(text: String)

    /** The session ended without a result, for [failure]. No further callback follows. */
    fun onFailure(failure: DictationFailure)
}

/** Why a dictation session ended without text — each has its own sentence on screen. */
enum class DictationFailure {
    /** Nothing was said, or nothing could be made out. */
    NOTHING_HEARD,

    /** The phone has no on-device model for the requested language. */
    LANGUAGE_UNAVAILABLE,

    /** The speech service is serving another app. */
    BUSY,

    /** The microphone permission is missing or was refused. */
    PERMISSION,

    /** On-device recognition is not (or no longer) available on this phone. */
    UNAVAILABLE,

    /** Anything else: the microphone failed, the service stopped. */
    OTHER
}

/** How dictated words join the draft the parent had already typed. */
object DictationText {

    /**
     * [spoken] appended to [draft], separated by one space unless the draft is empty or already
     * ends in whitespace. Dictation adds to what was typed; it never replaces it.
     */
    fun merge(draft: String, spoken: String): String {
        val words = spoken.trim()
        return when {
            words.isEmpty() -> draft
            draft.isEmpty() || draft.last().isWhitespace() -> draft + words
            else -> "$draft $words"
        }
    }
}
