package com.coparently.app.domain.repository

import com.coparently.app.domain.model.ChildInfo
import kotlinx.coroutines.flow.Flow

/**
 * Repository interface for managing child information.
 * Part of the domain layer in Clean Architecture.
 */
interface ChildInfoRepository {

    /**
     * The children of the family on screen as a Flow — with two families, the other family's
     * children are left out (`FamilyView`); a child naming no family is always included.
     *
     * @return Flow of the children this screen may show
     */
    fun getAllChildInfo(): Flow<List<ChildInfo>>

    /**
     * Every child this phone holds, in every family. Only for work that must reach all of them
     * whichever family is on screen: withdrawing health consent, and connecting a school for a
     * family the parent chose. Defaults to [getAllChildInfo] for a fake.
     */
    fun getAllChildInfoInEveryFamily(): Flow<List<ChildInfo>> = getAllChildInfo()

    /**
     * Gets child information by ID.
     *
     * @param id The child info ID
     * @return The child information or null if not found
     */
    suspend fun getChildInfoById(id: String): ChildInfo?

    /**
     * Gets child information by ID as a Flow.
     *
     * @param id The child info ID
     * @return Flow that emits the child information
     */
    fun observeChildInfoById(id: String): Flow<ChildInfo?>

    /**
     * Inserts or updates child information.
     *
     * @param childInfo The child information to insert or update
     */
    suspend fun upsertChildInfo(childInfo: ChildInfo)

    /**
     * Deletes child information.
     *
     * @param childInfo The child information to delete
     */
    suspend fun deleteChildInfo(childInfo: ChildInfo)

    /**
     * Syncs local child information with Firestore.
     */
    /**
     * Uploads what is pending and pulls the remote side once, then **returns**.
     *
     * Named for the shape rather than for the subject (CQ-10). This was `syncWithFirestore()`
     * on all seven repositories, and on three of them it meant the opposite: an endless
     * snapshot listener that never returns. `SyncService.performFullSync()` already awaits the
     * pet one, so adding an expense call beside it by analogy — which is exactly what the old
     * name invited — would have made `performFullSync()` hang, `SyncWorker` be killed at
     * WorkManager's ten-minute ceiling, and sync stop entirely, with no exception and no log.
     */
    suspend fun pullOnce()
}
