package com.coparently.app.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.coparently.app.data.local.entity.MedicalRecordEntity
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

/**
 * DAO for medical records table.
 * Provides database operations for medical records.
 */
@Dao
interface MedicalRecordDao {

    /**
     * Gets all medical records for a specific child.
     */
    @Query("SELECT * FROM medical_records WHERE childId = :childId ORDER BY date DESC")
    fun getMedicalRecordsForChild(childId: String): Flow<List<MedicalRecordEntity>>

    /**
     * Gets medical records for a specific child within a date range.
     */
    @Query("""
        SELECT * FROM medical_records
        WHERE childId = :childId
        AND date BETWEEN :startDate AND :endDate
        ORDER BY date DESC
    """)
    fun getMedicalRecordsForPeriod(
        childId: String,
        startDate: LocalDate,
        endDate: LocalDate
    ): Flow<List<MedicalRecordEntity>>

    /**
     * Gets a single medical record by ID.
     */
    @Query("SELECT * FROM medical_records WHERE id = :id")
    suspend fun getMedicalRecordById(id: String): MedicalRecordEntity?

    /**
     * Inserts a medical record. Replaces on conflict.
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMedicalRecord(medicalRecord: MedicalRecordEntity)

    /**
     * Updates a medical record.
     */
    @Update
    suspend fun updateMedicalRecord(medicalRecord: MedicalRecordEntity)

    /**
     * Deletes a medical record by ID.
     */
    @Query("DELETE FROM medical_records WHERE id = :id")
    suspend fun deleteMedicalRecordById(id: String)

    /**
     * Deletes a medical record.
     */
    @Delete
    suspend fun deleteMedicalRecord(medicalRecord: MedicalRecordEntity)

    /**
     * Gets all unsynced medical records.
     */
    @Query("SELECT * FROM medical_records WHERE syncedToFirestore = 0")
    suspend fun getUnsyncedMedicalRecords(): List<MedicalRecordEntity>

    /**
     * Marks a medical record as synced.
     */
    @Query("UPDATE medical_records SET syncedToFirestore = 1 WHERE id = :id")
    suspend fun markAsSynced(id: String)
}
