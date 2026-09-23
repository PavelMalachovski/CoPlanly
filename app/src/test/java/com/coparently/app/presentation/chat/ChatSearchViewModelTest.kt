package com.coparently.app.presentation.chat

import com.coparently.app.domain.chat.ChatSearchHit
import com.coparently.app.domain.chat.ChatSearchResult
import com.coparently.app.domain.chat.SearchSnippet
import com.coparently.app.domain.model.Message
import com.coparently.app.domain.repository.ChatSearchRepository
import com.coparently.app.presentation.common.testParentsSource
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

/**
 * [ChatSearchViewModel] (MON-15): the debounce, the one-conversation bound, and the jump to a
 * result. Matching itself is `ChatSearchTest`'s; here the repository is a stub, because what is
 * under test is when it is asked, and what for.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ChatSearchViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private val found = ChatSearchResult(listOf(hit()), truncated = false)
    private lateinit var repository: ChatSearchRepository

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        repository = mockk {
            coEvery { search(CONVERSATION, any()) } returns found
            coEvery { countMessagesSince(CONVERSATION, SENT_AT) } returns WINDOW_NEEDED
        }
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `typing is searched once, after the pause, with the last query`() = runTest {
        val viewModel = openSearch()

        viewModel.onQueryChange("pi")
        viewModel.onQueryChange("pic")
        viewModel.onQueryChange("pickup")
        advanceTimeBy(ChatSearchViewModel.SEARCH_DEBOUNCE_MS - 1)
        runCurrent()

        coVerify(exactly = 0) { repository.search(any(), any()) }
        assertEquals(ChatSearchState.Prompt, viewModel.state.value)

        advanceTimeBy(1)
        runCurrent()

        coVerify(exactly = 1) { repository.search(CONVERSATION, "pickup") }
        assertEquals(ChatSearchState.Results("pickup", found), viewModel.state.value)
    }

    @Test
    fun `a query too short to search runs nothing and shows the prompt`() = runTest {
        val viewModel = openSearch()
        viewModel.onQueryChange("pickup")
        advanceUntilIdle()

        viewModel.onQueryChange("p")
        advanceUntilIdle()

        assertEquals(ChatSearchState.Prompt, viewModel.state.value)
        coVerify(exactly = 1) { repository.search(any(), any()) }
    }

    @Test
    fun `nothing matching is an answer, not a failure`() = runTest {
        coEvery { repository.search(CONVERSATION, "zebra") } returns ChatSearchResult.EMPTY
        val viewModel = openSearch()

        viewModel.onQueryChange("zebra")
        advanceUntilIdle()

        assertEquals(ChatSearchState.Results("zebra", ChatSearchResult.EMPTY), viewModel.state.value)
    }

    @Test
    fun `a failed local read says so instead of claiming no results`() = runTest {
        coEvery { repository.search(CONVERSATION, "pickup") } throws IllegalStateException("database closed")
        val viewModel = openSearch()

        viewModel.onQueryChange("pickup")
        advanceUntilIdle()

        assertEquals(ChatSearchState.Failed, viewModel.state.value)
    }

    @Test
    fun `only the conversation search was opened over is searched`() = runTest {
        val viewModel = openSearch()

        viewModel.onQueryChange("pickup")
        advanceUntilIdle()

        coVerify(exactly = 1) { repository.search(CONVERSATION, "pickup") }
        coVerify(exactly = 0) { repository.search(neq(CONVERSATION), any()) }
    }

    @Test
    fun `typing after search closed runs nothing`() = runTest {
        val viewModel = openSearch()
        viewModel.onQueryChange("pic")
        viewModel.close()

        viewModel.onQueryChange("pickup")
        advanceUntilIdle()

        assertEquals(ChatSearchState.Closed, viewModel.state.value)
        coVerify(exactly = 0) { repository.search(any(), any()) }
    }

    @Test
    fun `a different thread closes a search opened over another`() = runTest {
        val viewModel = openSearch()

        viewModel.onThreadShown(CONVERSATION)
        assertEquals(ChatSearchState.Prompt, viewModel.state.value)

        viewModel.onThreadShown("another-family")
        assertEquals(ChatSearchState.Closed, viewModel.state.value)
    }

    @Test
    fun `choosing a result closes search and asks for a window that reaches it`() = runTest {
        val viewModel = openSearch()
        val jumps = mutableListOf<ChatSearchJump>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.jumps.toList(jumps) }

        viewModel.select(hit())
        advanceUntilIdle()

        assertEquals(ChatSearchState.Closed, viewModel.state.value)
        assertEquals(listOf(ChatSearchJump(MESSAGE_ID, WINDOW_NEEDED)), jumps)
    }

    private fun openSearch(): ChatSearchViewModel =
        ChatSearchViewModel(repository, testParentsSource()).also { it.open(CONVERSATION) }

    private fun hit() = ChatSearchHit(
        message = Message(
            id = MESSAGE_ID,
            conversationId = CONVERSATION,
            senderId = "user-b",
            senderName = "Bob",
            content = "pickup at five",
            sentAtMillis = SENT_AT
        ),
        snippet = SearchSnippet("pickup at five", 0, 6)
    )

    private companion object {
        const val CONVERSATION = "user-a__user-b"
        const val MESSAGE_ID = "m-42"
        const val SENT_AT = 1_785_578_400_000L
        const val WINDOW_NEEDED = 120
    }
}
