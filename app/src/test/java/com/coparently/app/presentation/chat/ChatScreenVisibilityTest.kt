package com.coparently.app.presentation.chat

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.createComposeRule
import com.coparently.app.presentation.common.LocalScreenVisible
import com.coparently.app.testing.RobolectricTestApplication
import io.mockk.mockk
import io.mockk.verify
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * A chat thread is open in its ViewModel — which is what lets it mark the co-parent's messages read
 * — only while the parent sees it (MON-24 phase 3, part B, Task 3). The iPhone's shell keeps a hidden
 * tab's screens composed and says so through `LocalScreenVisible`; Android provides nothing, and its
 * thread is open for as long as the screen is composed, as before.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = RobolectricTestApplication::class)
class ChatScreenVisibilityTest {

    @get:Rule
    val rule = createComposeRule()

    private val viewModel = mockk<ChatViewModel>(relaxed = true)

    @Test
    fun `a thread that stops being visible is closed once and reopens when shown again`() {
        val visible: MutableState<Boolean> = mutableStateOf(true)
        rule.setContent {
            CompositionLocalProvider(LocalScreenVisible provides visible) {
                ChatThreadOpenEffect(CONVERSATION, viewModel)
            }
        }
        rule.waitForIdle()
        verify(exactly = 1) { viewModel.onThreadOpened(CONVERSATION) }
        verify(exactly = 0) { viewModel.onThreadClosed() }

        rule.runOnIdle { visible.value = false }
        rule.waitForIdle()
        verify(exactly = 1) { viewModel.onThreadClosed() }
        verify(exactly = 1) { viewModel.onThreadOpened(CONVERSATION) }

        rule.runOnIdle { visible.value = true }
        rule.waitForIdle()
        verify(exactly = 2) { viewModel.onThreadOpened(CONVERSATION) }
        verify(exactly = 1) { viewModel.onThreadClosed() }
    }

    @Test
    fun `a thread composed hidden is not opened until it is shown`() {
        val visible: MutableState<Boolean> = mutableStateOf(false)
        rule.setContent {
            CompositionLocalProvider(LocalScreenVisible provides visible) {
                ChatThreadOpenEffect(CONVERSATION, viewModel)
            }
        }
        rule.waitForIdle()
        verify(exactly = 0) { viewModel.onThreadOpened(any()) }

        rule.runOnIdle { visible.value = true }
        rule.waitForIdle()
        verify(exactly = 1) { viewModel.onThreadOpened(CONVERSATION) }
    }

    @Test
    fun `a visible thread that leaves composition is closed`() {
        var shown by mutableStateOf(true)
        val visible = mutableStateOf(true)
        rule.setContent {
            CompositionLocalProvider(LocalScreenVisible provides visible) {
                if (shown) ChatThreadOpenEffect(CONVERSATION, viewModel)
            }
        }
        rule.waitForIdle()
        rule.runOnIdle { shown = false }
        rule.waitForIdle()
        verify(exactly = 1) { viewModel.onThreadOpened(CONVERSATION) }
        verify(exactly = 1) { viewModel.onThreadClosed() }
    }

    @Test
    fun `with no visibility the thread is opened once and never closed while composed`() {
        rule.setContent { ChatThreadOpenEffect(CONVERSATION, viewModel) }
        rule.waitForIdle()
        rule.runOnIdle { }
        verify(exactly = 1) { viewModel.onThreadOpened(CONVERSATION) }
        verify(exactly = 0) { viewModel.onThreadClosed() }
    }

    @Test
    fun `with no visibility a new conversation closes the old thread and opens the new`() {
        var conversation by mutableStateOf(CONVERSATION)
        rule.setContent { ChatThreadOpenEffect(conversation, viewModel) }
        rule.waitForIdle()
        rule.runOnIdle { conversation = OTHER }
        rule.waitForIdle()
        verify(exactly = 1) { viewModel.onThreadOpened(CONVERSATION) }
        verify(exactly = 1) { viewModel.onThreadClosed() }
        verify(exactly = 1) { viewModel.onThreadOpened(OTHER) }
    }

    private companion object {
        const val CONVERSATION = "alice_bob"
        const val OTHER = "alice_carol"
    }
}
