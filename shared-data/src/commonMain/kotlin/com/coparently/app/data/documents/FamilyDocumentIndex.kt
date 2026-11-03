package com.coparently.app.data.documents

import com.coparently.app.data.platform.logger
import com.coparently.app.data.remote.firebase.DocumentPath
import com.coparently.app.data.remote.firebase.DocumentQuery
import com.coparently.app.data.remote.firebase.FirestoreClient
import com.coparently.app.domain.documents.FamilyDocument
import com.coparently.app.domain.documents.VaultListing
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.transform

/**
 * The vault's index in Firestore, `family_documents/{id}` (MON-23): the live listener, the create
 * and the tombstone. Room holds a read-through copy of what the listener confirmed
 * ([FamilyDocumentIndexCache]); nothing here writes to Room and nothing in Room is written back.
 *
 * On [FirestoreClient] since MON-24 phase 2 (Task 8d): the same query, listener and writes as the
 * Android SDK calls it made.
 */
class FamilyDocumentIndex(
    private val firestore: FirestoreClient,
    private val cache: FamilyDocumentIndexCache
) {

    /**
     * [familyId]'s documents as [uid] may read them: the server's answer, or the cached copy of
     * the last one while the server cannot be reached.
     *
     * The query filters on `sharedWith` *and* `familyId`, the two fields the read rule and the
     * create rule key on (CLAUDE.md item 12), so a parent with two families sees the selected
     * one's documents only.
     */
    fun observe(uid: String, familyId: String): Flow<VaultListing?> =
        cache.listing(familyId, events(uid, familyId))

    private fun events(uid: String, familyId: String): Flow<VaultIndexEvent> {
        // Metadata changes included: without them the listener stays silent when the server
        // confirms exactly what Firestore's own cache already showed, and the screen would keep
        // saying "may be out of date" over a list that is current.
        val query = DocumentQuery(COLLECTION)
            .whereArrayContains("sharedWith", uid)
            .whereEqualTo("familyId", familyId)
        return firestore.observeWithMetadata(query)
            .transform { snapshot ->
                when {
                    // This phone's own write, not yet confirmed: the confirmed answer follows.
                    snapshot.hasPendingWrites -> Unit
                    snapshot.isFromCache -> emit(VaultIndexEvent.FromLocalCache)
                    else -> emit(
                        VaultIndexEvent.FromServer(
                            snapshot.documents.mapNotNull { FamilyDocumentMapper.indexEntry(it.id, it.data) }
                        )
                    )
                }
            }
            .catch { error ->
                logger.w(TAG, "Vault listener failed", error)
                emit(VaultIndexEvent.Failed)
                // Not closed: a refused or dropped listener must not end the screen's flow.
                awaitCancellation()
            }
    }

    /** Writes [document]'s index entry; the listener's next server answer caches it. */
    suspend fun write(document: FamilyDocument) {
        firestore.set(DocumentPath(COLLECTION, document.id), FamilyDocumentMapper.toFirestoreMap(document))
    }

    /**
     * Tombstones document [docId] with `update()`, never `set()` (CLAUDE.md item 14): the
     * tombstone keeps the audience the read rule is keyed on, so the co-parent's listener sees the
     * document leave rather than lose it.
     */
    suspend fun tombstone(docId: String, deletedBy: String, atMillis: Long) {
        firestore.update(DocumentPath(COLLECTION, docId), FamilyDocumentMapper.tombstone(deletedBy, atMillis))
    }

    private companion object {
        const val TAG = "FamilyDocuments"
        const val COLLECTION = "family_documents"
    }
}
