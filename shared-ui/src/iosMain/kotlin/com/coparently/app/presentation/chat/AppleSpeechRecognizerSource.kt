@file:OptIn(ExperimentalForeignApi::class)

package com.coparently.app.presentation.chat

import com.coparently.app.data.platform.logger
import com.coparently.app.domain.dictation.DictationFailure
import kotlinx.cinterop.ExperimentalForeignApi
import platform.AVFAudio.AVAudioEngine
import platform.AVFAudio.AVAudioSession
import platform.AVFAudio.AVAudioSessionCategoryRecord
import platform.AVFAudio.AVAudioSessionInterruptionNotification
import platform.AVFAudio.AVAudioSessionModeMeasurement
import platform.AVFAudio.AVAudioSessionSetActiveOptionNotifyOthersOnDeactivation
import platform.AVFAudio.setActive
import platform.Foundation.NSError
import platform.Foundation.NSLocale
import platform.Foundation.NSNotificationCenter
import platform.Foundation.NSOperationQueue
import platform.Speech.SFSpeechAudioBufferRecognitionRequest
import platform.Speech.SFSpeechRecognitionResult
import platform.Speech.SFSpeechRecognitionTask
import platform.Speech.SFSpeechRecognizer
import platform.darwin.NSObjectProtocol

/**
 * [SpeechRecognizerSource] over Apple's Speech framework and the microphone, **on the device only**
 * (CLAUDE.md item 28, owner decision: the audio must not leave the phone).
 *
 * A language counts only where `SFSpeechRecognizer.supportsOnDeviceRecognition` says the phone has
 * its model, and every request sets `requiresOnDeviceRecognition`, so the recognizer refuses rather
 * than send the audio to Apple's servers; there is no other code path. The microphone's buffers go
 * from the input tap straight into the request and are never read, copied or stored. The audio
 * session (category record, mode measurement) is deactivated, telling other apps they may resume,
 * when a session finishes or is abandoned; an interruption (a call, Siri) abandons it.
 */
internal object AppleSpeechRecognizerSource : SpeechRecognizerSource {

    override fun supportsOnDevice(languageTag: String): Boolean =
        recognizerFor(languageTag)?.supportsOnDeviceRecognition == true

    override fun begin(
        languageTag: String,
        onText: (text: String, isFinal: Boolean) -> Unit,
        onFailure: (DictationFailure) -> Unit
    ): SpeechSession? {
        val recognizer = recognizerFor(languageTag)?.takeIf { it.supportsOnDeviceRecognition } ?: return null
        val session = AppleSpeechSession(onText, onFailure)
        return if (session.start(recognizer)) session else null
    }

    /** The recognizer for [languageTag], or null where the phone has none (Apple's initializer may give nil). */
    private fun recognizerFor(languageTag: String): SFSpeechRecognizer? =
        runCatching { SFSpeechRecognizer(locale = NSLocale(localeIdentifier = languageTag)) }.getOrNull()
}

private const val TAG = "AppleSpeech"
private const val INPUT_BUS = 0
private const val TAP_FRAMES = 1024

/** Speech's error codes that mean nothing was said, or nothing could be made out. */
private val NOTHING_HEARD_CODES = setOf(203L, 1110L)

/**
 * One recognition: the engine's input tap feeding an on-device request, the task's results handed
 * on, and the microphone released once — after [finish] on the last result, at [abort], at an
 * interruption or at a failure.
 */
private class AppleSpeechSession(
    private val onText: (text: String, isFinal: Boolean) -> Unit,
    private val onFailure: (DictationFailure) -> Unit
) : SpeechSession {

    private val engine = AVAudioEngine()
    private val request = SFSpeechAudioBufferRecognitionRequest()
    private var task: SFSpeechRecognitionTask? = null
    private var interruption: NSObjectProtocol? = null
    private var microphoneOn = false
    private var ended = false

    /** Starts the microphone and the recognition; false (with everything released) when it cannot. */
    fun start(recognizer: SFSpeechRecognizer): Boolean {
        request.requiresOnDeviceRecognition = true
        request.shouldReportPartialResults = true
        recognizer.queue = NSOperationQueue.mainQueue
        if (!startMicrophone()) {
            release()
            return false
        }
        task = recognizer.recognitionTaskWithRequest(request) { result, error -> answer(result, error) }
        interruption = NSNotificationCenter.defaultCenter.addObserverForName(
            name = AVAudioSessionInterruptionNotification,
            `object` = AVAudioSession.sharedInstance(),
            queue = NSOperationQueue.mainQueue,
        ) { _ -> interrupted() }
        return true
    }

    override fun finish() {
        if (ended) return
        // The request gets no more audio; the task then delivers its last words as final.
        releaseMicrophone()
    }

    override fun abort() {
        if (ended) return
        ended = true
        task?.cancel()
        release()
    }

    private fun startMicrophone(): Boolean {
        val audio = AVAudioSession.sharedInstance()
        val configured = audio.setCategory(AVAudioSessionCategoryRecord, AVAudioSessionModeMeasurement, 0u, null) &&
            audio.setActive(true, null)
        if (!configured) {
            logger.w(TAG, "The audio session could not be set up for recording")
            return false
        }
        microphoneOn = true
        val input = engine.inputNode
        val bus = INPUT_BUS.toULong()
        // The buffers go into the request and nowhere else: audio is never kept.
        input.installTapOnBus(bus, TAP_FRAMES.toUInt(), input.outputFormatForBus(bus)) { buffer, _ ->
            if (buffer != null) request.appendAudioPCMBuffer(buffer)
        }
        engine.prepare()
        if (!engine.startAndReturnError(null)) {
            logger.w(TAG, "The audio engine did not start")
            return false
        }
        return true
    }

    private fun answer(result: SFSpeechRecognitionResult?, error: NSError?) {
        if (ended) return
        if (result != null) {
            val isFinal = result.final
            if (isFinal) {
                ended = true
                release()
            }
            onText(result.bestTranscription.formattedString, isFinal)
        } else if (error != null) {
            ended = true
            release()
            val heardNothing = error.code in NOTHING_HEARD_CODES
            onFailure(if (heardNothing) DictationFailure.NOTHING_HEARD else DictationFailure.OTHER)
        }
    }

    private fun interrupted() {
        if (ended) return
        ended = true
        task?.cancel()
        release()
        onFailure(DictationFailure.OTHER)
    }

    /** Stops the microphone and ends the request's audio, and deactivates the audio session, once. */
    private fun releaseMicrophone() {
        if (!microphoneOn) return
        microphoneOn = false
        engine.stop()
        engine.inputNode.removeTapOnBus(INPUT_BUS.toULong())
        request.endAudio()
        AVAudioSession.sharedInstance().setActive(false, AVAudioSessionSetActiveOptionNotifyOthersOnDeactivation, null)
    }

    private fun release() {
        releaseMicrophone()
        interruption?.let { NSNotificationCenter.defaultCenter.removeObserver(it) }
        interruption = null
        task = null
    }
}
