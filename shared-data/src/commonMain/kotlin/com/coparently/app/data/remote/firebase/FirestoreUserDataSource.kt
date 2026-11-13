package com.coparently.app.data.remote.firebase

import com.coparently.app.data.platform.logger

/**
 * Remote data source for users using Firestore.
 * Handles all Firestore operations for user profiles.
 */
class FirestoreUserDataSource(
    private val firestore: FirestoreClient
) {
    private val usersCollection = "users"
    private val invitationsCollection = "invitations"

    /**
     * Gets a user by Firebase UID.
     * Uses DEFAULT source which tries server first, falls back to cache automatically if offline.
     */
    suspend fun getUserById(uid: String): Map<String, Any?>? {
        // Validate uid before making request
        if (uid.isBlank()) {
            logger.w("FirestoreUserDataSource", "Attempted to get user with blank uid")
            return null
        }

        return try {
            readUserOrThrow(uid)
        } catch (e: Exception) {
            logger.e("FirestoreUserDataSource", "Failed to get user", e)
            null
        }
    }

    /**
     * One read of `users/{uid}`, as [getUserById] makes it, that tells a missing document apart
     * from a read that failed: the document's fields, null when there is no such document, and
     * the failure thrown when the read itself did not succeed.
     *
     * [getUserById] answers null for both, which is right for a caller that only shows what it
     * finds. A caller that *writes* from what it read must not take a failed read for an empty
     * document: `UserRepositoryImpl.ensureProfile` once seeded the local profile, and merged a name
     * guessed from the e-mail address over the stored one, from a read that had failed right after
     * a sign-in on the iPhone.
     */
    suspend fun readUserOrThrow(uid: String): Map<String, Any?>? {
        // Use DEFAULT source - tries server first, automatically falls back to cache if offline
        val snapshot = firestore.get(DocumentPath(usersCollection, uid))

        logger.d(
            "FirestoreUserDataSource",
            "Got user from ${if (snapshot.isFromCache) "cache" else "server"}: $uid"
        )
        return snapshot.data
    }

    /**
     * Writes [userData] into the user profile, creating the document if it does not exist.
     *
     * Always a `set(..., merge)`: `users/{uid}` carries state this app is not the only
     * writer of — `partnerId` and `pairedAt` come from the pairing Cloud Function,
     * `pendingRevocationOf` from the unpair sweep — so a full `.set()` here would silently
     * delete whichever of them the caller's map happened to omit.
     */
    suspend fun updateUser(uid: String, userData: Map<String, Any?>): Result<Unit> {
        return try {
            // Use set with merge instead of update to create document if it doesn't exist
            firestore.set(DocumentPath(usersCollection, uid), userData, SetMerge.Merge)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
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
            firestore.set(DocumentPath(invitationsCollection, invitationId), invitationData)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Gets invitation by ID.
     * Uses DEFAULT source which tries server first, falls back to cache automatically if offline.
     */
    suspend fun getInvitationById(invitationId: String): Map<String, Any?>? {
        return try {
            // Use DEFAULT source - tries server first, automatically falls back to cache if offline
            val snapshot = firestore.get(DocumentPath(invitationsCollection, invitationId))

            logger.d(
                "FirestoreUserDataSource",
                "Got invitation from ${if (snapshot.isFromCache) "cache" else "server"}: $invitationId"
            )
            snapshot.data
        } catch (e: Exception) {
            logger.e("FirestoreUserDataSource", "Failed to get an invitation", e)
            null
        }
    }

    /**
     * Updates invitation status.
     */
    suspend fun updateInvitationStatus(invitationId: String, status: String): Result<Unit> {
        return try {
            firestore.update(DocumentPath(invitationsCollection, invitationId), "status", status)
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
            firestore.delete(DocumentPath(invitationsCollection, invitationId))
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
