package com.coparently.app.presentation.chat

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import com.coparently.app.domain.model.Conversation
import com.coparently.app.presentation.common.BarLeading
import com.coparently.app.presentation.common.Loadable
import com.coparently.app.presentation.common.PushedBarsHarness
import com.coparently.app.testing.RobolectricTestApplication
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.datetime.LocalDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The chat's bars (MON-24 phase 3, part B, Task 7): where a shell draws the bars (the iPhone, a
 * recording host here) a thread hands it its header as the title view, search and the overflow as
 * a menu of today's entries, and while searching the search field with a close button; the
 * conversation list hands it a large title, the gear and its "+". With no host (Android) both draw
 * the Material bars, the overflow menu and the floating button they always drew.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = RobolectricTestApplication::class, qualifiers = "w393dp-h851dp")
class ChatBarsTest : PushedBarsHarness() {

    // region The thread

    @Test
    fun `the thread's bar carries its header, search and the overflow menu`() {
        compose(withHost = true) { threadBar() }
        rule.waitForIdle()
        val bar = shown.last()
        assertEquals("Bob", bar.title)
        assertNotNull("The header is not the title view", bar.titleContent)
        assertEquals(BarLeading.None, bar.leading)
        assertEquals(
            listOf("magnifyingglass" to "Search this conversation", "ellipsis.circle" to "More actions"),
            bar.actions.map { it.sfSymbol to it.label }
        )
        assertNull(bar.actions[0].menu)
        val menu = bar.actions[1].menu.orEmpty()
        assertEquals(listOf("Settings", "Block and stop sharing"), menu.map { it.label })
        assertEquals(listOf("gearshape", "nosign"), menu.map { it.sfSymbol })
        assertEquals(listOf(false, true), menu.map { it.destructive })

        clickAction("Search this conversation")
        rule.runOnIdle { menu.forEach { it.onClick() } }
        assertEquals(listOf("search", "settings", "block"), clicks)
    }

    @Test
    fun `a thread pushed over the list goes back`() {
        compose(withHost = true) { threadBar(onBack = { clicks += "up" }) }
        rule.waitForIdle()
        val leading = shown.last().leading
        assertTrue("The leading is $leading", leading is BarLeading.Back)
        assertEquals("Back", (leading as BarLeading.Back).label)
        rule.runOnIdle { leading.onClick() }
        assertEquals(listOf("up"), clicks)
    }

    @Test
    fun `the overflow leaves out what today's leaves out`() {
        compose(withHost = true) { threadBar(onOpenSettings = null) }
        rule.waitForIdle()
        assertEquals(listOf("Block and stop sharing"), shown.last().actions[1].menu.orEmpty().map { it.label })
    }

    @Test
    fun `with no entries there is no overflow`() {
        compose(withHost = true) { threadBar(onOpenSettings = null, onBlock = null) }
        rule.waitForIdle()
        assertEquals(listOf("magnifyingglass"), shown.last().actions.map { it.sfSymbol })
    }

    @Test
    fun `while searching the bar is the search field and its close button closes the search`() {
        compose(withHost = true) {
            var query by remember { mutableStateOf<String?>("") }
            threadBar(searchQuery = query, onOpenSearch = { query = "" }, onCloseSearch = { query = null })
        }
        rule.waitForIdle()
        val searching = shown.last()
        assertEquals("Search this conversation", searching.title)
        assertNotNull("The search field is not the title view", searching.titleContent)
        assertTrue(searching.actions.isEmpty())
        val leading = searching.leading
        assertTrue("The leading is $leading", leading is BarLeading.Close)
        assertEquals("Close search", (leading as BarLeading.Close).label)

        rule.runOnIdle { leading.onClick() }
        rule.waitForIdle()
        val thread = shown.last()
        assertEquals("Bob", thread.title)
        assertEquals(BarLeading.None, thread.leading)
        assertEquals(listOf("magnifyingglass", "ellipsis.circle"), thread.actions.map { it.sfSymbol })
    }

    @Test
    fun `on Android the thread draws its own bar and overflow menu`() {
        compose(withHost = false) { threadBar() }
        rule.waitForIdle()
        assertTrue("A bar reached a host on Android", shown.isEmpty())
        assertTrue(rule.onAllNodesWithText(HEADER).fetchSemanticsNodes().isNotEmpty())
        assertTrue(
            rule.onAllNodesWithContentDescription("Search this conversation").fetchSemanticsNodes().isNotEmpty()
        )
        rule.onNodeWithContentDescription("More actions").performClick()
        rule.waitForIdle()
        assertTrue(rule.onAllNodesWithText("Settings").fetchSemanticsNodes().isNotEmpty())
        assertTrue(rule.onAllNodesWithText("Block and stop sharing").fetchSemanticsNodes().isNotEmpty())
    }

    // endregion

    // region The conversation list

    @Test
    fun `the list's bar has a large title, the gear and the plus, and no floating button`() {
        val viewModel = chatViewModel(listOf(conversation("one", "Bob"), conversation("two", "Carol")))
        compose(withHost = true) { listScreen(viewModel) }
        rule.waitForIdle()
        val bar = shown.last()
        assertEquals("Messages", bar.title)
        assertTrue("No large title", bar.largeTitle)
        assertEquals(BarLeading.None, bar.leading)
        assertEquals(
            listOf("gearshape" to "Settings", "plus" to "New conversation"),
            bar.actions.map { it.sfSymbol to it.label }
        )
        assertEquals(0, rule.onAllNodesWithContentDescription("New conversation").fetchSemanticsNodes().size)

        clickAction("Settings")
        clickAction("New conversation")
        assertEquals(listOf("settings"), clicks)
        verify { viewModel.startConversationWithPartner(any()) }
    }

    @Test
    fun `the list's plus waits while a conversation opens`() {
        val viewModel = chatViewModel(listOf(conversation("one", "Bob"), conversation("two", "Carol")), opening = true)
        compose(withHost = true) { listScreen(viewModel) }
        rule.waitForIdle()
        assertFalse(shown.last().actions.single { it.sfSymbol == "plus" }.enabled)
    }

    @Test
    fun `an empty list's bar has only the gear`() {
        compose(withHost = true) { listScreen(chatViewModel(emptyList())) }
        rule.waitForIdle()
        assertEquals(listOf("gearshape"), shown.last().actions.map { it.sfSymbol })
    }

    @Test
    fun `on Android the list draws its own bar and floating button`() {
        val viewModel = chatViewModel(listOf(conversation("one", "Bob"), conversation("two", "Carol")))
        compose(withHost = false) { listScreen(viewModel) }
        rule.waitForIdle()
        assertTrue("A bar reached a host on Android", shown.isEmpty())
        assertTrue(rule.onAllNodesWithText("Messages").fetchSemanticsNodes().isNotEmpty())
        assertTrue(rule.onAllNodesWithContentDescription("Settings").fetchSemanticsNodes().isNotEmpty())
        assertEquals(1, rule.onAllNodesWithContentDescription("New conversation").fetchSemanticsNodes().size)
    }

    // endregion

    // One parameter per callback of the bar, each with the default most cases want.
    @Suppress("LongParameterList")
    @Composable
    private fun threadBar(
        searchQuery: String? = null,
        onOpenSearch: () -> Unit = { clicks += "search" },
        onCloseSearch: () -> Unit = { clicks += "close" },
        onBack: (() -> Unit)? = null,
        onOpenSettings: (() -> Unit)? = { clicks += "settings" },
        onBlock: (() -> Unit)? = { clicks += "block" }
    ) {
        ChatTopBar(
            title = "Bob",
            searchQuery = searchQuery,
            onSearchQueryChange = {},
            onOpenSearch = onOpenSearch,
            onCloseSearch = onCloseSearch,
            onBack = onBack,
            onOpenSettings = onOpenSettings,
            onBlock = onBlock
        ) {
            Text(HEADER)
        }
    }

    @Composable
    private fun listScreen(viewModel: ChatViewModel) {
        ConversationsScreen(
            onConversationClick = {},
            onNavigateToPairing = {},
            onOpenSettings = { clicks += "settings" },
            viewModel = viewModel
        )
    }

    private fun chatViewModel(conversations: List<Conversation>, opening: Boolean = false): ChatViewModel =
        mockk(relaxed = true) {
            every { this@mockk.conversations } returns MutableStateFlow(Loadable.Loaded(conversations))
            every { isOpeningConversation } returns MutableStateFlow(opening)
            every { currentUserId } returns MutableStateFlow("alice")
            every { events } returns MutableSharedFlow()
        }

    private fun conversation(id: String, title: String) = Conversation(
        id = id,
        participants = listOf("alice", id),
        title = title,
        createdAt = LocalDateTime(2026, 5, 1, 9, 0)
    )

    private companion object {
        const val HEADER = "Bob · up to date"
    }
}
