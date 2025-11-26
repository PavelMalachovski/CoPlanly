package com.coparently.app.data.remote.firebase

import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Source
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Remote data source for users using Firestore.
 * Handles all Firestore operations for user profiles.
 */
@Singleton
class FirestoreUserDataSource @Inject constructor(
    private val firestore: FirebaseFirestore
) {
    private val usersCollection = "users"
    private val invitationsCollection = "invitations"

    /**
     * Gets a user by Firebase UID.
     * Tries to get from server first, falls back to cache if offline.
     */
    suspend fun getUserById(uid: String): Map<String, Any?>? {
        return try {
            // Try to get from server first
            firestore.collection(usersCollection)
                .document(uid)
                .get(Source.SERVER)
                .await()
                .data
        } catch (e: Exception) {
            // If offline or server unavailable, try cache
            try {
                android.util.Log.d("FirestoreUserDataSource", "Server unavailable, trying cache for user: $uid", e)
                firestore.collection(usersCollection)
                    .document(uid)
                    .get(Source.CACHE)
                    .await()
                    .data
            } catch (cacheException: Exception) {
                android.util.Log.w("FirestoreUserDataSource", "Failed to get user from cache: $uid", cacheException)
                null
            }
        }
    }

    /**
     * Inserts or updates a user profile.
     */
    suspend fun upsertUser(uid: String, userData: Map<String, Any?>): Result<Unit> {
        return try {
            firestore.collection(usersCollection)
                .document(uid)
                .set(userData)
                .await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Updates a user profile.
     */
    suspend fun updateUser(uid: String, userData: Map<String, Any?>): Result<Unit> {
        return try {
            firestore.collection(usersCollection)
                .document(uid)
                .update(userData)
                .await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Gets a user by email.
     * Tries to get from server first, falls back to cache if offline.
     */
    suspend fun getUserByEmail(email: String): Map<String, Any?>? {
        return try {
            // Try to get from server first
            val snapshot = firestore.collection(usersCollection)
                .whereEqualTo("email", email)
                .limit(1)
                .get(Source.SERVER)
                .await()
            snapshot.documents.firstOrNull()?.data
        } catch (e: Exception) {
            // If offline or server unavailable, try cache
            try {
                android.util.Log.d("FirestoreUserDataSource", "Server unavailable, trying cache for email: $email", e)
                val snapshot = firestore.collection(usersCollection)
                    .whereEqualTo("email", email)
                    .limit(1)
                    .get(Source.CACHE)
                    .await()
                snapshot.documents.firstOrNull()?.data
            } catch (cacheException: Exception) {
                android.util.Log.w("FirestoreUserDataSource", "Failed to get user by email from cache: $email", cacheException)
                null
            }
        }
    }

    /**
     * Creates an invitation.
     */
    @Suppress("UNUSED_PARAMETER")
    suspend fun createInvitation(
        invitationId: String,
        fromUserId: String,
        toEmail: String,
        invitationData: Map<String, Any?>
    ): Result<Unit> {
        // Parameters are already included in invitationData
        return try {
            firestore.collection(invitationsCollection)
                .document(invitationId)
                .set(invitationData)
                .await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Gets invitation by ID.
     * Tries to get from server first, falls back to cache if offline.
     */
    suspend fun getInvitationById(invitationId: String): Map<String, Any?>? {
        return try {
            // Try to get from server first
            firestore.collection(invitationsCollection)
                .document(invitationId)
                .get(Source.SERVER)
                .await()
                .data
        } catch (e: Exception) {
            // If offline or server unavailable, try cache
            try {
                android.util.Log.d("FirestoreUserDataSource", "Server unavailable, trying cache for invitation: $invitationId", e)
                firestore.collection(invitationsCollection)
                    .document(invitationId)
                    .get(Source.CACHE)
                    .await()
                    .data
            } catch (cacheException: Exception) {
                android.util.Log.w("FirestoreUserDataSource", "Failed to get invitation from cache: $invitationId", cacheException)
                null
            }
        }
    }

    /**
     * Gets invitations for a specific email.
     * Tries to get from server first, falls back to cache if offline.
     */
    suspend fun getInvitationsForEmail(email: String): List<Map<String, Any?>> {
        return try {
            // Try to get from server first
            val snapshot = firestore.collection(invitationsCollection)
                .whereEqualTo("toEmail", email)
                .whereEqualTo("status", "pending")
                .get(Source.SERVER)
                .await()
            snapshot.documents.map { it.data!! }
        } catch (e: Exception) {
            // If offline or server unavailable, try cache
            try {
                android.util.Log.d("FirestoreUserDataSource", "Server unavailable, trying cache for invitations: $email", e)
                val snapshot = firestore.collection(invitationsCollection)
                    .whereEqualTo("toEmail", email)
                    .whereEqualTo("status", "pending")
                    .get(Source.CACHE)
                    .await()
                snapshot.documents.map { it.data!! }
            } catch (cacheException: Exception) {
                android.util.Log.w("FirestoreUserDataSource", "Failed to get invitations from cache: $email", cacheException)
                emptyList()
            }
        }
    }

    /**
     * Updates invitation status.
     */
    suspend fun updateInvitationStatus(invitationId: String, status: String): Result<Unit> {
        return try {
            firestore.collection(invitationsCollection)
                .document(invitationId)
                .update("status", status)
                .await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Deletes an invitation.
     */
    suspend fun deleteInvitation(invitationId: String): Result<Unit> {
        return try {
            firestore.collection(invitationsCollection)
                .document(invitationId)
                .delete()
                .await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}

