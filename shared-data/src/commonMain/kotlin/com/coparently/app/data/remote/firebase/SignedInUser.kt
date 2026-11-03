package com.coparently.app.data.remote.firebase

/**
 * Who is signed in, as far as a repository needs to know: the account's uid (MON-24 phase 2,
 * Task 8b).
 *
 * The repositories that moved to `:shared-data` asked the app's `FirebaseAuthService` for
 * `getCurrentUser()?.uid` and, for a push's actor, its display name, and since Task 8d for an
 * invitation's sender address and a friend's picture, and nothing else; that service —
 * sign-in, sign-out, the Android SDK's `FirebaseUser` — stays in the app until Auth moves. So these
 * are the questions they ask, and `FirebaseAuthService` answers them on Android; the iPhone's
 * sign-in will answer them there.
 */
interface SignedInUser {

    /** The signed-in account's uid, or null when nobody is signed in. */
    fun getCurrentUid(): String?

    /** The signed-in account's display name, or null when nobody is signed in or it has none. */
    fun getCurrentDisplayName(): String?

    /**
     * The signed-in account's e-mail address, or null when nobody is signed in or it has none
     * (Task 8d: an invitation names its sender's address when their profile has no name).
     */
    fun getCurrentEmail(): String?

    /**
     * The address of the signed-in account's own picture (a Google account's), or null when nobody
     * is signed in or it has none (Task 8d: a calendar friend's first profile takes it).
     */
    fun getCurrentPhotoUrl(): String?
}
