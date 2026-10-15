package com.coparently.app.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.coparently.app.data.local.entity.CustodyModelEntity
import kotlinx.coroutines.flow.Flow

/**
 * Data Access Object for CustodyModelEntity.
 * Provides methods to access custody model configurations from the Room database.
 */
@Dao
interface CustodyModelDao {
    /**
     * Every active row: one per family this phone has followed, plus any that names no family
     * (schema 45). `CustodyModelRepository` picks the family on screen's through
     * `ActiveCustodyRow`; nothing reads this list whole.
     */
    @Query("SELECT * FROM custody_models WHERE isActive = 1")
    fun observeActiveModels(): Flow<List<CustodyModelEntity>>

    /** [observeActiveModels], read once. */
    @Query("SELECT * FROM custody_models WHERE isActive = 1")
    suspend fun getActiveModelsSync(): List<CustodyModelEntity>

    /**
     * Deactivates [familyId]'s rows and every row that names no family, before that family's new
     * row is inserted; with a null [familyId], only the rows that name none.
     *
     * Other families' rows stay active: each is the schedule of a family this phone is not
     * showing. A row that names no family goes with any family's write, because it answers for a
     * family only while the parent has exactly one, and left active it would come back as that
     * family's the day the parent is down to one again — with the other family's pattern in it.
     */
    @Query("UPDATE custody_models SET isActive = 0 WHERE familyId IS NULL OR familyId = :familyId")
    suspend fun deactivateFamily(familyId: String?)

    /**
     * Gets all custody models (including inactive).
     */
    @Query("SELECT * FROM custody_models ORDER BY createdAt DESC")
    fun getAllModels(): Flow<List<CustodyModelEntity>>

    /**
     * Gets a custody model by ID.
     */
    @Query("SELECT * FROM custody_models WHERE id = :id")
    suspend fun getModelById(id: String): CustodyModelEntity?

    /**
     * Inserts a new custody model.
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertModel(model: CustodyModelEntity)

    /**
     * Updates an existing custody model.
     */
    @Update
    suspend fun updateModel(model: CustodyModelEntity)

    /**
     * Deletes a custody model.
     */
    @Delete
    suspend fun deleteModel(model: CustodyModelEntity)

    /**
     * Activates a specific model by ID.
     */
    @Query("UPDATE custody_models SET isActive = 1 WHERE id = :id")
    suspend fun activateModel(id: String)

    /**
     * Deletes a custody model by ID.
     */
    @Query("DELETE FROM custody_models WHERE id = :id")
    suspend fun deleteModelById(id: String)
}
