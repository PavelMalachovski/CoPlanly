package com.coparently.app.data.remote.firebase

import com.coparently.app.data.session.ProfileIdentity
import kotlinx.coroutines.flow.Flow

/**
 * Who is signed in, as far as a repository needs to know: the account's uid (MON-24 phase 2,
 * Task 8b).
 *
 * The repositories that moved to `:shared-data` asked the app's `FirebaseAuthService` for
 * `getCurrentUser()?.uid` and, for a push's actor, its display name, and since Task 8d for an
 * invitation's sender address and a friend's picture, and nothing else; that service —
 * sign-in, sign-out, the Android SDK's `FirebaseUser` — stays in the app until Auth moves. So these
 * are the questions they ask, and `FirebaseAuthService` answers them on Android; the iPhone's
 * sign-in will answer them there. Since Task 8f the profile's repository asks two more: who is
 * signed in as it changes, and the whole account at one moment.
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

    /**
     * The signed-in account's uid each time the sign-in state is reported: at once, then on every
     * change. It may repeat a uid (Firebase reports the same user again on a token refresh); a
     * reader that wants only real changes adds `distinctUntilChanged`.
     */
    fun observeCurrentUid(): Flow<String?>

    /**
     * What the account carries, read at one moment, or null when nobody is signed in (Task 8f:
     * `UserRepositoryImpl.ensureProfile` resolves the profile's name, address and picture from it,
     * and logs whether the session is anonymous).
     */
    fun getCurrentAccount(): SignedInAccount?
}

/**
 * The signed-in account as the identity provider reports it (Firebase Auth's `FirebaseUser` on
 * Android), as plain values.
 *
 * @property uid The account's uid.
 * @property displayName Its top-level display name, if any.
 * @property email Its top-level e-mail address, if any.
 * @property photoUrl The address of its top-level picture, if any.
 * @property isAnonymous Whether it is an anonymous session.
 * @property providers Each linked provider's own identity (`FirebaseUser.providerData`), in the
 *   order the provider reports them.
 */
data class SignedInAccount(
    val uid: String,
    val displayName: String?,
    val email: String?,
    val photoUrl: String?,
    val isAnonymous: Boolean,
    val providers: List<ProfileIdentity.ProviderIdentity>
)
