package com.coparently.app.data.remote.firebase

import com.google.firebase.firestore.FirebaseFirestore
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
            // Single-field equality only — no orderBy, so no composite index is required.
            // Consumers (Room DAO / dashboards) sort by createdAt themselves.
            collection
                .whereEqualTo(field, userId)
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
     * Whether [fromUid] has a request waiting on [myUid]'s answer — one family's part of the
     * family switcher's dot (M-8), for a family that is not on screen.
     *
     * **Shaped by the rule** (CLAUDE.md item 12). `change_requests` allows a read only to
     * `requestedBy` or `requestedTo`, and a list query is accepted only when its filters
     * guarantee that for every result: the `requestedTo == myUid` equality is what does, and the
     * other two only narrow it. The requester, not `familyId`, names the family: a request is
     * only ever addressed to a live co-parent, and a request written before the stamp existed
     * carries none.
     *
     * `limit(1)`, because the question is yes or no. Three equality filters and no `orderBy` are
     * served by merging single-field indexes, so no composite index is needed.
     *
     * Fails the flow on a listener error rather than swallowing it; the caller bounds the retry.
     *
     * @param myUid The signed-in parent, who would have to answer.
     * @param fromUid The co-parent of the family being watched.
     */
    fun observeHasPendingFrom(myUid: String, fromUid: String): Flow<Boolean> = callbackFlow {
        val registration = collection
            .whereEqualTo("requestedTo", myUid)
            .whereEqualTo("requestedBy", fromUid)
            .whereEqualTo("status", PENDING)
            .limit(1)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    close(error)
                    return@addSnapshotListener
                }
                if (snapshot != null) trySend(!snapshot.isEmpty)
            }

        awaitClose { registration.remove() }
    }

    /**
     * Adds or updates a change request document.
     */
    suspend fun setChangeRequest(requestId: String, data: Map<String, Any>) {
        collection.document(requestId).set(data).await()
    }

    private companion object {
        /** `ChangeRequestStatus.PENDING.name`, as `ChangeRequestRepositoryImpl` writes it. */
        const val PENDING = "PENDING"
    }
}
