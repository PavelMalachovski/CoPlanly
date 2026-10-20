package com.coparently.app.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.coparently.app.data.local.entity.ChildInfoEntity
import kotlinx.coroutines.flow.Flow

/**
 * DAO for accessing child information data in the local Room database.
 * Provides CRUD operations for [ChildInfoEntity].
 */
@Dao
interface ChildInfoDao {

    /**
     * Gets all child information as a Flow.
     * The Flow will emit new values whenever the data changes.
     *
     * @return Flow of list of all child information
     */
    @Query("SELECT * FROM child_info WHERE deletedAtMillis IS NULL ORDER BY childName ASC")
    fun getAllChildInfo(): Flow<List<ChildInfoEntity>>

    /**
     * Gets child information by ID, **including a pending tombstone**.
     *
     * The one read deliberately not filtered on `deletedAtMillis`, mirroring
     * [EventDao.getEventById]: its callers are the sync and delete paths, and for them "there is
     * no such row" and "there is a row this device has deleted" are opposite answers. The
     * downstream half must recognise a local tombstone rather than treat the id as unknown and
     * insert the remote document over it, which would resurrect the record the parent just
     * deleted. A caller answering a *user's* question filters at the repository boundary —
     * see `ChildInfoRepositoryImpl.getChildInfoById`.
     *
     * @param id The child info ID
     * @return The child information or null if not found
     */
    @Query("SELECT * FROM child_info WHERE id = :id")
    suspend fun getChildInfoById(id: String): ChildInfoEntity?

    /**
     * Gets child information by ID as a Flow.
     *
     * @param id The child info ID
     * @return Flow that emits the child information
     */
    @Query("SELECT * FROM child_info WHERE id = :id AND deletedAtMillis IS NULL")
    fun observeChildInfoById(id: String): Flow<ChildInfoEntity?>

    /**
     * Inserts a child information.
     * If a child info with the same ID already exists, it will be replaced.
     *
     * @param childInfo The child information to insert
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertChildInfo(childInfo: ChildInfoEntity)

    /**
     * Inserts multiple child information entries.
     *
     * @param childInfoList The list of child information to insert
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAllChildInfo(childInfoList: List<ChildInfoEntity>)

    /**
     * Updates a child information.
     *
     * @param childInfo The child information to update
     */
    @Update
    suspend fun updateChildInfo(childInfo: ChildInfoEntity)

    /**
     * Deletes a child information.
     *
     * @param childInfo The child information to delete
     */
    @Delete
    suspend fun deleteChildInfo(childInfo: ChildInfoEntity)

    /**
     * Deletes a child information by ID.
     *
     * @param id The ID of the child information to delete
     */
    @Query("DELETE FROM child_info WHERE id = :id")
    suspend fun deleteChildInfoById(id: String)

    /**
     * Deletes all child information.
     */
    @Query("DELETE FROM child_info")
    suspend fun deleteAllChildInfo()

    /**
     * Gets all child information that needs to be synced to Firestore.
     *
     * **Deliberately not filtered on `deletedAtMillis`.** This is the outbox, and a pending
     * tombstone is the half of it that used to have no path at all (CQ-19): the caller splits
     * the two apart and writes each deletion as a tombstone before uploading the live rows.
     * Excluding them here is the one change that would silently restore the original defect.
     *
     * @return List of child information that has not been synced, deletions included
     */
    @Query("SELECT * FROM child_info WHERE syncedToFirestore = 0")
    suspend fun getUnsyncedChildInfo(): List<ChildInfoEntity>

    /**
     * Marks a child record deleted, leaving the row in place as an outbox entry.
     *
     * `deletedAtMillis IS NULL` in the WHERE clause makes a second delete a no-op rather than
     * re-dating the first one, which would push the sweep's ninety-day deadline out every time
     * a retry ran.
     *
     * @param id The record's id.
     * @param deletedAtMillis When it was deleted, epoch millis.
     * @return How many rows were marked — zero if it was already a tombstone.
     */
    @Query(
        "UPDATE child_info SET deletedAtMillis = :deletedAtMillis, syncedToFirestore = 0 " +
            "WHERE id = :id AND deletedAtMillis IS NULL"
    )
    suspend fun markDeleted(id: String, deletedAtMillis: Long): Int

    /**
     * Marks child information as synced to Firestore.
     *
     * @param id The ID of the child information to mark as synced
     */
    @Query("UPDATE child_info SET syncedToFirestore = 1 WHERE id = :id")
    suspend fun markAsSynced(id: String)

    /**
     * Re-queues this user's own child-info rows for upload, so their audience is recomputed.
     *
     * Mirrors `EventDao.markOwnEventsUnsynced`. Rows whose `createdByFirebaseUid` is null are
     * deliberately not matched: nothing distinguishes this user's un-stamped row from anybody
     * else's, and re-publishing a stranger's row under this user's audience would be worse than
     * leaving it alone.
     *
     * @param myUid Firebase UID of the signed-in user.
     * @return How many rows were re-queued.
     */
    @Query(
        "UPDATE child_info SET syncedToFirestore = 0 " +
            "WHERE createdByFirebaseUid = :myUid AND deletedAtMillis IS NULL"
    )
    suspend fun markOwnChildInfoUnsynced(myUid: String): Int

    /**
     * Stamps [familyId] on every child_info row [authorUid] wrote that names no family yet
     * (`FamilyIdBackfill`).
     *
     * By author, never across the table: a co-parent's row of no family belongs to the family with
     * them, and this parent's own belongs to their one family only while nothing says there was an
     * earlier one ([countRowsOfOtherFamilies]). Stamping every null row with one family is how a
     * parent's records written after an unpair were moved into the next family (isolation audit,
     * October 2026). A row that already names a family keeps it.
     *
     * @return How many rows were stamped.
     */
    @Query("UPDATE child_info SET familyId = :familyId WHERE familyId IS NULL AND createdByFirebaseUid = :authorUid")
    suspend fun stampFamilyIdOfAuthor(familyId: String, authorUid: String): Int

    /**
     * How many child_info rows name a family of [myUid]'s other than [familyId] — evidence of an
     * earlier family, which makes this parent's own rows of no family ambiguous.
     */
    @Query(
        "SELECT COUNT(*) FROM child_info WHERE familyId IS NOT NULL AND familyId != '' " +
            "AND familyId != :familyId AND instr(familyId, :myUid) > 0"
    )
    suspend fun countRowsOfOtherFamilies(familyId: String, myUid: String): Int

    /**
     * [myUid]'s own child records that name no family, as they change ("Records without a
     * family"): "only mine" until the parent assigns them. See [EventDao.observeOwnWithoutFamily].
     */
    @Query(
        "SELECT * FROM child_info WHERE createdByFirebaseUid = :myUid AND (familyId IS NULL OR familyId = '') " +
            "AND deletedAtMillis IS NULL ORDER BY childName ASC"
    )
    fun observeOwnWithoutFamily(myUid: String): Flow<List<ChildInfoEntity>>

    /**
     * Gives [myUid]'s own child record [id] the family the parent chose for it, and queues it for
     * upload so the sync sends it with that family's audience (`FamilyAudience.recordPartnerFor`).
     * Only while it still names no family, and never a pending tombstone.
     *
     * @return 1 when the record took the family, 0 otherwise.
     */
    @Query(
        "UPDATE child_info SET familyId = :familyId, syncedToFirestore = 0 " +
            "WHERE id = :id AND createdByFirebaseUid = :myUid AND (familyId IS NULL OR familyId = '') " +
            "AND deletedAtMillis IS NULL"
    )
    suspend fun assignFamily(id: String, familyId: String, myUid: String): Int
}
