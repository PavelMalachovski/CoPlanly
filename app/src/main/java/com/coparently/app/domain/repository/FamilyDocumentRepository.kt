package com.coparently.app.domain.repository

import com.coparently.app.domain.documents.DocumentCategory
import com.coparently.app.domain.documents.FamilyDocument
import com.coparently.app.domain.documents.VaultListing
import kotlinx.coroutines.flow.Flow
import java.io.File

/**
 * The family's document vault (MON-23).
 *
 * Firestore is the store every write goes to, and the list is observed live. Room keeps a
 * read-through cache of the **index** only (schema 43): the last list the server confirmed, shown
 * when the listener cannot answer from the server and labelled as possibly out of date. It never
 * uploads anything, has no outbox, and never holds a file's bytes.
 */
interface FamilyDocumentRepository {

    /**
     * The family's live documents, tombstones excluded: the server's answer, or — while the server
     * cannot be read — this phone's last copy of it, marked [VaultListing.possiblyOutdated]. Null
     * when neither exists, so the screen can say "unavailable" rather than showing an empty vault
     * as if it were one.
     */
    fun observe(familyId: String): Flow<VaultListing?>

    /**
     * Uploads the file at [contentUri] and files it in [familyId]'s vault.
     *
     * The file is uploaded first and the document written second; a failed document write
     * removes the file again, so the vault never lists a file that is not there and the bucket
     * keeps no orphan it can avoid.
     */
    suspend fun add(
        familyId: String,
        title: String,
        category: DocumentCategory,
        contentUri: String
    ): Result<FamilyDocument>

    /** Tombstones [document] (CLAUDE.md item 14). Only its uploader may; the rule refuses others. */
    suspend fun delete(document: FamilyDocument): Result<Unit>

    /** A local copy of [document]'s file, downloaded and checked against its digest. */
    suspend fun localCopy(document: FamilyDocument): Result<File>
}
