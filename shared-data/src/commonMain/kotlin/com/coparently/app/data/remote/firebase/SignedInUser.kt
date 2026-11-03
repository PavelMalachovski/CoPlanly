package com.coparently.app.data.remote.firebase

/**
 * Who is signed in, as far as a repository needs to know: the account's uid (MON-24 phase 2,
 * Task 8b).
 *
 * The repositories that moved to `:shared-data` asked the app's `FirebaseAuthService` for
 * `getCurrentUser()?.uid` and, for a push's actor, its display name, and nothing else; that service —
 * sign-in, sign-out, the Android SDK's `FirebaseUser` — stays in the app until Auth moves. So these
 * are the questions they ask, and `FirebaseAuthService` answers them on Android; the iPhone's
 * sign-in will answer them there.
 */
interface SignedInUser {

    /** The signed-in account's uid, or null when nobody is signed in. */
    fun getCurrentUid(): String?

    /** The signed-in account's display name, or null when nobody is signed in or it has none. */
    fun getCurrentDisplayName(): String?
}
