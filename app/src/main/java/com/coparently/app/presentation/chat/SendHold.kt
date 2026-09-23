package com.coparently.app.presentation.chat

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * A message the composer has let go of but that has not been handed to the repository yet.
 *
 * @property conversationId The thread it is for.
 * @property text What was typed — returned to the composer on Undo.
 * @property secondsLeft Whole seconds until it is sent, for the "Sending in N s…" line.
 */
data class PendingSend(
    val conversationId: String,
    val text: String,
    val secondsLeft: Int
)

/**
 * "Pause before sending" (MON-19): holds one outgoing message for [seconds] with an Undo, then
 * sends it.
 *
 * The message does not exist anywhere but here until the pause ends — not in Room, not in the
 * outbox, not on the server — so Undo is a real undo rather than a delete the co-parent's phone
 * might already have mirrored. The cost is that a held message lives only in memory, which is why
 * `ChatViewModel.onCleared` hands it back to the draft store rather than losing it.
 *
 * One message at a time: holding a second one sends the first immediately, so the conversation
 * keeps the order it was typed in.
 *
 * @param scope Where the countdown runs; the ViewModel's scope, so it dies with the screen.
 * @param seconds How long a message is held.
 */
class SendHold(
    private val scope: CoroutineScope,
    private val seconds: Int = PAUSE_SECONDS
) {
    private val _pending = MutableStateFlow<PendingSend?>(null)

    /** The held message, or null when nothing is waiting. */
    val pending: StateFlow<PendingSend?> = _pending.asStateFlow()

    private var countdown: Job? = null
    private var release: (() -> Unit)? = null

    /**
     * Holds [text] and runs [send] when the pause ends, unless [undo] comes first.
     *
     * @param send Hands the message to the repository. Runs at most once.
     */
    fun hold(conversationId: String, text: String, send: () -> Unit) {
        sendNow()
        release = send
        // Set before the countdown starts, so the line appears in the same frame the composer
        // empties and an Undo in that frame already has something to undo.
        _pending.value = PendingSend(conversationId, text, seconds)
        countdown = scope.launch {
            for (left in seconds downTo 1) {
                _pending.value = PendingSend(conversationId, text, left)
                delay(TICK_MS)
            }
            countdown = null
            deliverHeld()
        }
    }

    /** Sends whatever is held now, without waiting out the pause. */
    fun sendNow() {
        countdown?.cancel()
        countdown = null
        deliverHeld()
    }

    /**
     * Cancels the held message and returns it, or null when nothing was held (the pause may have
     * ended a moment ago — the message is then already on its way, and Undo has nothing to take).
     */
    fun undo(): PendingSend? {
        val held = _pending.value
        countdown?.cancel()
        countdown = null
        release = null
        _pending.value = null
        return held
    }

    private fun deliverHeld() {
        val send = release ?: return
        release = null
        _pending.value = null
        send()
    }

    companion object {
        /** How long a message waits for an Undo. Gmail's default undo-send window. */
        const val PAUSE_SECONDS = 5

        /** One countdown step. */
        const val TICK_MS = 1000L

        /**
         * The composer's text after an Undo: the held message, followed by anything typed since on
         * its own line — neither is thrown away.
         */
        fun restore(held: String, composer: String): String =
            if (composer.isBlank()) held else "$held\n$composer"
    }
}
