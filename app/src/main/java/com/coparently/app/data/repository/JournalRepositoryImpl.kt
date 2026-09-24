package com.coparently.app.data.repository

import com.coparently.app.data.local.dao.JournalDao
import com.coparently.app.data.local.entity.JournalEntryEntity
import com.coparently.app.domain.journal.JournalEntry
import com.coparently.app.domain.repository.JournalRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

/**
 * [JournalRepository] over Room alone (MON-22).
 *
 * Deliberately has no Firestore data source, no sync hook and no push: the only dependency is the
 * DAO, so there is nothing here a later change could route an entry off the phone through.
 */
@Singleton
class JournalRepositoryImpl @Inject constructor(
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
        journalDao.entriesBetween(authorUid, from, to).map { it.toJournalEntry() }
}

/** Room row to domain entry. */
internal fun JournalEntryEntity.toJournalEntry() = JournalEntry(
    id = id,
    entryDate = entryDate,
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
    entryDate = entryDate,
    text = text,
    createdAtMillis = createdAtMillis,
    updatedAtMillis = updatedAtMillis
)
