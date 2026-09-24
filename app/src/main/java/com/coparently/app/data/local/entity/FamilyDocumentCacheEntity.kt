package com.coparently.app.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * The last server answer for one vault index document, as Room keeps it (MON-23, schema 43).
 *
 * **A cache, not a source of truth, and not an outbox.** Every row is written from a snapshot the
 * server confirmed and from nothing else: there is no `syncedToFirestore` column because nothing
 * here is ever uploaded, and a vault write still goes to Firestore first. The rows are read only
 * when the live listener cannot answer from the server, and the screen then says the list may be
 * out of date.
 *
 * The **index** only — never the file. The bytes stay in Storage and in `SharedFileCache`, which
 * checks a download against [sha256]; nothing here is a download URL.
 *
 * Columns mirror `family_documents/{id}` as `FamilyDocumentMapper` reads it; `sharedWith` is not
 * kept, because a vault document's audience is its family's two parents by rule.
 *
 * @property familyId The family the row was listed under — every read is scoped to it.
 * @property category The stored wire value (`DocumentCategory.wire`).
 * @property deletedAtMillis Set when the server's copy is a tombstone (CLAUDE.md item 14); such a
 *   row is never listed, and it leaves the cache once the sweep removes the document.
 */
@Entity(tableName = "family_documents_cache")
data class FamilyDocumentCacheEntity(
    @PrimaryKey
    val id: String,
    val familyId: String,
    val createdByFirebaseUid: String,
    val title: String,
    val category: String,
    val fileName: String,
    val storagePath: String,
    val contentType: String,
    val sizeBytes: Long,
    val sha256: String,
    val createdAtMillis: Long,
    val deletedAtMillis: Long?
)
