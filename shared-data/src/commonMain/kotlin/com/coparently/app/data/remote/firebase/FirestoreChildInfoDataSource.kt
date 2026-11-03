package com.coparently.app.data.remote.firebase

import com.coparently.app.data.sync.Tombstone
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/**
 * Remote data source for child information using Firestore.
 * Handles all Firestore operations for child info shared between parents.
 */
class FirestoreChildInfoDataSource(
    private val firestore: FirestoreClient
) {
    private val childInfoCollection = "child_info"

    // `getAllChildInfo` was removed by the August 2026 audit, for the reason given in
    // `FirestoreEventDataSource`: it queried the whole `child_info` collection with no
    // `sharedWith` filter — every family's children, medical notes included — and had no
    // caller. `getChildInfoForParent` below is the filtered read the app actually uses.

    /**
     * Gets child information for a specific parent pair.
     * Parents can only see child info they're associated with.
     *
     * @param parentId Firebase UID of one of the parents
     */
    fun getChildInfoForParent(parentId: String): Flow<List<Map<String, Any?>>> = flow {
        val documents = firestore.get(
            DocumentQuery(childInfoCollection)
                .whereArrayContains("sharedWith", parentId)
                .orderBy("childName")
        )
        emit(documents.mapNotNull { it.data })
    }

    /**
     * Gets child information by ID.
     *
     * @param id The child info ID
     * @return The child information data or null if not found
     */
    suspend fun getChildInfoById(id: String): Map<String, Any?>? {
        return firestore.get(DocumentPath(childInfoCollection, id))
            .data
    }

    /**
     * Inserts or updates child information.
     *
     * @param id The child info ID
     * @param childInfoData The child information data
     */
    suspend fun upsertChildInfo(id: String, childInfoData: Map<String, Any?>): Result<Unit> {
        return try {
            firestore.set(DocumentPath(childInfoCollection, id), childInfoData)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Updates specific fields of child information.
     *
     * @param id The child info ID
     * @param updates Map of field updates
     */
    suspend fun updateChildInfo(id: String, updates: Map<String, Any?>): Result<Unit> {
        return try {
            firestore.update(DocumentPath(childInfoCollection, id), updates)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Marks a child record deleted, leaving the document in place so the co-parent can be told (CQ-19).
     *
     * `update` rather than `set`: the `child_info` read rule is keyed on `sharedWith`, which
     * lives on the existing document, so a tombstone that replaced it would be one the co-parent
     * is not allowed to read — a deletion delivered to nobody, which is the defect this exists
     * to fix.
     *
     * A missing document is [Result.success], not a failure. `update` raises `NOT_FOUND` when
     * the document never landed, and in that case the deletion has nothing to reach: there is no
     * remote copy for a co-parent to be holding. Reporting failure would keep the local
     * tombstone queued forever, retrying a write that cannot succeed.
     *
     * @param id The record's id.
     * @param deletedAtMillis When it was deleted, epoch millis.
     * @param deletedBy Firebase UID of whoever deleted it.
     */
    suspend fun tombstoneChildInfo(id: String, deletedAtMillis: Long, deletedBy: String): Result<Unit> {
        return try {
            firestore.update(DocumentPath(childInfoCollection, id), Tombstone.fields(deletedAtMillis, deletedBy))
            Result.success(Unit)
        } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
            if (firestore.errorCodeOf(e) == FirestoreErrorCode.NOT_FOUND) {
                Result.success(Unit)
            } else {
                Result.failure(e)
            }
        }
    }

    /**
     * Observes real-time changes to child information for specific parents.
     *
     * @param parentIds List of parent Firebase UIDs
     */
    fun observeChildInfoForParents(parentIds: List<String>): Flow<List<Map<String, Any?>>> = flow {
        // In Firestore, we need to query for documents where sharedWith array contains any of the parentIds
        // For simplicity, we'll query for the first parent and rely on sharedWith containing both
        if (parentIds.isNotEmpty()) {
            val documents = firestore.get(
                DocumentQuery(childInfoCollection)
                    .whereArrayContains("sharedWith", parentIds.first())
                    .orderBy("childName")
            )
            emit(documents.mapNotNull { it.data })
        } else {
            emit(emptyList())
        }
    }
}
