package com.coparently.app.data.remote.firebase

import com.coparently.app.data.platform.logger
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

/**
 * Service for Firebase Authentication.
 * Handles user authentication using email/password and provides Firebase UID.
 *
 * It is also the Android [SignedInUser], the questions the repositories in `:shared-data` ask of
 * the signed-in account.
 *
 * @property firebaseAuth Firebase Authentication instance
 */
class FirebaseAuthService(
    private val firebaseAuth: FirebaseAuth
) : SignedInUser {
    /**
     * Gets the current Firebase user.
     */
    fun getCurrentUser(): FirebaseUser? {
        return firebaseAuth.currentUser
    }

    /**
     * Gets the current user's Firebase UID.
     */
    override fun getCurrentUid(): String? {
        return firebaseAuth.currentUser?.uid
    }

    /**
     * Gets the current user's display name, as the account carries it.
     */
    override fun getCurrentDisplayName(): String? = firebaseAuth.currentUser?.displayName

    /**
     * Gets the current user's e-mail address, as the account carries it.
     */
    override fun getCurrentEmail(): String? = firebaseAuth.currentUser?.email

    /**
     * Gets the address of the current user's own picture, as the account carries it.
     */
    override fun getCurrentPhotoUrl(): String? = firebaseAuth.currentUser?.photoUrl?.toString()

    /**
     * Checks if user is authenticated.
     */
    fun isAuthenticated(): Boolean {
        return firebaseAuth.currentUser != null
    }

    /**
     * Signs in with email and password.
     *
     * @param email User's email address
     * @param password User's password
     * @return Result containing FirebaseUser on success or error message on failure
     */
    suspend fun signInWithEmail(email: String, password: String): Result<FirebaseUser> {
        return try {
            val result = firebaseAuth.signInWithEmailAndPassword(email, password).await()
            Result.success(result.user!!)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Creates a new account with email and password.
     *
     * @param email User's email address
     * @param password User's password
     * @return Result containing FirebaseUser on success or error message on failure
     */
    suspend fun createAccountWithEmail(email: String, password: String): Result<FirebaseUser> {
        return try {
            // Log Firebase configuration for debugging
            val projectId = firebaseAuth.app.options.projectId
            val apiKey = firebaseAuth.app.options.apiKey
            logger.d(
                "FirebaseAuthService",
                "Firebase config - projectId: $projectId, apiKey: ${apiKey?.take(10)}..."
            )

            val result = firebaseAuth.createUserWithEmailAndPassword(email, password).await()
            Result.success(result.user!!)
        } catch (e: Exception) {
            // Log detailed error information
            logger.e("FirebaseAuthService", "Account creation failed", e)
            logger.e("FirebaseAuthService", "Error details - message: ${e.message}, cause: ${e.cause}")
            Result.failure(e)
        }
    }

    /**
     * Signs in with Google ID token.
     *
     * @param idToken Google ID token from Google Sign-In
     * @return Result containing FirebaseUser on success or error message on failure
     */
    suspend fun signInWithGoogleIdToken(idToken: String): Result<FirebaseUser> {
        return try {
            val credential = GoogleAuthProvider.getCredential(idToken, null)
            val result = firebaseAuth.signInWithCredential(credential).await()
            Result.success(result.user!!)
        } catch (e: Exception) {
            logger.e("FirebaseAuthService", "Google sign-in failed", e)
            Result.failure(e)
        }
    }

    /**
     * Sends a password reset email.
     *
     * @param email User's email address
     * @return Result indicating success or failure
     */
    suspend fun sendPasswordResetEmail(email: String): Result<Unit> {
        return try {
            firebaseAuth.sendPasswordResetEmail(email).await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Signs out the current user.
     */
    fun signOut() {
        firebaseAuth.signOut()
    }

    /**
     * Signs out the current user from Firebase authentication.
     * This should be called when the user wants to sign out of the app completely.
     */
    suspend fun signOutCompletely(): Result<Unit> {
        return try {
            firebaseAuth.signOut()
            Result.success(Unit)
        } catch (e: Exception) {
            logger.e("FirebaseAuthService", "Error signing out: ${e.message}", e)
            Result.failure(e)
        }
    }

    /**
     * Deletes the current user account.
     *
     * @return Result indicating success or failure
     */
    suspend fun deleteCurrentUser(): Result<Unit> {
        return try {
            firebaseAuth.currentUser?.delete()?.await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Gets the authentication state as a Flow.
     * Emits the current user whenever auth state changes.
     */
    fun getAuthStateFlow(): Flow<FirebaseUser?> = callbackFlow {
        val listener = FirebaseAuth.AuthStateListener { auth ->
            trySend(auth.currentUser)
        }

        firebaseAuth.addAuthStateListener(listener)
        awaitClose { firebaseAuth.removeAuthStateListener(listener) }
    }

    /**
     * Waits for authentication to be ready and returns the current user.
     * This is useful when you need to ensure auth state is initialized.
     *
     * @param timeoutMs Maximum time to wait in milliseconds (default: 5000ms)
     * @return FirebaseUser if authenticated, null if not authenticated or timeout
     */
    suspend fun waitForAuthReady(timeoutMs: Long = 5000): FirebaseUser? {
        val startTime = System.currentTimeMillis()

        while (System.currentTimeMillis() - startTime < timeoutMs) {
            // Check if Firebase is properly initialized by verifying projectId exists
            if (firebaseAuth.app.options.projectId != null) {
                // Firebase is ready, return current user (may be null if not signed in)
                return getCurrentUser()
            }
            kotlinx.coroutines.delay(100) // Wait 100ms before checking again
        }

        // Timeout reached, return whatever we have
        return getCurrentUser()
    }
}
