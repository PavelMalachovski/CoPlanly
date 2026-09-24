package com.coparently.app.data.documents

import android.util.Log
import com.coparently.app.domain.documents.FamilyDocument
import com.coparently.app.domain.documents.VaultListing
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.MetadataChanges
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The vault's index in Firestore, `family_documents/{id}` (MON-23): the live listener, the create
 * and the tombstone. Room holds a read-through copy of what the listener confirmed
 * ([FamilyDocumentIndexCache]); nothing here writes to Room and nothing in Room is written back.
 */
@Singleton
class FamilyDocumentIndex @Inject constructor(
    private val firestore: FirebaseFirestore,
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

    private fun events(uid: String, familyId: String): Flow<VaultIndexEvent> = callbackFlow {
        // Metadata changes included: without them the listener stays silent when the server
        // confirms exactly what Firestore's own cache already showed, and the screen would keep
        // saying "may be out of date" over a list that is current.
        val registration = firestore.collection(COLLECTION)
            .whereArrayContains("sharedWith", uid)
            .whereEqualTo("familyId", familyId)
            .addSnapshotListener(MetadataChanges.INCLUDE) { snapshot, error ->
                when {
                    // Not closed: a refused or dropped listener must not end the screen's flow.
                    error != null -> {
                        Log.w(TAG, "Vault listener failed", error)
                        trySend(VaultIndexEvent.Failed)
                    }
                    snapshot == null -> Unit
                    // This phone's own write, not yet confirmed: the confirmed answer follows.
                    snapshot.metadata.hasPendingWrites() -> Unit
                    snapshot.metadata.isFromCache -> trySend(VaultIndexEvent.FromLocalCache)
                    else -> trySend(
                        VaultIndexEvent.FromServer(
                            snapshot.documents.mapNotNull { FamilyDocumentMapper.indexEntry(it.id, it.data) }
                        )
                    )
                }
            }
        awaitClose { registration.remove() }
    }

    /** Writes [document]'s index entry; the listener's next server answer caches it. */
    suspend fun write(document: FamilyDocument) {
        firestore.collection(COLLECTION).document(document.id)
            .set(FamilyDocumentMapper.toFirestoreMap(document))
            .await()
    }

    /**
     * Tombstones document [docId] with `update()`, never `set()` (CLAUDE.md item 14): the
     * tombstone keeps the audience the read rule is keyed on, so the co-parent's listener sees the
     * document leave rather than lose it.
     */
    suspend fun tombstone(docId: String, deletedBy: String, atMillis: Long) {
        firestore.collection(COLLECTION).document(docId)
            .update(FamilyDocumentMapper.tombstone(deletedBy, atMillis))
            .await()
    }

    private companion object {
        const val TAG = "FamilyDocuments"
        const val COLLECTION = "family_documents"
    }
}
