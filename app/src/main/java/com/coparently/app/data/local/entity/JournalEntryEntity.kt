package com.coparently.app.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.time.LocalDate

/**
 * A private journal entry as Room stores it (MON-22, schema 41).
 *
 * **There is no `syncedToFirestore` column, and none may be added.** Every shared table has one
 * because it is an outbox; this table is not, because nothing leaves it. The absence is the
 * guarantee that no sync pass can pick an entry up by accident.
 *
 * @property entryDate The day the entry is about, ISO text through `Converters`.
 * @property createdAtMillis First saved, epoch millis.
 * @property updatedAtMillis Last saved, epoch millis.
 * @property familyId See [com.coparently.app.domain.journal.JournalEntry.familyId].
 * @property createdByFirebaseUid The author; every query is scoped to it.
 */
@Entity(tableName = "journal_entries")
data class JournalEntryEntity(
    @PrimaryKey
    val id: String,
    val createdByFirebaseUid: String,
    val familyId: String?,
    val entryDate: LocalDate,
    val text: String,
    val createdAtMillis: Long,
    val updatedAtMillis: Long
)
