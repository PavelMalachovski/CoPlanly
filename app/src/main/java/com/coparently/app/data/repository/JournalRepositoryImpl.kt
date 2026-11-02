package com.coparently.app.data.repository

import com.coparently.app.data.local.dao.JournalDao
import com.coparently.app.data.local.entity.JournalEntryEntity
import com.coparently.app.domain.journal.JournalEntry
import com.coparently.app.domain.repository.JournalRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.datetime.toJavaLocalDate
import kotlinx.datetime.toKotlinLocalDate
import java.time.LocalDate

/**
 * [JournalRepository] over Room alone (MON-22).
 *
 * Deliberately has no Firestore data source, no sync hook and no push: the only dependency is the
 * DAO, so there is nothing here a later change could route an entry off the phone through.
 */
class JournalRepositoryImpl(
    private val journalDao: JournalDao
) : JournalRepository {

    override fun observeEntries(authorUid: String): Flow<List<JournalEntry>> =
        journalDao.observeEntries(authorUid).map { rows -> rows.map { it.toJournalEntry() } }

    override suspend fun getEntry(id: String, authorUid: String): JournalEntry? =
        journalDao.getEntry(id, authorUid)?.toJournalEntry()

    override suspend fun save(entry: JournalEntry) {
        journalDao.upsert(entry.toJournalEntity())
    }

    override suspend fun delete(id: String, authorUid: String) {
        journalDao.delete(id, authorUid)
    }

    override suspend fun entriesBetween(authorUid: String, from: LocalDate, to: LocalDate): List<JournalEntry> =
        journalDao.entriesBetween(authorUid, from.toKotlinLocalDate(), to.toKotlinLocalDate())
            .map { it.toJournalEntry() }
}

/**
 * Room row to domain entry. The row's date is `kotlinx.datetime`'s since the entities moved to
 * common code (MON-24 phase 2); the domain entry stays on `java.time` until it moves too.
 */
internal fun JournalEntryEntity.toJournalEntry() = JournalEntry(
    id = id,
    entryDate = entryDate.toJavaLocalDate(),
    text = text,
    createdAtMillis = createdAtMillis,
    updatedAtMillis = updatedAtMillis,
    familyId = familyId,
    createdByFirebaseUid = createdByFirebaseUid
)

/** Domain entry to Room row. */
internal fun JournalEntry.toJournalEntity() = JournalEntryEntity(
    id = id,
    createdByFirebaseUid = createdByFirebaseUid,
    familyId = familyId,
    entryDate = entryDate.toKotlinLocalDate(),
    text = text,
    createdAtMillis = createdAtMillis,
    updatedAtMillis = updatedAtMillis
)
