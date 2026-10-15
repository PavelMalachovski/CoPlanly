package com.coparently.app.domain.repository

import com.coparently.app.domain.model.InvitePreview
import com.coparently.app.domain.model.PairingInvite
import com.coparently.app.domain.model.PairingState
import com.coparently.app.domain.model.PartnerSummary
import kotlinx.coroutines.flow.Flow

/**
 * Owns the co-parent link: whether it exists, how to offer one, and how to end it.
 *
 * Failures come back as `Result.failure(PairingException)` carrying a
 * `PairingError`, so callers map one typed value to one message.
 */
interface PairingRepository {

    /**
     * Emits the current pairing state and every subsequent change, driven by
     * Firestore snapshot listeners — the inviting phone learns that its
     * invitation was accepted without polling or a push. Recovers to
     * [PairingState.Loading] rather than terminating if a transient Firestore
     * error occurs (offline with no cache, a rules mismatch); it also emits
     * [PairingState.Loading] whenever there is no signed-in user, including
     * briefly on a cold start before Firebase Auth restores its session.
     */
    fun observePairingState(): Flow<PairingState>

    /**
     * [partnerUid]'s profile, every time their own `users/{uid}` document changes — a rename, a
     * new photo, a slot, a colour.
     *
     * For a co-parent [observePairingState] does not carry. That state names the co-parent the
     * server pins `partnerId` to, which `acceptPairingInvitation` keeps on the *first* one, so a
     * parent showing their second family needs this to name the co-parent actually on screen
     * (`presentation/common/CoParentOnScreen.kt`). Only a co-parent's document is readable
     * (`firestore.rules`' `isPartnerOf`); anybody else's reads as a profile with no fields.
     *
     * [PartnerSummary.pairedSinceMillis] is null in every summary: `users/{uid}.pairedAt` is when
     * this account's *latest* link was made, which says nothing about this one.
     */
    fun observeCoParentProfile(partnerUid: String): Flow<PartnerSummary>

    /**
     * Returns this user's outstanding code/QR/link invite, creating one only
     * when there is no pending, unexpired one already. Reuse matters: a code
     * the user has already sent by message must not silently stop working.
     */
    suspend fun createOrReuseInviteCode(): Result<PairingInvite>

    /**
     * Withdraws this user's active code/QR/link invite (the one
     * [createOrReuseInviteCode] returns) so that code stops working.
     */
    suspend fun revokeActiveInvite(): Result<Unit>

    /**
     * Names the person behind [code] without redeeming it, so the screen can ask "Link with
     * Alice?" rather than "Link with this code?" before anything is linked. Fails with the
     * `PairingError` the redeem would fail with; writes nothing.
     */
    suspend fun previewInvite(code: String): Result<InvitePreview>

    /**
     * Redeems an invitation by its short [code].
     *
     * Reaches the same `acceptPairingInvitation` callable as [acceptIncoming] — a code/QR/
     * deep-link redemption and an addressed-invitation accept are two entry points to the
     * same server-side pairing — so it can move this device's parent slot the same way. See
     * [acceptIncoming]'s return doc for what the caller does with it.
     */
    suspend fun redeem(code: String): Result<String?>

    /**
     * Accepts an invitation addressed to this user by document id.
     *
     * @return the parent slot ("mom"/"dad") this device was just assigned, or null if the
     *   backend did not report one (see `PairingFunctions.AcceptInvitationResult.role` for
     *   why that is tolerated rather than treated as a failure). Accepting may move this
     *   device from the slot it held while unpaired to the other one — the caller compares
     *   this against the slot it read locally beforehand to decide whether records created
     *   before pairing need to be re-stamped (see `ParentSlotMigrator`).
     */
    suspend fun acceptIncoming(invitationId: String): Result<String?>

    /** Declines an invitation addressed to this user. */
    suspend fun rejectIncoming(invitationId: String): Result<Unit>

    /** Ends the co-parent link for both parents. Shared data is kept. */
    suspend fun unpair(): Result<Unit>
}
