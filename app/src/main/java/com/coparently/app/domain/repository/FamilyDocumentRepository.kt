package com.coparently.app.domain.repository

import com.coparently.app.domain.documents.DocumentCategory
import com.coparently.app.domain.documents.FamilyDocument
import kotlinx.coroutines.flow.Flow
import java.io.File

/**
 * The family's document vault (MON-23).
 *
 * Firestore is the only store: the list is observed live, like the calendar-friend list, and
 * nothing is cached in Room — a vault table is a schema version, recorded as a follow-up in
 * `docs/ROADMAP.md` MON-23 rather than taken here.
 */
interface FamilyDocumentRepository {

    /**
     * The family's live documents, tombstones excluded, or null while the server cannot be read —
     * so the screen can say "unavailable" rather than showing an empty vault as if it were one.
     */
    fun observe(familyId: String): Flow<List<FamilyDocument>?>

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
