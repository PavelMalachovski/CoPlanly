package com.coparently.app.data.remote.firebase

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge

/**
 * Data source for event change requests in Firestore.
 *
 * Only documents involving the current user (as requester or addressee) are observed,
 * so a device never pulls other families' requests.
 */
class FirestoreChangeRequestDataSource(
    private val firestore: FirestoreClient
) {

    /**
     * Observes change requests where [userId] participates on either side.
     * Emits the merged, deduplicated document list on every snapshot of either query.
     *
     * Each document carries its id under `"id"` and, under [HAS_PENDING_WRITES], whether it holds
     * a write of this phone's that the server has not confirmed. Firestore echoes a write to the
     * listener before the server has it, so without the mark an answer given offline would look
     * exactly like one that left.
     */
    fun observeChangeRequestsForUser(userId: String): Flow<List<Map<String, Any>>> = flow {
        val latest = HashMap<String, List<Map<String, Any>>>()

        val snapshots = listOf("requestedBy", "requestedTo").map { field ->
            // Single-field equality only — no orderBy, so no composite index is required.
            // Consumers (Room DAO / dashboards) sort by createdAt themselves.
            firestore.observe(DocumentQuery(COLLECTION).whereEqualTo(field, userId)).map { documents ->
                field to documents.mapNotNull { doc ->
                    doc.data?.plus(listOf("id" to doc.id, HAS_PENDING_WRITES to doc.hasPendingWrites))?.asFieldMap()
                }
            }
        }

        // Both listeners feed this one collector, so `latest` is only ever touched here; either
        // listener's failure ends the flow, as `close(error)` did.
        merge(*snapshots.toTypedArray()).collect { (field, documents) ->
            latest[field] = documents
            emit(latest.values.flatten().distinctBy { it["id"] })
        }
    }

    /**
     * The ids of the requests [fromUid] has made to [myUid] that are still pending — one family's
     * part of the family switcher's dot (M-8), for a family that is not on screen. The caller
     * decides which of them still wait on an answer, by their clocks (phase 3a).
     *
     * **Shaped by the rule** (CLAUDE.md item 12). `change_requests` allows a read only to
     * `requestedBy` or `requestedTo`, and a list query is accepted only when its filters
     * guarantee that for every result: the `requestedTo == myUid` equality is what does, and the
     * other two only narrow it. The requester, not `familyId`, names the family: a request is
     * only ever addressed to a live co-parent, and a request written before the stamp existed
     * carries none.
     *
     * No `limit`: the dot needs the ids to look their clocks up, and a family has a handful of
     * pending requests at most. Three equality filters and no `orderBy` are served by merging
     * single-field indexes, so no composite index is needed.
     *
     * Fails the flow on a listener error rather than swallowing it; the caller bounds the retry.
     *
     * @param myUid The signed-in parent, who would have to answer.
     * @param fromUid The co-parent of the family being watched.
     */
    fun observePendingIdsFrom(myUid: String, fromUid: String): Flow<Set<String>> =
        firestore.observe(
            DocumentQuery(COLLECTION)
                .whereEqualTo("requestedTo", myUid)
                .whereEqualTo("requestedBy", fromUid)
                .whereEqualTo("status", PENDING)
        ).map { documents -> documents.map { it.id }.toSet() }

    /**
     * The change request [requestId] as the server holds it, never from Firestore's cache; null when
     * there is no such document. Throws when the server cannot be reached.
     */
    suspend fun getFromServer(requestId: String): Map<String, Any>? =
        firestore.get(DocumentPath(COLLECTION, requestId), ReadSource.SERVER).data?.asFieldMap()

    /**
     * Adds or updates a change request document.
     */
    suspend fun setChangeRequest(requestId: String, data: Map<String, Any>) {
        firestore.set(DocumentPath(COLLECTION, requestId), data)
    }

    companion object {
        /** Whether this phone wrote the document and the server has not confirmed it yet. */
        const val HAS_PENDING_WRITES = "__hasPendingWrites__"

        private const val COLLECTION = "change_requests"

        /** `ChangeRequestStatus.PENDING.name`, as `ChangeRequestRepositoryImpl` writes it. */
        private const val PENDING = "PENDING"
    }
}
