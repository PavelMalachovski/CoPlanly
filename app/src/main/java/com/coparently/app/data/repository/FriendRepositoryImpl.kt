package com.coparently.app.data.repository

import android.util.Log
import com.coparently.app.data.family.SelectedFamilySource
import com.coparently.app.data.remote.firebase.AcceptCalendarFriendResult
import com.coparently.app.data.remote.firebase.FirebaseAuthService
import com.coparently.app.data.remote.firebase.PairingException
import com.coparently.app.data.remote.firebase.PairingFunctions
import com.coparently.app.domain.friends.CalendarFriendGrant
import com.coparently.app.domain.friends.CalendarFriendPolicy
import com.coparently.app.domain.friends.FriendProfile
import com.coparently.app.domain.guests.GuestInvite
import com.coparently.app.domain.model.PairingError
import com.coparently.app.domain.pairing.InviteCodeGenerator
import com.coparently.app.domain.repository.FriendRepository
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Firestore-backed [FriendRepository].
 *
 * Writes the invitation document; the redemption that follows is a Cloud Function, for the same
 * reason pairing's and the guest's are — it must read the inviter's `users` document to prove
 * they are a paired parent, which the caller cannot read until the grant exists.
 *
 * Expiry is applied **on read** through [CalendarFriendPolicy] rather than trusted from storage,
 * so a lapsed grant disappears from the parents' list the moment it lapses, with no sweep in the
 * path. The security rule enforces the same instant server-side, so this is presentation, not the
 * gate.
 */
@Singleton
class FriendRepositoryImpl @Inject constructor(
    private val firestore: FirebaseFirestore,
    private val authService: FirebaseAuthService,
    private val pairingFunctions: PairingFunctions,
    private val selectedFamilySource: SelectedFamilySource
) : FriendRepository {

    override suspend fun inviteFriend(grantExpiresAtMillis: Long): Result<GuestInvite> {
        // Refused here as well as by the create rule and the callable, for the reason
        // `GuestRepositoryImpl` states: the rule stops a malformed document existing, the
        // callable stops it meaning anything, and this stops the user being shown a code that
        // was never going to work.
        if (grantExpiresAtMillis <= System.currentTimeMillis()) {
            return Result.failure(PairingException(PairingError.GrantEnded))
        }
        return runFriend {
            val user = authService.getCurrentUser()
                ?: throw PairingException(PairingError.Unknown("Not signed in"))
            val profile = firestore.collection(USERS).document(user.uid).get().await()
            val invite = GuestInvite(
                id = UUID.randomUUID().toString(),
                code = InviteCodeGenerator.generate(),
                // Empty: a friend is invited to the calendar, not to one child.
                childInfoId = "",
                inviteExpiresAtMillis = System.currentTimeMillis() + INVITE_TTL_MILLIS,
                grantExpiresAtMillis = grantExpiresAtMillis
            )
            firestore.collection(INVITATIONS).document(invite.id).set(
                mapOf(
                    "id" to invite.id,
                    "code" to invite.code,
                    "fromUserId" to user.uid,
                    "fromUserName" to (profile.getString("name") ?: user.email.orEmpty()),
                    "fromUserEmail" to user.email.orEmpty(),
                    // Empty, like the guest invite's: a parent generally does not know which
                    // account their child's grandmother signs in with.
                    "toEmail" to "",
                    "status" to STATUS_PENDING,
                    "createdAt" to System.currentTimeMillis(),
                    "expiresAt" to invite.inviteExpiresAtMillis,
                    "acceptedBy" to null,
                    "kind" to KIND_FRIEND,
                    "friendExpiresAt" to invite.grantExpiresAtMillis,
                    // **Which family the friend is being admitted to (M-6).** Recorded when the
                    // code is generated rather than when it is redeemed, because the two can be
                    // days apart and a parent with two families may well be looking at the other
                    // one by then — and a friend admitted to the wrong household is precisely
                    // the failure this item exists to end.
                    //
                    // The callable never trusts it: it checks the id against the inviter's live
                    // co-parents and falls back to the family they are showing. So a blank here
                    // (no family selected — an unpaired parent, whom the callable refuses
                    // anyway) costs nothing.
                    "familyId" to (selectedFamilySource.selected()?.familyId.orEmpty())
                )
            ).await()
            invite
        }
    }

    override suspend fun acceptFriendInvite(code: String): Result<AcceptCalendarFriendResult> {
        val normalized = code.trim().uppercase()
        if (!InviteCodeGenerator.isValid(normalized)) {
            return Result.failure(PairingException(PairingError.NotFound))
        }
        return pairingFunctions.acceptCalendarFriendInvitation(code = normalized)
    }

    override fun observeFamilyFriends(): Flow<List<CalendarFriendGrant>> {
        val myUid = authService.getCurrentUser()?.uid ?: return flowOf(emptyList())
        // Every grant naming this parent — the one list query the rule admits a parent — kept to
        // the family on screen. A parent in two families sees each family's friends under that
        // family (L-5 keys a grant per family), and a friend admitted by both appears in both.
        val grantsNamingMe = observeGrants(
            firestore.collection(CALENDAR_FRIENDS).whereArrayContains("familyParents", myUid)
        )
        return combine(grantsNamingMe, selectedFamilySource.observe(myUid)) { grants, family ->
            val familyId = family?.familyId
            if (familyId == null) {
                emptyList<CalendarFriendGrant>()
            } else {
                CalendarFriendPolicy.active(
                    grants.filter { it.familyId == familyId },
                    System.currentTimeMillis()
                )
            }
        }
    }

    override suspend fun revokeFriend(friendUid: String): Result<Unit> = runFriend {
        val myUid = authService.getCurrentUser()?.uid
            ?: throw PairingException(PairingError.Unknown("Not signed in"))
        // The family on screen, the one the list this revoke was chosen from is kept to. The
        // same friend's grant in another family is a different document and stays (L-5).
        val familyId = selectedFamilySource.observe(myUid).first()?.familyId
            ?: throw PairingException(PairingError.Unknown("No family on screen"))
        firestore.collection(CALENDAR_FRIENDS)
            .document(CalendarFriendGrant.documentId(familyId, friendUid))
            .delete()
            .await()
    }

    override fun observeMyGrants(): Flow<List<CalendarFriendGrant>> {
        val myUid = authService.getCurrentUser()?.uid ?: return flowOf(emptyList())
        return observeGrants(myGrantsQuery(myUid)).map { grants -> liveSoonestFirst(grants) }
    }

    override suspend fun myGrants(): List<CalendarFriendGrant> {
        val myUid = authService.getCurrentUser()?.uid ?: return emptyList()
        val documents = try {
            myGrantsQuery(myUid).get().await().documents
        } catch (e: CancellationException) {
            // Never swallowed: `runFriend` in this same file rethrows it for the same reason —
            // a cancelled coroutine that reports itself as a failed read is a lie about why.
            throw e
        } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
            Log.w(TAG, "Could not read this account's calendar-friend grants", e)
            return emptyList()
        }
        return liveSoonestFirst(documents.mapNotNull { FriendMappers.grantFrom(it.id, it.data) })
    }

    /**
     * The friend's own grants, one per family (L-5). Filtered on `friendUid`, the field the read
     * rule keys the friend's side on — an unfiltered read of the collection is refused outright.
     */
    private fun myGrantsQuery(myUid: String): Query =
        firestore.collection(CALENDAR_FRIENDS).whereEqualTo("friendUid", myUid)

    private fun liveSoonestFirst(grants: List<CalendarFriendGrant>): List<CalendarFriendGrant> =
        CalendarFriendPolicy.active(grants, System.currentTimeMillis()).sortedBy { it.expiresAtMillis }

    /**
     * A grant query as a flow of decoded grants. Not closed on error: a denied or dropped
     * listener must not take the screen's whole flow down, and an empty list is the honest
     * reading of "this device cannot see any grants right now".
     */
    private fun observeGrants(query: Query): Flow<List<CalendarFriendGrant>> = callbackFlow {
        val registration = query.addSnapshotListener { snapshot, error ->
            if (error != null) {
                trySend(emptyList())
                return@addSnapshotListener
            }
            trySend(
                snapshot?.documents.orEmpty().mapNotNull { doc ->
                    FriendMappers.grantFrom(doc.id, doc.data)
                }
            )
        }
        awaitClose { registration.remove() }
    }

    override fun observeMyProfile(): Flow<FriendProfile?> {
        val myUid = authService.getCurrentUser()?.uid ?: return flowOf(null)
        return observeFriendProfile(myUid)
    }

    override suspend fun saveMyProfile(profile: FriendProfile): Result<Unit> = runFriend {
        val user = authService.getCurrentUser()
            ?: throw PairingException(PairingError.Unknown("Not signed in"))
        // The Google account's own picture, when the profile carries none. The rule
        // `ProfileIdentity` applies to a parent's avatar, applied here: take the strongest
        // source that actually has a value, and never let it overwrite something already
        // stored — a friend who has set a picture of their own keeps it.
        val photoUrl = profile.photoUrl?.takeIf { it.isNotBlank() }
            ?: user.photoUrl?.toString()?.takeIf { it.isNotBlank() }
        // The document id is always this account's own uid: the rule refuses any other, and
        // writing one would be an attempt to author somebody else's profile.
        firestore.collection(FRIEND_PROFILES).document(user.uid)
            .set(FriendMappers.profileToMap(profile.copy(uid = user.uid, photoUrl = photoUrl)))
            .await()
    }

    override fun observeFriendProfile(friendUid: String): Flow<FriendProfile?> =
        observeDocument(FRIEND_PROFILES, friendUid)
            .map { data -> FriendMappers.profileFrom(friendUid, data) }

    /** One document as a flow, degrading a failed listener to null rather than closing. */
    private fun observeDocument(collection: String, id: String): Flow<Map<String, Any?>?> =
        callbackFlow {
            val registration = firestore.collection(collection).document(id)
                .addSnapshotListener { snapshot, error ->
                    trySend(if (error != null) null else snapshot?.data)
                }
            awaitClose { registration.remove() }
        }

    /**
     * Runs a Firestore block and normalizes any failure into a [PairingException], the shape
     * `GuestRepositoryImpl.runGuest` uses. Cancellation propagates rather than being reported as
     * a failed invitation.
     */
    private suspend fun <T> runFriend(block: suspend () -> T): Result<T> = try {
        Result.success(block())
    } catch (e: CancellationException) {
        throw e
    } catch (e: PairingException) {
        Result.failure(e)
    } catch (
        @Suppress("TooGenericExceptionCaught") e: Exception
    ) {
        Result.failure(PairingException(PairingError.Unknown(e.message)))
    }

    private companion object {
        const val TAG = "FriendRepository"
        const val USERS = "users"
        const val INVITATIONS = "invitations"
        const val CALENDAR_FRIENDS = "calendar_friends"
        const val FRIEND_PROFILES = "friend_profiles"
        const val STATUS_PENDING = "pending"

        /** Marks the document as a friend invitation; matches `FRIEND_INVITATION` server-side. */
        const val KIND_FRIEND = "friend"

        /**
         * How long the *offer* stays redeemable — a week, matching the guest invite's and for the
         * same reason: it is read out to somebody who will install the app that evening.
         *
         * Not how long the access lasts: that is `grantExpiresAtMillis`, chosen by the parent and
         * stamped verbatim at redemption rather than restarted.
         */
        const val INVITE_TTL_MILLIS = 7L * 24 * 60 * 60 * 1000
    }
}
