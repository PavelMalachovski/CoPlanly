package com.coparently.app.data.remote.firebase

/**
 * The sign-in session as the app's start decides it and sign-out ends it (MON-24 phase 2, Task 10):
 * what `AuthStateViewModel` asks of Firebase Auth, which stays in the app.
 *
 * The app's `FirebaseAuthService` answers it on Android, beside [SignedInUser]; the iPhone's sign-in
 * will answer it there (phase 3).
 */
interface AuthSession {

    /**
     * Waits, briefly, for authentication to be ready, then answers the signed-in account's uid, or
     * null when nobody is signed in (or the wait ran out with nobody known).
     */
    suspend fun awaitSignedInUid(): String?

    /** Ends the session. A failure is returned, never thrown. */
    suspend fun signOutCompletely(): Result<Unit>
}
