package com.coparently.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.coparently.app.data.local.entity.PetEntity
import kotlinx.coroutines.flow.Flow

/**
 * DAO for accessing pet data in the local Room database.
 * Provides CRUD operations for [PetEntity].
 */
@Dao
interface PetDao {

    /**
     * Gets all pets as a Flow, ordered by name.
     */
    @Query("SELECT * FROM pets WHERE deletedAtMillis IS NULL ORDER BY name ASC")
    fun getAllPets(): Flow<List<PetEntity>>

    /**
     * Gets a pet by ID.
     *
     * @param id The pet ID
     * @return The pet or null if not found
     */
    @Query("SELECT * FROM pets WHERE id = :id")
    suspend fun getPetById(id: String): PetEntity?

    /**
     * Gets a pet by ID as a Flow.
     *
     * @param id The pet ID
     */
    @Query("SELECT * FROM pets WHERE id = :id AND deletedAtMillis IS NULL")
    fun observePetById(id: String): Flow<PetEntity?>

    /**
     * Inserts a pet, replacing any existing row with the same ID.
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPet(pet: PetEntity)

    /**
     * Inserts multiple pets.
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAllPets(pets: List<PetEntity>)

    /**
     * Updates a pet.
     */
    @Update
    suspend fun updatePet(pet: PetEntity)

    /**
     * Deletes a pet by ID.
     */
    @Query("DELETE FROM pets WHERE id = :id")
    suspend fun deletePetById(id: String)

    /**
     * Gets all pets that need to be synced to Firestore.
     *
     * **Deliberately not filtered on `deletedAtMillis`** — this is the outbox, and a pending
     * tombstone is what it exists to carry. See [ChildInfoDao.getUnsyncedChildInfo].
     */
    @Query("SELECT * FROM pets WHERE syncedToFirestore = 0")
    suspend fun getUnsyncedPets(): List<PetEntity>

    /**
     * Marks a pet deleted, leaving the row in place as an outbox entry.
     *
     * The `child_info` twin, and see [ChildInfoDao.markDeleted] for why the WHERE clause
     * excludes rows that are already tombstones.
     *
     * @param id The pet's id.
     * @param deletedAtMillis When it was deleted, epoch millis.
     * @return How many rows were marked — zero if it was already a tombstone.
     */
    @Query(
        "UPDATE pets SET deletedAtMillis = :deletedAtMillis, syncedToFirestore = 0 " +
            "WHERE id = :id AND deletedAtMillis IS NULL"
    )
    suspend fun markDeleted(id: String, deletedAtMillis: Long): Int

    /**
     * Marks a pet as synced to Firestore.
     */
    @Query("UPDATE pets SET syncedToFirestore = 1 WHERE id = :id")
    suspend fun markAsSynced(id: String)

    /**
     * Re-queues this user's own pet rows for upload, so their audience is recomputed.
     *
     * Mirrors `ChildInfoDao.markOwnChildInfoUnsynced`. Rows whose `createdByFirebaseUid` is
     * null are deliberately not matched — nothing distinguishes this user's un-stamped row
     * from anybody else's.
     *
     * @param myUid Firebase UID of the signed-in user.
     * @return How many rows were re-queued.
     */
    @Query(
        "UPDATE pets SET syncedToFirestore = 0 " +
            "WHERE createdByFirebaseUid = :myUid AND deletedAtMillis IS NULL"
    )
    suspend fun markOwnPetsUnsynced(myUid: String): Int

    /**
     * Stamps [familyId] on every pets row [authorUid] wrote that names no family yet
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
    @Query("UPDATE pets SET familyId = :familyId WHERE familyId IS NULL AND createdByFirebaseUid = :authorUid")
    suspend fun stampFamilyIdOfAuthor(familyId: String, authorUid: String): Int

    /**
     * How many pets rows name a family of [myUid]'s other than [familyId] — evidence of an
     * earlier family, which makes this parent's own rows of no family ambiguous.
     */
    @Query(
        "SELECT COUNT(*) FROM pets WHERE familyId IS NOT NULL AND familyId != '' " +
            "AND familyId != :familyId AND instr(familyId, :myUid) > 0"
    )
    suspend fun countRowsOfOtherFamilies(familyId: String, myUid: String): Int
}
