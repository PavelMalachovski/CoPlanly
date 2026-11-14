package com.coparently.app.ios.shell

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The shell's answer to `LocalScreenVisible` (MON-24 phase 3, part B, Task 3): a screen is seen only
 * while its tab is selected, it is on top of its stack and the app is in the foreground, so a Chat
 * tab left open stops marking messages read.
 */
class ScreenVisibilityIosTest {

    private val shell = ShellVisibility()
    private val chat = shell.stack(tab = CHAT)
    private val home = shell.stack(tab = HOME)

    private fun chatThreadShown() {
        shell.selectedStack = CHAT
        chat.topIds = setOf(THREAD)
        home.topIds = setOf(HOME_SCREEN)
    }

    @Test
    fun `the top screen of the selected tab is seen`() {
        chatThreadShown()

        assertTrue(chat.of(THREAD).value)
        assertFalse(home.of(HOME_SCREEN).value)
    }

    @Test
    fun `a stack is hidden once another tab is selected and seen again on return`() {
        chatThreadShown()
        val thread = chat.of(THREAD)

        shell.selectedStack = HOME
        assertFalse(thread.value)
        assertTrue(home.of(HOME_SCREEN).value)

        shell.selectedStack = CHAT
        assertTrue(thread.value)
    }

    @Test
    fun `nothing is seen while the app is in the background`() {
        chatThreadShown()
        val thread = chat.of(THREAD)

        shell.foreground = false
        assertFalse(thread.value)

        shell.foreground = true
        assertTrue(thread.value)
    }

    @Test
    fun `a screen pushed over the thread hides it and a sheet over it does not`() {
        chatThreadShown()
        val thread = chat.of(THREAD)

        chat.topIds = setOf(PUSHED)
        assertFalse(thread.value)

        chat.topIds = setOf(THREAD, SHEET)
        assertTrue(thread.value)
        assertTrue(chat.of(SHEET).value)
    }

    @Test
    fun `the root flow is never hidden by a tab`() {
        val flow = shell.stack(tab = null)
        flow.topIds = setOf(THREAD)
        shell.selectedStack = HOME

        assertTrue(flow.of(THREAD).value)
        shell.foreground = false
        assertFalse(flow.of(THREAD).value)
    }

    private companion object {
        const val CHAT = "chat"
        const val HOME = "home"
        const val THREAD = "entry-thread"
        const val PUSHED = "entry-pushed"
        const val SHEET = "entry-sheet"
        const val HOME_SCREEN = "entry-home"
    }
}
