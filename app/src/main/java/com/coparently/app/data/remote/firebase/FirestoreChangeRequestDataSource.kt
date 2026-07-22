package com.coparently.app.data.remote.firebase

import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Data source for event change requests in Firestore.
 *
 * Only documents involving the current user (as requester or addressee) are observed,
 * so a device never pulls other families' requests.
 */
@Singleton
class FirestoreChangeRequestDataSource @Inject constructor(
    private val firestore: FirebaseFirestore
) {
    private val collection = firestore.collection("change_requests")

    /**
     * Observes change requests where [userId] participates on either side.
     * Emits the merged, deduplicated document list on every snapshot of either query.
     */
    fun observeChangeRequestsForUser(userId: String): Flow<List<Map<String, Any>>> = callbackFlow {
        val latest = HashMap<String, List<Map<String, Any>>>()

        fun emitMerged() {
            val merged = latest.values.flatten()
                .distinctBy { it["id"] }
            trySend(merged)
        }

        val registrations = listOf("requestedBy", "requestedTo").map { field ->
            collection
                .whereEqualTo(field, userId)
                .orderBy("createdAt", Query.Direction.DESCENDING)
                .addSnapshotListener { snapshot, error ->
                    if (error != null) {
                        close(error)
                        return@addSnapshotListener
                    }
                    if (snapshot != null) {
                        latest[field] = snapshot.documents.mapNotNull { doc ->
                            doc.data?.plus("id" to doc.id)
                        }
                        emitMerged()
                    }
                }
        }

        awaitClose { registrations.forEach { it.remove() } }
    }

    /**
     * Adds or updates a change request document.
     */
    suspend fun setChangeRequest(requestId: String, data: Map<String, Any>) {
        collection.document(requestId).set(data).await()
    }
}
