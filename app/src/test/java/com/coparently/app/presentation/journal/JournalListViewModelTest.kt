package com.coparently.app.presentation.journal

import com.coparently.app.domain.journal.JournalEntry
import com.coparently.app.domain.repository.JournalRepository
import com.coparently.app.domain.repository.UserRepository
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test
import java.time.LocalDate
import kotlin.test.assertEquals
import kotlin.test.assertIs

/** The private journal's list (MON-22): the signed-in parent's entries, and delete with Undo. */
@OptIn(ExperimentalCoroutinesApi::class)
class JournalListViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private val repository = mockk<JournalRepository>(relaxed = true)
    private val userRepository = mockk<UserRepository>()
    private val entry = JournalEntry(
        id = "j1",
        entryDate = LocalDate.of(2026, 9, 1),
        text = "Late pickup",
        createdAtMillis = 1_000L,
        updatedAtMillis = 2_000L,
        familyId = "alice__bob",
        createdByFirebaseUid = ALICE
    )

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `the list is the signed-in parent's own entries`() = runTest(dispatcher) {
        every { userRepository.observeCurrentUserId() } returns flowOf(ALICE)
        every { repository.observeEntries(ALICE) } returns flowOf(listOf(entry))
        val model = JournalListViewModel(repository, userRepository)
        backgroundScope.launch { model.state.collect {} }
        advanceUntilIdle()

        assertEquals(listOf(entry), assertIs<JournalListState.Loaded>(model.state.value).entries)
    }

    @Test
    fun `signed out, nobody's entries are shown and none are read`() = runTest(dispatcher) {
        every { userRepository.observeCurrentUserId() } returns flowOf(null)
        val model = JournalListViewModel(repository, userRepository)
        backgroundScope.launch { model.state.collect {} }
        advanceUntilIdle()

        assertEquals(emptyList(), assertIs<JournalListState.Loaded>(model.state.value).entries)
        verify(exactly = 0) { repository.observeEntries(any()) }
    }

    @Test
    fun `a change of account follows the new account's entries`() = runTest(dispatcher) {
        val account = MutableStateFlow<String?>(ALICE)
        every { userRepository.observeCurrentUserId() } returns account
        every { repository.observeEntries(ALICE) } returns flowOf(listOf(entry))
        every { repository.observeEntries(BOB) } returns flowOf(emptyList())
        val model = JournalListViewModel(repository, userRepository)
        backgroundScope.launch { model.state.collect {} }
        advanceUntilIdle()

        account.value = BOB
        advanceUntilIdle()

        assertEquals(emptyList(), assertIs<JournalListState.Loaded>(model.state.value).entries)
    }

    @Test
    fun `delete removes the entry, and undo puts the same entry back`() = runTest(dispatcher) {
        every { userRepository.observeCurrentUserId() } returns flowOf(ALICE)
        val model = JournalListViewModel(repository, userRepository)

        model.delete(entry)
        advanceUntilIdle()
        coVerify { repository.delete("j1", ALICE) }

        model.restore(entry)
        advanceUntilIdle()
        coVerify { repository.save(entry) }
    }

    private companion object {
        const val ALICE = "alice"
        const val BOB = "bob"
    }
}
