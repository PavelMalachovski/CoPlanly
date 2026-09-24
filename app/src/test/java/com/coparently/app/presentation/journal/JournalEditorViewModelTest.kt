package com.coparently.app.presentation.journal

import androidx.lifecycle.SavedStateHandle
import com.coparently.app.R
import com.coparently.app.domain.journal.JournalEntry
import com.coparently.app.domain.repository.JournalRepository
import com.coparently.app.domain.repository.UserRepository
import com.coparently.app.presentation.common.ParentsSource
import com.coparently.app.presentation.common.UiText
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test
import java.io.IOException
import java.time.LocalDate
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/**
 * Writing and editing a private journal entry (MON-22).
 *
 * Pinned: a new entry is stamped with its author and the family at create (item 18), read fresh
 * rather than from a shared flow (item 17); an edit is a copy that keeps the id, the first-written
 * time and the family; and a failed save is a sentence, not a silent return to the list.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class JournalEditorViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private val repository = mockk<JournalRepository>(relaxed = true)
    private val userRepository = mockk<UserRepository>()
    private val parentsSource = mockk<ParentsSource>()
    private val existing = JournalEntry(
        id = "j1",
        entryDate = LocalDate.of(2026, 9, 1),
        text = "Late pickup",
        createdAtMillis = 1_000L,
        updatedAtMillis = 1_000L,
        familyId = "alice__carol",
        createdByFirebaseUid = ALICE
    )

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        coEvery { userRepository.getCurrentUserId() } returns ALICE
        coEvery { parentsSource.coParentUid() } returns BOB
        coEvery { repository.getEntry("j1", ALICE) } returns existing
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun editor(entryId: String) = JournalEditorViewModel(
        SavedStateHandle(mapOf(JournalEditorViewModel.ARG_ENTRY_ID to entryId)),
        repository,
        userRepository,
        parentsSource
    )

    @Test
    fun `a new entry opens empty on today and cannot be saved blank`() = runTest(dispatcher) {
        val model = editor(JournalEditorViewModel.NEW_ENTRY)
        advanceUntilIdle()

        assertTrue(model.state.value.isNew)
        assertEquals(LocalDate.now(), model.state.value.entryDate)
        model.setText("   ")
        assertFalse(model.state.value.canSave)
        model.save()
        advanceUntilIdle()
        coVerify(exactly = 0) { repository.save(any()) }
    }

    @Test
    fun `a new entry is stamped with its author and the family at create`() = runTest(dispatcher) {
        val saved = slot<JournalEntry>()
        coEvery { repository.save(capture(saved)) } returns Unit
        val model = editor(JournalEditorViewModel.NEW_ENTRY)
        advanceUntilIdle()

        model.setDate(LocalDate.of(2026, 8, 30))
        model.setText("  Missed the call again  ")
        model.save()
        advanceUntilIdle()

        assertEquals(ALICE, saved.captured.createdByFirebaseUid)
        assertEquals("alice__bob", saved.captured.familyId)
        assertEquals(LocalDate.of(2026, 8, 30), saved.captured.entryDate)
        assertEquals("Missed the call again", saved.captured.text)
        assertEquals(saved.captured.createdAtMillis, saved.captured.updatedAtMillis)
        assertTrue(model.state.value.saved)
    }

    @Test
    fun `an unpaired parent's entry belongs to no family`() = runTest(dispatcher) {
        coEvery { parentsSource.coParentUid() } returns null
        val saved = slot<JournalEntry>()
        coEvery { repository.save(capture(saved)) } returns Unit
        val model = editor(JournalEditorViewModel.NEW_ENTRY)
        advanceUntilIdle()

        model.setText("First entry")
        model.save()
        advanceUntilIdle()

        assertEquals(null, saved.captured.familyId)
    }

    @Test
    fun `an edit keeps the id, the first-written time and the family, and moves the rest`() = runTest(dispatcher) {
        val saved = slot<JournalEntry>()
        coEvery { repository.save(capture(saved)) } returns Unit
        val model = editor("j1")
        advanceUntilIdle()
        assertFalse(model.state.value.isNew)
        assertEquals("Late pickup", model.state.value.text)

        model.setText("Late pickup, 40 minutes")
        model.save()
        advanceUntilIdle()

        assertEquals("j1", saved.captured.id)
        assertEquals(1_000L, saved.captured.createdAtMillis)
        assertNotEquals(1_000L, saved.captured.updatedAtMillis)
        // Never re-derived: the entry stays in the family it was written in (item 18).
        assertEquals("alice__carol", saved.captured.familyId)
        assertEquals("Late pickup, 40 minutes", saved.captured.text)
    }

    @Test
    fun `an entry that is gone opens as a new one rather than resurrecting it`() = runTest(dispatcher) {
        coEvery { repository.getEntry("gone", ALICE) } returns null
        val saved = slot<JournalEntry>()
        coEvery { repository.save(capture(saved)) } returns Unit
        val model = editor("gone")
        advanceUntilIdle()

        assertTrue(model.state.value.isNew)
        model.setText("Rewritten")
        model.save()
        advanceUntilIdle()
        assertNotEquals("gone", saved.captured.id)
    }

    @Test
    fun `a failed save says so and stays on the form`() = runTest(dispatcher) {
        coEvery { repository.save(any()) } throws IOException("disk full")
        val model = editor(JournalEditorViewModel.NEW_ENTRY)
        advanceUntilIdle()

        model.setText("Something")
        model.save()
        advanceUntilIdle()

        assertEquals(UiText.Res(R.string.journal_error_save), model.state.value.error)
        assertFalse(model.state.value.saved)
        assertFalse(model.state.value.saving)
    }

    @Test
    fun `signed out, nothing is saved and the form says why`() = runTest(dispatcher) {
        coEvery { userRepository.getCurrentUserId() } returns null
        val model = editor(JournalEditorViewModel.NEW_ENTRY)
        advanceUntilIdle()

        model.setText("Something")
        model.save()
        advanceUntilIdle()

        coVerify(exactly = 0) { repository.save(any()) }
        assertEquals(UiText.Res(R.string.journal_error_signed_out), model.state.value.error)
    }

    private companion object {
        const val ALICE = "alice"
        const val BOB = "bob"
    }
}
