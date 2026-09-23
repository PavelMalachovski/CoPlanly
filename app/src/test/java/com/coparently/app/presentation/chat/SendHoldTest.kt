package com.coparently.app.presentation.chat

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * The hold behind "Pause before sending" (MON-19), on its own: the message is sent exactly once,
 * and only when the pause ran out without an Undo.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SendHoldTest {

    @Test
    fun `a held message is sent once, when the pause runs out`() = runTest {
        val hold = SendHold(this)
        var sent = 0

        hold.hold(CONVERSATION, TEXT) { sent++ }
        advanceTimeBy(SendHold.PAUSE_SECONDS * SendHold.TICK_MS - 1)
        runCurrent()
        assertEquals(0, sent)
        assertEquals(1, hold.pending.value?.secondsLeft)

        advanceTimeBy(1)
        runCurrent()
        assertEquals(1, sent)
        assertNull(hold.pending.value)

        advanceUntilIdle()
        assertEquals(1, sent)
    }

    @Test
    fun `undo cancels the send and returns what was held`() = runTest {
        val hold = SendHold(this)
        var sent = 0

        hold.hold(CONVERSATION, TEXT) { sent++ }
        runCurrent()
        val held = hold.undo()
        advanceUntilIdle()

        assertEquals(PendingSend(CONVERSATION, TEXT, SendHold.PAUSE_SECONDS), held)
        assertEquals(0, sent)
        assertNull(hold.pending.value)
    }

    @Test
    fun `undo with nothing held returns nothing`() = runTest {
        assertNull(SendHold(this).undo())
    }

    @Test
    fun `holding a second message sends the first at once`() = runTest {
        val hold = SendHold(this)
        val sent = mutableListOf<String>()

        hold.hold(CONVERSATION, "first") { sent += "first" }
        runCurrent()
        hold.hold(CONVERSATION, "second") { sent += "second" }
        runCurrent()

        assertEquals(listOf("first"), sent)
        assertEquals("second", hold.pending.value?.text)

        advanceUntilIdle()
        assertEquals(listOf("first", "second"), sent)
    }

    @Test
    fun `restoring puts the held text first and keeps what was typed since`() {
        assertEquals(TEXT, SendHold.restore(TEXT, ""))
        assertEquals(TEXT, SendHold.restore(TEXT, "  "))
        assertEquals("$TEXT\nmore", SendHold.restore(TEXT, "more"))
    }

    private companion object {
        const val CONVERSATION = "user-a__user-b"
        const val TEXT = "See you at five."
    }
}
