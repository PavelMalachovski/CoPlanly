package com.coparently.app.data.remote.firebase

import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Firestore data source for medical records and allergies.
 * Handles all Firestore operations for medical data.
 */
@Singleton
class FirestoreMedicalDataSource @Inject constructor(
    private val firestore: FirebaseFirestore
) {

    companion object {
        private const val MEDICAL_RECORDS_COLLECTION = "medicalRecords"
        private const val ALLERGIES_COLLECTION = "allergies"
    }

    /**
     * Gets medical records for a specific child from Firestore.
     */
    fun getMedicalRecordsForChild(childId: String): Flow<List<Map<String, Any?>>> = callbackFlow {
        val listener = firestore.collection(MEDICAL_RECORDS_COLLECTION)
            .whereEqualTo("childId", childId)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    close(error)
                    return@addSnapshotListener
                }

                val records = snapshot?.documents?.mapNotNull { it.data } ?: emptyList()
                trySend(records)
            }

        awaitClose { listener.remove() }
    }

    /**
     * Upserts a medical record to Firestore.
     */
    suspend fun upsertMedicalRecord(id: String, data: Map<String, Any?>): Result<Unit> {
        return try {
            firestore.collection(MEDICAL_RECORDS_COLLECTION)
                .document(id)
                .set(data)
                .await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Deletes a medical record from Firestore.
     */
    suspend fun deleteMedicalRecord(id: String): Result<Unit> {
        return try {
            firestore.collection(MEDICAL_RECORDS_COLLECTION)
                .document(id)
                .delete()
                .await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Gets allergies for a specific child from Firestore.
     */
    fun getAllergiesForChild(childId: String): Flow<List<Map<String, Any?>>> = callbackFlow {
        val listener = firestore.collection(ALLERGIES_COLLECTION)
            .whereEqualTo("childId", childId)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    close(error)
                    return@addSnapshotListener
                }

                val allergies = snapshot?.documents?.mapNotNull { it.data } ?: emptyList()
                trySend(allergies)
            }

        awaitClose { listener.remove() }
    }

    /**
     * Upserts an allergy to Firestore.
     */
    suspend fun upsertAllergy(id: String, data: Map<String, Any?>): Result<Unit> {
        return try {
            firestore.collection(ALLERGIES_COLLECTION)
                .document(id)
                .set(data)
                .await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Deletes an allergy from Firestore.
     */
    suspend fun deleteAllergy(id: String): Result<Unit> {
        return try {
            firestore.collection(ALLERGIES_COLLECTION)
                .document(id)
                .delete()
                .await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
