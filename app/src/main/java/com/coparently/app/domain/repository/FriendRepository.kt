package com.coparently.app.domain.repository

import com.coparently.app.data.remote.firebase.AcceptCalendarFriendResult
import com.coparently.app.domain.friends.CalendarFriendGrant
import com.coparently.app.domain.friends.FriendProfile
import com.coparently.app.domain.guests.GuestInvite
import kotlinx.coroutines.flow.Flow

/**
 * Letting a trusted third person read the family's calendar, and their own profile.
 *
 * Separate from [PairingRepository] and [GuestRepository] for the reason the three callables are
 * separate: a friend, a guest and a co-parent are different things, and the code that admits one
 * must not be one edit away from admitting another. Failures come back as `Result.failure`
 * carrying a `PairingException`, the shape both siblings use.
 */
interface FriendRepository {

    /**
     * Offers calendar access to whoever redeems the returned code, ending at
     * [grantExpiresAtMillis].
     *
     * Mints a **fresh** invitation every time, like [GuestRepository.inviteGuest] and unlike the
     * co-parent code: a friend code identifies one *person* being let in, and a code already read
     * out to one grandparent must not silently become another's.
     *
     * Reuses [GuestInvite] as the carrier — the two invitations differ only in what they open,
     * and the `childInfoId` is empty for a friend, who is invited to the calendar rather than to
     * one child.
     */
    suspend fun inviteFriend(grantExpiresAtMillis: Long): Result<GuestInvite>

    /** Redeems a friend invitation by its short [code]. */
    suspend fun acceptFriendInvite(code: String): Result<AcceptCalendarFriendResult>

    /**
     * The live calendar-friend grants on the family on screen, for the parents' "who can see
     * this" list.
     *
     * Read through the grants naming this signed-in parent (the query the rule keys on) and kept
     * to the family the switcher shows, so a parent in two families sees each family's friends
     * under that family, and never another family's; expiry is applied by
     * [com.coparently.app.domain.friends.CalendarFriendPolicy] on read rather than trusted from
     * storage, so a lapsed grant disappears without waiting for a sweep.
     */
    fun observeFamilyFriends(): Flow<List<CalendarFriendGrant>>

    /**
     * Ends [friendUid]'s access to the family on screen — the grant
     * `calendar_friends/{familyId}__{friendUid}`. Either parent may revoke. A grant the same
     * friend holds in another family is not touched (L-5).
     */
    suspend fun revokeFriend(friendUid: String): Result<Unit>

    /**
     * This account's own grants, when the signed-in user is a friend rather than a parent — one
     * per family that admitted them (L-5), live ones only, soonest-ending first. Empty while they
     * are not a friend of anybody, or once every grant has lapsed.
     */
    fun observeMyGrants(): Flow<List<CalendarFriendGrant>>

    /**
     * This account's own live grants, read once.
     *
     * The save path's accessor, and it exists for the reason CLAUDE.md's invariant 17 gives:
     * `FriendViewModel.myGrants` is a `WhileSubscribed` StateFlow, and `FriendProfileScreen` is
     * its own route that never collects it — so its `.value` was the initial empty value for
     * every save that ViewModel instance ever made, and the profile went out with an empty
     * `familyParents`, which is the gate the parents read it through.
     *
     * @return the grants, empty when this account is not a calendar friend or every one lapsed.
     */
    suspend fun myGrants(): List<CalendarFriendGrant>

    /** The friend's own profile, or null before they have written one. */
    fun observeMyProfile(): Flow<FriendProfile?>

    /** Writes the signed-in friend's own profile. Never another account's — the rule refuses it. */
    suspend fun saveMyProfile(profile: FriendProfile): Result<Unit>

    /** A friend's profile as the two parents read it, or null when there is none. */
    fun observeFriendProfile(friendUid: String): Flow<FriendProfile?>
}
