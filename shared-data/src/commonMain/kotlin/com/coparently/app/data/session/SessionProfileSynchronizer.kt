package com.coparently.app.data.session

import com.coparently.app.data.platform.logger
import com.coparently.app.data.remote.firebase.SignedInUser
import com.coparently.app.domain.repository.UserRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

/**
 * Keeps the signed-in user's profile document in existence and up to date, for the whole
 * session rather than for whoever happens to open a particular screen.
 *
 * Firebase Auth creates an account, not a `users/{uid}` document. Nothing else in the app
 * writes the user's own `name` and `email` there: the FCM registration writes `fcmToken`
 * with a merge (so the document exists, carrying that one key), the pairing Cloud Function
 * adds `partnerId`/`pairedAt`, and the app itself only read it. The
 * co-parent's pairing card reads `name`/`email` from that same document, so the gap
 * surfaced on the other phone as "Unknown"/"Email unavailable".
 *
 * The auth-state boundary is the correct trigger because it is the one event every signed-in
 * user passes through:
 *
 * - `AuthStateListener` fires immediately on registration, so a session restored from a
 *   build that never wrote a profile is repaired at the next cold start — no re-sign-in and
 *   no visit to the pairing screen required;
 * - it fires again on every sign-in and account switch, so a fresh account gets a profile
 *   before it can be paired with.
 *
 * The earlier version of this logic lived in the pairing ViewModel and therefore only ever
 * ran for a user who opened the pairing screen; the pairing rewrite dropped it, which is how
 * the underlying gap became visible.
 *
 * In `:shared-data` since MON-24 phase 2 (Task 8j): the sign-in state is [SignedInUser]'s
 * `observeCurrentUid` (the app's `FirebaseAuthService` reports it from its `AuthStateListener`, as
 * `getAuthStateFlow` did), and the wipe before the profile write is the platform's
 * [AccountConsistency] (the app's `AccountSwitchGuard`). The parameters kept their names.
 */
class SessionProfileSynchronizer(
    private val authService: SignedInUser,
    private val userRepository: UserRepository,
    private val accountSwitchGuard: AccountConsistency
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /**
     * Starts observing the auth state for the lifetime of the process.
     *
     * Called once from `CoPlanlyApplication.onCreate`. Safe to call again — the underlying
     * work is idempotent — but there is no reason to.
     */
    fun start() {
        scope.launch { observeSessions() }
    }

    /**
     * Collects distinct signed-in UIDs and repairs the profile for each.
     *
     * Distinct on the UID: Firebase re-emits the same user on token refresh, and there is no
     * point re-reading the profile for that. Internal for the tests.
     */
    internal suspend fun observeSessions() {
        authService.observeCurrentUid()
            .distinctUntilChanged()
            .collect { uid ->
                if (uid == null) return@collect
                // Before the profile write: if a different account just signed in, the wipe
                // must land before anything reads or uploads the previous account's rows.
                accountSwitchGuard.ensureAccountConsistency()
                logger.d(TAG, "Ensuring the profile document for the signed-in user")
                userRepository.ensureProfile()
            }
    }

    private companion object {
        const val TAG = "SessionProfileSync"
    }
}
