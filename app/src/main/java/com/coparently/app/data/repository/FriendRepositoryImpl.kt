package com.coparently.app.data.repository

import com.coparently.app.data.family.SelectedFamilySource
import com.coparently.app.data.platform.logger
import com.coparently.app.data.remote.firebase.DocumentPath
import com.coparently.app.data.remote.firebase.DocumentQuery
import com.coparently.app.data.remote.firebase.FirestoreClient
import com.coparently.app.data.remote.firebase.FirestoreErrorCode
import com.coparently.app.data.remote.firebase.PairingException
import com.coparently.app.data.remote.firebase.PairingFunctions
import com.coparently.app.data.remote.firebase.SignedInUser
import com.coparently.app.data.remote.firebase.StoredDocument
import com.coparently.app.domain.family.FamilyKey
import com.coparently.app.domain.friends.AcceptCalendarFriendResult
import com.coparently.app.domain.friends.CalendarFriendGrant
import com.coparently.app.domain.friends.CalendarFriendPolicy
import com.coparently.app.domain.friends.FriendCalendarRead
import com.coparently.app.domain.friends.FriendProfile
import com.coparently.app.domain.guests.GuestInvite
import com.coparently.app.domain.model.PairingError
import com.coparently.app.domain.pairing.InviteCodeGenerator
import com.coparently.app.domain.repository.FriendRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.datetime.LocalDate
import java.util.UUID

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
 *
 * On [FirestoreClient] and [SignedInUser] since MON-24 phase 2 (Task 8d): the same documents,
 * queries and listeners as the Android SDK calls it made. It stays in the app while
 * [FriendRepository] and `ProfessionalEvents` take a `java.time.LocalDate`.
 */
class FriendRepositoryImpl(
    private val firestore: FirestoreClient,
    private val authService: SignedInUser,
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
            val uid = authService.getCurrentUid()
                ?: throw PairingException(PairingError.Unknown("Not signed in"))
            val email = authService.getCurrentEmail().orEmpty()
            val profile = firestore.get(DocumentPath(USERS, uid))
            val invite = GuestInvite(
                id = UUID.randomUUID().toString(),
                code = InviteCodeGenerator.generate(),
                // Empty: a friend is invited to the calendar, not to one child.
                childInfoId = "",
                inviteExpiresAtMillis = System.currentTimeMillis() + INVITE_TTL_MILLIS,
                grantExpiresAtMillis = grantExpiresAtMillis
            )
            firestore.set(
                DocumentPath(INVITATIONS, invite.id),
                mapOf(
                    "id" to invite.id,
                    "code" to invite.code,
                    "fromUserId" to uid,
                    "fromUserName" to (profile.string("name") ?: email),
                    "fromUserEmail" to email,
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
            )
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
        val myUid = authService.getCurrentUid() ?: return flowOf(emptyList())
        // Every grant naming this parent — the one list query the rule admits a parent — kept to
        // the family on screen. A parent in two families sees each family's friends under that
        // family (L-5 keys a grant per family), and a friend admitted by both appears in both.
        val grantsNamingMe = observeGrants(
            DocumentQuery(CALENDAR_FRIENDS).whereArrayContains("familyParents", myUid)
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
        val myUid = authService.getCurrentUid()
            ?: throw PairingException(PairingError.Unknown("Not signed in"))
        // The family on screen, the one the list this revoke was chosen from is kept to. The
        // same friend's grant in another family is a different document and stays (L-5).
        val familyId = selectedFamilySource.observe(myUid).first()?.familyId
            ?: throw PairingException(PairingError.Unknown("No family on screen"))
        firestore.delete(DocumentPath(CALENDAR_FRIENDS, CalendarFriendGrant.documentId(familyId, friendUid)))
    }

    override fun observeMyGrants(): Flow<List<CalendarFriendGrant>> {
        val myUid = authService.getCurrentUid() ?: return flowOf(emptyList())
        return observeGrants(myGrantsQuery(myUid)).map { grants -> liveSoonestFirst(grants) }
    }

    override suspend fun myGrants(): List<CalendarFriendGrant> {
        val myUid = authService.getCurrentUid() ?: return emptyList()
        val documents = try {
            firestore.get(myGrantsQuery(myUid))
        } catch (e: CancellationException) {
            // Never swallowed: `runFriend` in this same file rethrows it for the same reason —
            // a cancelled coroutine that reports itself as a failed read is a lie about why.
            throw e
        } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
            logger.w(TAG, "Could not read this account's calendar-friend grants", e)
            return emptyList()
        }
        return liveSoonestFirst(documents.mapNotNull { FriendMappers.grantFrom(it.id, it.data) })
    }

    /**
     * The friend's own grants, one per family (L-5). Filtered on `friendUid`, the field the read
     * rule keys the friend's side on — an unfiltered read of the collection is refused outright.
     */
    private fun myGrantsQuery(myUid: String): DocumentQuery =
        DocumentQuery(CALENDAR_FRIENDS).whereEqualTo("friendUid", myUid)

    private fun liveSoonestFirst(grants: List<CalendarFriendGrant>): List<CalendarFriendGrant> =
        CalendarFriendPolicy.active(grants, System.currentTimeMillis()).sortedBy { it.expiresAtMillis }

    /**
     * A grant query as a flow of decoded grants. Not closed on error: a denied or dropped
     * listener must not take the screen's whole flow down, and an empty list is the honest
     * reading of "this device cannot see any grants right now". The failed listener says nothing
     * more, and the flow stays open until its collector leaves, as the SDK listener it replaced
     * did.
     */
    private fun observeGrants(query: DocumentQuery): Flow<List<CalendarFriendGrant>> =
        firestore.observe(query)
            .map { documents -> documents.mapNotNull { doc -> FriendMappers.grantFrom(doc.id, doc.data) } }
            .catch {
                emit(emptyList())
                awaitCancellation()
            }

    override fun observeMyProfile(): Flow<FriendProfile?> {
        val myUid = authService.getCurrentUid() ?: return flowOf(null)
        return observeFriendProfile(myUid)
    }

    override suspend fun saveMyProfile(profile: FriendProfile): Result<Unit> = runFriend {
        val uid = authService.getCurrentUid()
            ?: throw PairingException(PairingError.Unknown("Not signed in"))
        // The Google account's own picture, when the profile carries none. The rule
        // `ProfileIdentity` applies to a parent's avatar, applied here: take the strongest
        // source that actually has a value, and never let it overwrite something already
        // stored — a friend who has set a picture of their own keeps it.
        val photoUrl = profile.photoUrl?.takeIf { it.isNotBlank() }
            ?: authService.getCurrentPhotoUrl()?.takeIf { it.isNotBlank() }
        // The document id is always this account's own uid: the rule refuses any other, and
        // writing one would be an attempt to author somebody else's profile.
        firestore.set(
            DocumentPath(FRIEND_PROFILES, uid),
            FriendMappers.profileToMap(profile.copy(uid = uid, photoUrl = photoUrl))
        )
    }

    override fun observeFriendProfile(friendUid: String): Flow<FriendProfile?> =
        observeDocument(FRIEND_PROFILES, friendUid)
            .map { data -> FriendMappers.profileFrom(friendUid, data) }

    override fun observeFamilyEvents(
        familyId: String,
        from: LocalDate,
        to: LocalDate
    ): Flow<FriendCalendarRead> {
        val parents = FamilyKey.membersOf(familyId) ?: return flowOf(FriendCalendarRead.Failed)
        // The query `firestore-tests/rules/friend-calendar.test.js` pins: `familyId` is what the
        // rule builds the grant's path from, and the creator filter is what lets Firestore prove
        // the creator is one of that family's parents from the query alone (M-6). The by-creator
        // query alone is refused outright.
        val query = DocumentQuery(EVENTS)
            .whereEqualTo(FIELD_FAMILY_ID, familyId)
            .whereIn(FIELD_CREATOR, parents.toList())
        return firestore.observe(query)
            .map<List<StoredDocument>, FriendCalendarRead> { snapshot ->
                val documents = snapshot.map { it.data.orEmpty() }
                FriendCalendarRead.Events(ProfessionalEvents.from(documents, from, to))
            }
            // A failed read ends the flow with what it means, as the listener closed itself.
            .catch { error ->
                logger.w(TAG, "The family's events could not be read", error)
                emit(friendCalendarReadFor(firestore.errorCodeOf(error)))
            }
    }

    /**
     * One document as a flow, degrading a failed listener to null rather than closing: the flow
     * stays open until its collector leaves, as the SDK listener it replaced did.
     */
    private fun observeDocument(collection: String, id: String): Flow<Map<String, Any?>?> =
        firestore.observe(DocumentPath(collection, id))
            .map<StoredDocument, Map<String, Any?>?> { it.data }
            .catch {
                emit(null)
                awaitCancellation()
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
        const val EVENTS = "events"
        const val FIELD_FAMILY_ID = "familyId"
        const val FIELD_CREATOR = "createdByFirebaseUid"
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

/**
 * What a failed read of the family's events means to a calendar friend (phase 2), from the
 * failure's Firestore status ([FirestoreClient.errorCodeOf]; null for a failure that is not
 * Firestore's). The rules refusing her is the one failure a retry cannot fix — her grant was
 * revoked or it lapsed — so it is told apart from every other, which she can retry.
 */
internal fun friendCalendarReadFor(code: FirestoreErrorCode?): FriendCalendarRead =
    if (code == FirestoreErrorCode.PERMISSION_DENIED) {
        FriendCalendarRead.AccessEnded
    } else {
        FriendCalendarRead.Failed
    }
