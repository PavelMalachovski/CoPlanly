package com.coparently.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.coparently.app.data.local.entity.JournalEntryEntity
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

/**
 * The private journal (MON-22). Every query is scoped to its author.
 *
 * `entryDate` is ISO text, so comparing it as a string orders it as a date.
 */
@Dao
interface JournalDao {

    /** [authorUid]'s entries, the day they are about newest first. */
    @Query(
        "SELECT * FROM journal_entries WHERE createdByFirebaseUid = :authorUid " +
            "ORDER BY entryDate DESC, createdAtMillis DESC"
    )
    fun observeEntries(authorUid: String): Flow<List<JournalEntryEntity>>

    /** One entry, only if [authorUid] wrote it. */
    @Query("SELECT * FROM journal_entries WHERE id = :id AND createdByFirebaseUid = :authorUid")
    suspend fun getEntry(id: String, authorUid: String): JournalEntryEntity?

    /** Inserts or replaces an entry. */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entry: JournalEntryEntity)

    /** Deletes one entry, only if [authorUid] wrote it. */
    @Query("DELETE FROM journal_entries WHERE id = :id AND createdByFirebaseUid = :authorUid")
    suspend fun delete(id: String, authorUid: String)

    /** [authorUid]'s entries about a day in [from]…[to], inclusive, oldest first. */
    @Query(
        "SELECT * FROM journal_entries WHERE createdByFirebaseUid = :authorUid " +
            "AND entryDate >= :from AND entryDate <= :to ORDER BY entryDate ASC, createdAtMillis ASC"
    )
    suspend fun entriesBetween(authorUid: String, from: LocalDate, to: LocalDate): List<JournalEntryEntity>
}
