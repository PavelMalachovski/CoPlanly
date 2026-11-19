@file:OptIn(ExperimentalAtomicApi::class)

package com.coparently.app.presentation.chat

import com.coparently.app.domain.dictation.DictationFailure
import com.coparently.app.domain.dictation.DictationListener
import com.coparently.app.domain.dictation.SpeechDictation
import platform.Foundation.NSLocale
import platform.Foundation.NSNotificationCenter
import platform.Foundation.NSOperationQueue
import platform.Foundation.NSUserDefaults
import platform.Foundation.preferredLanguages
import platform.UIKit.UIApplicationDidEnterBackgroundNotification
import platform.darwin.NSObjectProtocol
import platform.darwin.dispatch_async
import platform.darwin.dispatch_get_global_queue
import platform.posix.QOS_CLASS_UTILITY
import kotlin.concurrent.atomics.AtomicReference
import kotlin.concurrent.atomics.ExperimentalAtomicApi

/**
 * The phone's speech recognizer, as [IosSpeechDictation] needs it: Apple's Speech framework in the
 * app ([AppleSpeechRecognizerSource]), a fake in the tests. On the device only, always (CLAUDE.md
 * item 28): an implementation never starts a recognition that may send the audio to a server.
 */
internal interface SpeechRecognizerSource {

    /** Whether the phone recognizes [languageTag] on the device; false when it has no recognizer for it. */
    fun supportsOnDevice(languageTag: String): Boolean

    /**
     * Starts recognizing [languageTag] with on-device recognition only; null when it cannot start.
     *
     * @param onText The words heard so far, and whether they are the session's last
     * @param onFailure Why the session ended without its last words
     */
    fun begin(
        languageTag: String,
        onText: (text: String, isFinal: Boolean) -> Unit,
        onFailure: (DictationFailure) -> Unit
    ): SpeechSession?
}

/** One recognition [SpeechRecognizerSource.begin] started. */
internal interface SpeechSession {

    /** Stops listening; the recognizer then delivers its last words through `onText`. */
    fun finish()

    /** Abandons the recognition and releases the microphone; no callback follows. */
    fun abort()
}

/**
 * The language to dictate in on the iPhone: the parent's own words are in the language they speak.
 * The first entry of `AppleLanguages` in the app's defaults (where iOS writes the per-app language a
 * parent chose in Settings), else the phone's first preferred language, else English. Never the
 * app's `en-150` fallback, as on Android (`rememberDictationLanguageTag`): a Slovak phone dictates
 * Slovak.
 */
internal fun phoneDictationLanguage(): String {
    val chosen = NSUserDefaults.standardUserDefaults.stringArrayForKey(APPLE_LANGUAGES)
        ?.firstOrNull() as? String
    val preferred = NSLocale.preferredLanguages.firstOrNull() as? String
    return chosen?.takeIf { it.isNotBlank() } ?: preferred?.takeIf { it.isNotBlank() } ?: FALLBACK_LANGUAGE
}

private const val APPLE_LANGUAGES = "AppleLanguages"
private const val FALLBACK_LANGUAGE = "en"

/** Replaces the value by [transform] of it, atomically, and answers the value it replaced. */
private inline fun <T> AtomicReference<T>.replaceBy(transform: (T) -> T): T {
    while (true) {
        val previous = load()
        if (compareAndSet(previous, transform(previous))) return previous
    }
}

/**
 * Runs [work] on a background queue of utility priority: asking Speech whether a language has an
 * on-device model talks to the speech daemon synchronously, which must never block the main thread.
 */
internal fun offMainThread(work: () -> Unit) {
    dispatch_async(dispatch_get_global_queue(QOS_CLASS_UTILITY.toLong(), 0u)) { work() }
}

/**
 * [SpeechDictation] on the iPhone (phase 4 part E1): the phone's own recognizer, on the device only
 * (CLAUDE.md item 28). Where [source] has no on-device recognizer for the language, [isAvailable] is
 * false and the composer draws no microphone; there is no fallback to Apple's server recognition.
 *
 * One session at a time: a [start] while one runs abandons it silently and starts anew, as the
 * domain contract says and Android does (a session [stop] asked to finish, whose recognizer never
 * answers, is replaced the same way). Callbacks of a session that is no longer current are dropped.
 * The session also ends when the app goes to the background ([onBackground], registered for
 * `UIApplicationDidEnterBackgroundNotification` at construction).
 *
 * **Speech is never asked on the main thread whether a language is supported.** The question talks
 * to the speech daemon synchronously, which on a cold phone held the main thread for seconds. The
 * answer is cached per language tag, computed through [checkOffMain] and published atomically;
 * [isAvailable] and [start] read the cache, and while a tag has no answer yet they start its check
 * and say no (no microphone rather than a hang, design item 8). [warm] starts the check early: the
 * app calls it at start for the phone's dictation language, so the composer usually finds it done.
 *
 * @param languageTag The tag a [start] without one dictates in, and the one [isAvailable] asks about
 * @param checkOffMain Runs a support check off the main thread ([offMainThread]; a test may run it
 *   at once or later)
 */
internal class IosSpeechDictation(
    private val source: SpeechRecognizerSource,
    private val languageTag: () -> String = ::phoneDictationLanguage,
    private val checkOffMain: (() -> Unit) -> Unit = ::offMainThread,
) : SpeechDictation {

    private var current: Session? = null

    /** Whether each checked language tag has an on-device model; replaced whole, never mutated. */
    private val supported = AtomicReference<Map<String, Boolean>>(emptyMap())

    /** The tags whose check has been started, so each is asked of Speech once. */
    private val checking = AtomicReference<Set<String>>(emptySet())

    /**
     * The background observer's token, the one handle that could remove it. Kept for the life of
     * the dictation, which `platformUiModule` holds as a single for the process.
     */
    internal val backgroundObserver: NSObjectProtocol = NSNotificationCenter.defaultCenter.addObserverForName(
        name = UIApplicationDidEnterBackgroundNotification,
        `object` = null,
        queue = NSOperationQueue.mainQueue,
    ) { _ -> onBackground() }

    override fun isAvailable(): Boolean = supports(languageTag())

    /** Starts the support check for [tag] (by default the phone's dictation language), off the main thread. */
    internal fun warm(tag: String = languageTag()) {
        if (supported.load().containsKey(tag)) return
        if (tag in checking.replaceBy { it + tag }) return
        checkOffMain {
            val answer = runCatching { source.supportsOnDevice(tag) }.getOrDefault(false)
            supported.replaceBy { it + (tag to answer) }
        }
    }

    /** The cached answer for [tag]; with none yet, its check is started and the answer is no. */
    private fun supports(tag: String): Boolean {
        supported.load()[tag]?.let { return it }
        warm(tag)
        return supported.load()[tag] ?: false
    }

    override fun start(languageTag: String?, listener: DictationListener) {
        cancel()
        val tag = languageTag?.takeIf { it.isNotBlank() } ?: languageTag()
        if (!supports(tag)) {
            listener.onFailure(DictationFailure.LANGUAGE_UNAVAILABLE)
            return
        }
        val session = Session(listener)
        current = session
        val started = source.begin(
            languageTag = tag,
            onText = { text, isFinal -> session.text(text, isFinal) },
            onFailure = { failure -> session.fail(failure) },
        )
        if (current !== session) return
        if (started == null) {
            current = null
            listener.onFailure(DictationFailure.UNAVAILABLE)
        } else {
            session.recognition = started
        }
    }

    override fun stop() {
        current?.recognition?.finish()
    }

    override fun cancel() {
        val session = current ?: return
        current = null
        session.recognition?.abort()
    }

    /**
     * The app went to the background: the session ends and the microphone is released at once
     * (CLAUDE.md item 28: the session ends when the app stops), as Android's `ON_STOP` cancel ends
     * it. With nothing heard there is no callback, as for [cancel]: the screen's own `ON_STOP` cancel
     * ends the composer's "listening", and a "nothing heard" line for a session the parent never
     * ended would be wrong. Words already heard are handed on as the final text, a safety net so the
     * composer is not left listening to a stopped microphone should that cancel not arrive (it
     * ignores the callback when it did). Nothing happens without a session.
     */
    internal fun onBackground() {
        val session = current ?: return
        current = null
        session.recognition?.abort()
        if (session.heard.isNotBlank()) session.listener.onFinal(session.heard)
    }

    /** One [start]'s listener and recognition; its callbacks count only while it is [current]. */
    private inner class Session(val listener: DictationListener) {
        var recognition: SpeechSession? = null
        var heard: String = ""

        fun text(text: String, isFinal: Boolean) {
            if (current !== this) return
            heard = text
            if (isFinal) {
                current = null
                listener.onFinal(text)
            } else {
                listener.onPartial(text)
            }
        }

        fun fail(failure: DictationFailure) {
            if (current !== this) return
            current = null
            listener.onFailure(failure)
        }
    }
}
