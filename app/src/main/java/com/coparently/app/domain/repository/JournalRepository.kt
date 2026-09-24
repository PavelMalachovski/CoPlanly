package com.coparently.app.domain.repository

import com.coparently.app.domain.journal.JournalEntry
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

/**
 * A parent's private journal (MON-22): this phone's Room table and nothing else.
 *
 * Every read and delete names the author, so an entry written under one account is never shown
 * under another — belt and braces beside `AccountSwitchGuard`, which clears the table on an account
 * switch like every other table.
 */
interface JournalRepository {

    /** [authorUid]'s entries, the day they are about newest first. */
    fun observeEntries(authorUid: String): Flow<List<JournalEntry>>

    /** One of [authorUid]'s entries, or null when there is no such entry of theirs. */
    suspend fun getEntry(id: String, authorUid: String): JournalEntry?

    /** Inserts or replaces [entry] — also how Undo puts a deleted entry back, id and times intact. */
    suspend fun save(entry: JournalEntry)

    /** Deletes one of [authorUid]'s entries for good; nothing else holds a copy. */
    suspend fun delete(id: String, authorUid: String)

    /** [authorUid]'s entries about a day in [from]…[to], inclusive, oldest first — for the export. */
    suspend fun entriesBetween(authorUid: String, from: LocalDate, to: LocalDate): List<JournalEntry>
}
