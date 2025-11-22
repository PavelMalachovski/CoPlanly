package com.coparently.app.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.coparently.app.data.local.entity.AllergyEntity
import kotlinx.coroutines.flow.Flow

/**
 * DAO for allergies table.
 * Provides database operations for allergies.
 */
@Dao
interface AllergyDao {

    /**
     * Gets all allergies for a specific child.
     */
    @Query("SELECT * FROM allergies WHERE childId = :childId ORDER BY severity DESC")
    fun getAllergiesForChild(childId: String): Flow<List<AllergyEntity>>

    /**
     * Gets a single allergy by ID.
     */
    @Query("SELECT * FROM allergies WHERE id = :id")
    suspend fun getAllergyById(id: String): AllergyEntity?

    /**
     * Inserts an allergy. Replaces on conflict.
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAllergy(allergy: AllergyEntity)

    /**
     * Updates an allergy.
     */
    @Update
    suspend fun updateAllergy(allergy: AllergyEntity)

    /**
     * Deletes an allergy by ID.
     */
    @Query("DELETE FROM allergies WHERE id = :id")
    suspend fun deleteAllergyById(id: String)

    /**
     * Deletes an allergy.
     */
    @Delete
    suspend fun deleteAllergy(allergy: AllergyEntity)

    /**
     * Gets all unsynced allergies.
     */
    @Query("SELECT * FROM allergies WHERE syncedToFirestore = 0")
    suspend fun getUnsyncedAllergies(): List<AllergyEntity>

    /**
     * Marks an allergy as synced.
     */
    @Query("UPDATE allergies SET syncedToFirestore = 1 WHERE id = :id")
    suspend fun markAsSynced(id: String)
}
