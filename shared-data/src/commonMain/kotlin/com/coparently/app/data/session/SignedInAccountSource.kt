package com.coparently.app.data.session

import com.coparently.app.data.local.dao.UserDao
import com.coparently.app.data.local.entity.UserEntity
import com.coparently.app.data.remote.firebase.SignedInAccount
import com.coparently.app.data.remote.firebase.SignedInUser
import com.coparently.app.domain.model.AccountSummary
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map

/**
 * "Who am I signed in as", as a flow, for the screens that need to say so out loud.
 *
 * Two parents each holding a phone cannot tell their accounts apart from the pairing
 * screen alone — both phones show an invite code and nothing else identifying — so the
 * pairing screen and the Settings account card both render this.
 *
 * It combines the two halves of the identity rather than picking one:
 *
 * - **Firebase Auth** is the freshest and, for a Google account, the only source of the
 *   display name and the avatar. It is available the instant auth restores a session,
 *   before any Firestore round-trip.
 * - **The local Room row** is what survives an email/password sign-in (where Auth reports
 *   no `displayName` and no `photoUrl` at all) and is kept current by
 *   [SessionProfileSynchronizer]. Observing it, rather than reading it once, means the
 *   block fills in by itself the moment that repair lands.
 *
 * The preference between them is [ProfileIdentity]'s, the same one the write path uses, so
 * what this shows and what the co-parent reads out of Firestore cannot drift apart.
 *
 * In `:shared-data` since MON-24 phase 2 (Task 8j). The session is [SignedInUser]'s: the uid each
 * time the sign-in state is reported, and the account read when the row arrives, which is what
 * reading the `FirebaseUser` the state reported did, since Firebase keeps one `FirebaseUser` per
 * account and updates it in place. The parameters kept their names.
 */
class SignedInAccountSource(
    private val authService: SignedInUser,
    private val userDao: UserDao
) {

    /**
     * The signed-in account, or null when signed out (or when the account carries no
     * identity worth rendering at all).
     *
     * Re-emits on sign-in, sign-out, account switch and any local profile change.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    fun observe(): Flow<AccountSummary?> = authService.observeCurrentUid()
        .flatMapLatest { uid ->
            if (uid == null) {
                flowOf(null)
            } else {
                userDao.observeUserById(uid).map { row ->
                    // Another account signed in between the report and the row: that one's
                    // summary follows its own report, so this one says nothing.
                    authService.getCurrentAccount()?.takeIf { it.uid == uid }?.let { summarize(it, row) }
                }
            }
        }
        // Firebase re-emits the same user on every token refresh, and Room re-emits the
        // whole row when an unrelated column (fcmToken, partnerId) changes.
        .distinctUntilChanged()

    /**
     * Folds the auth session and the stored row into one summary, or null when neither
     * carries a name or an email — a state with nothing to show, where a "signed in as"
     * block would be worse than no block at all.
     */
    private fun summarize(user: SignedInAccount, row: UserEntity?): AccountSummary? {
        // Same gap `UserRepositoryImpl.ensureProfile` fills in: a real Google session whose
        // account record predates linking the provider has nothing at the top level.
        val providers = user.providers
        val email = ProfileIdentity.resolveEmail(
            topLevelEmail = user.email,
            storedRemoteEmail = null,
            storedLocalEmail = row?.email,
            providers = providers
        ).orEmpty()
        val name = ProfileIdentity.resolveName(
            displayName = user.displayName,
            storedRemoteName = null,
            storedLocalName = row?.name,
            email = email,
            providers = providers
        ).orEmpty()
        if (name.isBlank() && email.isBlank()) return null

        return AccountSummary(
            id = user.uid,
            name = name,
            email = email,
            photoUrl = ProfileIdentity.resolvePhotoUrl(
                authPhotoUrl = user.photoUrl,
                storedRemoteUrl = null,
                storedLocalUrl = row?.profilePhotoUrl,
                providers = providers
            )
        )
    }
}
