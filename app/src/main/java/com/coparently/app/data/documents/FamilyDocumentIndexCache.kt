package com.coparently.app.data.documents

import android.util.Log
import com.coparently.app.data.local.dao.FamilyDocumentCacheDao
import com.coparently.app.data.local.entity.FamilyDocumentCacheEntity
import com.coparently.app.domain.documents.DocumentCategory
import com.coparently.app.domain.documents.FamilyDocument
import com.coparently.app.domain.documents.VaultListing
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import javax.inject.Inject
import javax.inject.Singleton

/** What the vault listener reported, before the cache decides what the screen sees. */
sealed interface VaultIndexEvent {

    /** A snapshot the server confirmed: the complete answer to the vault query, tombstones included. */
    data class FromServer(val entries: List<FamilyDocumentMapper.IndexEntry>) : VaultIndexEvent

    /** Firestore answered from its own offline cache — nothing the server has just confirmed. */
    data object FromLocalCache : VaultIndexEvent

    /** The listener failed (refused, or dropped). */
    data object Failed : VaultIndexEvent
}

/**
 * The read-through Room cache of the vault **index** (MON-23, schema 43).
 *
 * Every server answer replaces the family's rows; while the listener cannot answer from the
 * server, the screen gets the rows last stored, marked [VaultListing.possiblyOutdated]. It never
 * uploads anything and has no outbox — every vault write still goes to Firestore first and reaches
 * this cache only through the next server answer — and it never holds a file's bytes, which stay
 * `SharedFileCache`'s job behind their SHA-256.
 *
 * Every read and write is scoped to one `familyId`, so switching family never lists another
 * family's documents; an account switch wipes the table with the rest of Room
 * (`AccountSwitchGuard` → `clearAllTables`).
 */
@Singleton
class FamilyDocumentIndexCache @Inject constructor(
    private val dao: FamilyDocumentCacheDao
) {

    /** The listing the vault screen shows for [familyId], given what its listener reports. */
    fun listing(familyId: String, events: Flow<VaultIndexEvent>): Flow<VaultListing?> = flow {
        events.collect { event ->
            when (event) {
                is VaultIndexEvent.FromServer -> emit(fromServer(familyId, event.entries))
                // Firestore's own cache also answers the moment a listener attaches, a beat before
                // the server does: with nothing stored, say nothing rather than flash "unavailable"
                // over what is usually an empty vault about to be confirmed.
                VaultIndexEvent.FromLocalCache -> lastKnown(familyId)?.let { emit(it) }
                VaultIndexEvent.Failed -> emit(lastKnown(familyId))
            }
        }
    }

    private suspend fun fromServer(
        familyId: String,
        entries: List<FamilyDocumentMapper.IndexEntry>
    ): VaultListing {
        // The query already filters on `familyId`; this keeps a row that names another family
        // from ever being cached, or listed, under this one.
        val inFamily = entries.filter { it.document.familyId == familyId }
        quietly("write", Unit) { dao.replaceFamily(familyId, inFamily.map { it.toCacheRow() }) }
        val live = inFamily.filter { it.deletedAtMillis == null }.map { it.document }
        return VaultListing(live.sortedWith(FamilyDocumentMapper.listOrder))
    }

    /** The family's last server-confirmed live documents, or null when none are stored. */
    private suspend fun lastKnown(familyId: String): VaultListing? {
        val documents = quietly("read", emptyList()) { dao.liveIn(familyId) }
            .filter { it.familyId == familyId }
            .map { it.toDocument() }
            .sortedWith(FamilyDocumentMapper.listOrder)
        return documents.takeIf { it.isNotEmpty() }?.let { VaultListing(it, possiblyOutdated = true) }
    }

    /** A cache that cannot be read or written must never take the live list down with it. */
    private suspend fun <T> quietly(operation: String, fallback: T, block: suspend () -> T): T = try {
        block()
    } catch (e: CancellationException) {
        throw e
    } catch (
        @Suppress("TooGenericExceptionCaught") e: Exception
    ) {
        Log.w(TAG, "Vault cache $operation failed", e)
        fallback
    }

    private companion object {
        const val TAG = "FamilyDocumentCache"
    }
}

/** The Room row for one index entry. */
internal fun FamilyDocumentMapper.IndexEntry.toCacheRow(): FamilyDocumentCacheEntity =
    FamilyDocumentCacheEntity(
        id = document.id,
        familyId = document.familyId,
        createdByFirebaseUid = document.createdByFirebaseUid,
        title = document.title,
        category = document.category.wire,
        fileName = document.fileName,
        storagePath = document.storagePath,
        contentType = document.contentType,
        sizeBytes = document.sizeBytes,
        sha256 = document.sha256,
        createdAtMillis = document.createdAtMillis,
        deletedAtMillis = deletedAtMillis
    )

/** The document a cached row describes. */
internal fun FamilyDocumentCacheEntity.toDocument(): FamilyDocument = FamilyDocument(
    id = id,
    familyId = familyId,
    createdByFirebaseUid = createdByFirebaseUid,
    title = title,
    category = DocumentCategory.fromWire(category),
    fileName = fileName,
    storagePath = storagePath,
    contentType = contentType,
    sizeBytes = sizeBytes,
    sha256 = sha256,
    createdAtMillis = createdAtMillis
)
