@file:OptIn(ExperimentalUuidApi::class)

package com.coparently.app.data.repository

import com.coparently.app.data.remote.firebase.DocumentPath
import com.coparently.app.data.remote.firebase.FirestoreClient
import com.coparently.app.data.remote.firebase.PairingException
import com.coparently.app.data.remote.firebase.PairingFunctions
import com.coparently.app.data.remote.firebase.SignedInUser
import com.coparently.app.domain.guests.AcceptGuestResult
import com.coparently.app.domain.guests.GuestInvite
import com.coparently.app.domain.model.PairingError
import com.coparently.app.domain.pairing.InviteCodeGenerator
import com.coparently.app.domain.repository.GuestRepository
import kotlinx.coroutines.CancellationException
import kotlin.time.Clock
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * Firestore-backed [GuestRepository].
 *
 * Writes the invitation document; the redemption that follows is a Cloud Function, for the
 * same reason pairing's is — it writes a child record the caller cannot yet read, let alone
 * write, since the `child_info` update rule requires the writer to already be in `sharedWith`.
 *
 * On [FirestoreClient] and [SignedInUser] since MON-24 phase 2 (Task 8d): the same read of the
 * sender's profile and the same invitation document as the Android SDK calls it made.
 */
class GuestRepositoryImpl(
    private val firestore: FirestoreClient,
    private val authService: SignedInUser,
    private val pairingFunctions: PairingFunctions
) : GuestRepository {

    override suspend fun inviteGuest(
        childInfoId: String,
        grantExpiresAtMillis: Long
    ): Result<GuestInvite> {
        if (childInfoId.isBlank()) {
            return Result.failure(PairingException(PairingError.Unknown("No child record")))
        }
        // Refused here as well as by the create rule and the callable. Three checks for one
        // condition looks redundant until you notice what each is protecting: the rule stops a
        // malformed document existing, the callable stops it meaning anything, and this stops
        // the user being shown a code that was never going to work.
        if (grantExpiresAtMillis <= nowMillis()) {
            return Result.failure(PairingException(PairingError.GrantEnded))
        }
        return runGuest {
            val uid = authService.getCurrentUid()
                ?: throw PairingException(PairingError.Unknown("Not signed in"))
            val email = authService.getCurrentEmail().orEmpty()
            val profile = firestore.get(DocumentPath(USERS, uid))
            val invite = GuestInvite(
                id = Uuid.random().toString(),
                code = InviteCodeGenerator.generate(),
                childInfoId = childInfoId,
                inviteExpiresAtMillis = nowMillis() + INVITE_TTL_MILLIS,
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
                    // Empty, always. A guest invitation is redeemed by whoever holds the code,
                    // the way the co-parent QR/link invite is; addressing one to an email
                    // would mean the parent has to know which account their child's
                    // grandmother signs in with, which they generally do not.
                    "toEmail" to "",
                    "status" to STATUS_PENDING,
                    "createdAt" to nowMillis(),
                    "expiresAt" to invite.inviteExpiresAtMillis,
                    "acceptedBy" to null,
                    "kind" to KIND_GUEST,
                    "childInfoId" to invite.childInfoId,
                    "guestExpiresAt" to invite.grantExpiresAtMillis
                )
            )
            invite
        }
    }

    override suspend fun acceptGuestInvite(code: String): Result<AcceptGuestResult> {
        val normalized = code.trim().uppercase()
        if (!InviteCodeGenerator.isValid(normalized)) {
            return Result.failure(PairingException(PairingError.NotFound))
        }
        return pairingFunctions.acceptGuestInvitation(code = normalized)
    }

    /**
     * Runs a Firestore block and normalizes any failure into a [PairingException], the same
     * shape `PairingRepositoryImpl.runPairing` uses. Cancellation propagates rather than being
     * reported as a failed invitation.
     */
    private suspend fun <T> runGuest(block: suspend () -> T): Result<T> = try {
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

    private fun nowMillis(): Long = Clock.System.now().toEpochMilliseconds()

    private companion object {
        const val USERS = "users"
        const val INVITATIONS = "invitations"
        const val STATUS_PENDING = "pending"

        /** Marks the document as a guest invitation; matches `GUEST_INVITATION` server-side. */
        const val KIND_GUEST = "guest"

        /**
         * How long the *offer* stays redeemable — a week, not the co-parent code's day.
         *
         * A co-parent invite is usually redeemed within minutes, by somebody sitting in the
         * same conversation. A guest invitation is more often read out over the phone to
         * somebody who will install the app that evening, or at the weekend.
         *
         * This is not how long the access lasts: that is `grantExpiresAtMillis`, chosen by the
         * parent, and it is stamped verbatim at redemption rather than restarted.
         */
        const val INVITE_TTL_MILLIS = 7L * 24 * 60 * 60 * 1000
    }
}
