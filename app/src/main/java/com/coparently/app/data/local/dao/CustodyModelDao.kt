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
     * Gets the active custody model.
     * There should only be one active model at a time.
     */
    @Query("SELECT * FROM custody_models WHERE isActive = 1 LIMIT 1")
    fun getActiveModel(): Flow<CustodyModelEntity?>

    /**
     * Gets the active custody model synchronously (for calculations).
     */
    @Query("SELECT * FROM custody_models WHERE isActive = 1 LIMIT 1")
    suspend fun getActiveModelSync(): CustodyModelEntity?

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
     * Deactivates all models.
     * Call this before activating a new model.
     */
    @Query("UPDATE custody_models SET isActive = 0")
    suspend fun deactivateAllModels()

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
