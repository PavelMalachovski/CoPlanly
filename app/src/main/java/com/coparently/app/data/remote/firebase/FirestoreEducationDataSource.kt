package com.coparently.app.data.remote.firebase

import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Firestore data source for education data (grades and school events).
 */
@Singleton
class FirestoreEducationDataSource @Inject constructor(
    private val firestore: FirebaseFirestore
) {

    companion object {
        private const val GRADES_COLLECTION = "grades"
        private const val SCHOOL_EVENTS_COLLECTION = "schoolEvents"
    }

    // Grades

    fun getGradesForChild(childId: String): Flow<List<Map<String, Any?>>> = callbackFlow {
        val listener = firestore.collection(GRADES_COLLECTION)
            .whereEqualTo("childId", childId)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    close(error)
                    return@addSnapshotListener
                }
                val grades = snapshot?.documents?.mapNotNull { it.data } ?: emptyList()
                trySend(grades)
            }
        awaitClose { listener.remove() }
    }

    suspend fun upsertGrade(id: String, data: Map<String, Any?>): Result<Unit> {
        return try {
            firestore.collection(GRADES_COLLECTION).document(id).set(data).await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun deleteGrade(id: String): Result<Unit> {
        return try {
            firestore.collection(GRADES_COLLECTION).document(id).delete().await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // School Events

    fun getSchoolEventsForChild(childId: String): Flow<List<Map<String, Any?>>> = callbackFlow {
        val listener = firestore.collection(SCHOOL_EVENTS_COLLECTION)
            .whereEqualTo("childId", childId)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    close(error)
                    return@addSnapshotListener
                }
                val events = snapshot?.documents?.mapNotNull { it.data } ?: emptyList()
                trySend(events)
            }
        awaitClose { listener.remove() }
    }

    suspend fun upsertSchoolEvent(id: String, data: Map<String, Any?>): Result<Unit> {
        return try {
            firestore.collection(SCHOOL_EVENTS_COLLECTION).document(id).set(data).await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun deleteSchoolEvent(id: String): Result<Unit> {
        return try {
            firestore.collection(SCHOOL_EVENTS_COLLECTION).document(id).delete().await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
