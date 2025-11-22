package com.coparently.app.domain.repository

import com.coparently.app.domain.model.Allergy
import com.coparently.app.domain.model.MedicalRecord
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

/**
 * Repository interface for managing medical records and allergies.
 * Part of the domain layer in Clean Architecture.
 */
interface MedicalRepository {

    /**
     * Gets all medical records for a specific child as a Flow.
     *
     * @param childId The child ID
     * @return Flow of list of medical records
     */
    fun getMedicalRecordsForChild(childId: String): Flow<List<MedicalRecord>>

    /**
     * Gets medical records for a specific child within a date range.
     *
     * @param childId The child ID
     * @param startDate Start date of the range
     * @param endDate End date of the range
     * @return Flow of list of medical records
     */
    fun getMedicalRecordsForPeriod(
        childId: String,
        startDate: LocalDate,
        endDate: LocalDate
    ): Flow<List<MedicalRecord>>

    /**
     * Gets a single medical record by ID.
     *
     * @param id The medical record ID
     * @return The medical record or null if not found
     */
    suspend fun getMedicalRecordById(id: String): MedicalRecord?

    /**
     * Inserts or updates a medical record.
     *
     * @param medicalRecord The medical record to insert or update
     */
    suspend fun upsertMedicalRecord(medicalRecord: MedicalRecord)

    /**
     * Deletes a medical record.
     *
     * @param medicalRecord The medical record to delete
     */
    suspend fun deleteMedicalRecord(medicalRecord: MedicalRecord)

    /**
     * Gets all allergies for a specific child as a Flow.
     *
     * @param childId The child ID
     * @return Flow of list of allergies
     */
    fun getAllergiesForChild(childId: String): Flow<List<Allergy>>

    /**
     * Gets a single allergy by ID.
     *
     * @param id The allergy ID
     * @return The allergy or null if not found
     */
    suspend fun getAllergyById(id: String): Allergy?

    /**
     * Inserts or updates an allergy.
     *
     * @param allergy The allergy to insert or update
     */
    suspend fun upsertAllergy(allergy: Allergy)

    /**
     * Deletes an allergy.
     *
     * @param allergy The allergy to delete
     */
    suspend fun deleteAllergy(allergy: Allergy)

    /**
     * Syncs local medical data with Firestore.
     */
    suspend fun syncWithFirestore()
}
