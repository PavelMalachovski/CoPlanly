package com.coparently.app.data.repository

import com.coparently.app.data.local.dao.JournalDao
import com.coparently.app.data.local.entity.JournalEntryEntity
import com.coparently.app.domain.journal.JournalEntry
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Test
import java.time.LocalDate
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * [JournalRepositoryImpl] (MON-22): Room and nothing else, every call scoped to the author, and a
 * round trip that loses no field — Undo relies on putting back exactly what was deleted.
 */
class JournalRepositoryImplTest {

    private val dao = mockk<JournalDao>(relaxed = true)
    private val repository = JournalRepositoryImpl(dao)
    private val entry = JournalEntry(
        id = "j1",
        entryDate = LocalDate.of(2026, 9, 1),
        text = "Late pickup",
        createdAtMillis = 1_000L,
        updatedAtMillis = 2_000L,
        familyId = "alice__bob",
        createdByFirebaseUid = "alice"
    )

    @Test
    fun `an entry survives the trip to Room and back with every field`() = runTest {
        val stored = slot<JournalEntryEntity>()
        coEvery { dao.upsert(capture(stored)) } returns Unit

        repository.save(entry)
        coEvery { dao.getEntry("j1", "alice") } returns stored.captured

        assertEquals(entry, repository.getEntry("j1", "alice"))
    }

    @Test
    fun `reads and deletes name the author`() = runTest {
        every { dao.observeEntries("alice") } returns flowOf(emptyList())
        coEvery { dao.getEntry("j1", "bob") } returns null

        repository.observeEntries("alice").first()
        assertNull(repository.getEntry("j1", "bob"))
        repository.delete("j1", "alice")

        coVerify { dao.delete("j1", "alice") }
    }

    @Test
    fun `the export's read is the author's entries for the period`() = runTest {
        val from = LocalDate.of(2026, 9, 1)
        val to = LocalDate.of(2026, 9, 30)
        coEvery { dao.entriesBetween("alice", from, to) } returns listOf(entry.toJournalEntity())

        assertEquals(listOf(entry), repository.entriesBetween("alice", from, to))
    }
}
