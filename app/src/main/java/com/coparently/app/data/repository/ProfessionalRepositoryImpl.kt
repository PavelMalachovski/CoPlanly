package com.coparently.app.data.repository

import android.util.Log
import com.coparently.app.data.family.SelectedFamilySource
import com.coparently.app.data.remote.firebase.AcceptProfessionalResult
import com.coparently.app.data.remote.firebase.FirebaseAuthService
import com.coparently.app.data.remote.firebase.FirestoreCustodyDataSource
import com.coparently.app.data.remote.firebase.FirestoreParentingPlanDataSource
import com.coparently.app.data.remote.firebase.PairingException
import com.coparently.app.data.remote.firebase.PairingFunctions
import com.coparently.app.domain.custody.SharedCustody
import com.coparently.app.domain.guests.GuestInvite
import com.coparently.app.domain.model.Event
import com.coparently.app.domain.model.PairingError
import com.coparently.app.domain.pairing.InviteCodeGenerator
import com.coparently.app.domain.parentingplan.ParentingPlanEntry
import com.coparently.app.domain.professionals.ProfessionalGrant
import com.coparently.app.domain.professionals.ProfessionalRole
import com.coparently.app.domain.repository.ProfessionalRepository
import com.google.firebase.firestore.FieldPath
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.tasks.await
import java.time.LocalDate
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Firestore-backed [ProfessionalRepository] (MON-18).
 *
 * The invitation is written here; the redemption is `acceptProfessionalInvitation`, which alone
 * may create a grant. The grant is then observed live on both sides, like the friend list — no
 * Room table, so no schema change and no copy of a family on a professional's phone.
 *
 * Each listener degrades a failure to an empty answer rather than closing its flow: a denial is
 * what an unconsented or expired grant *should* produce on the professional's side, and a screen
 * must show "nothing to read" then, not crash or spin.
 */
@Singleton
class ProfessionalRepositoryImpl @Inject constructor(
    private val firestore: FirebaseFirestore,
    private val authService: FirebaseAuthService,
    private val pairingFunctions: PairingFunctions,
    private val selectedFamilySource: SelectedFamilySource,
    private val custodyDataSource: FirestoreCustodyDataSource,
    private val planDataSource: FirestoreParentingPlanDataSource
) : ProfessionalRepository {

    override fun currentUid(): String? = authService.getCurrentUser()?.uid

    override suspend fun invite(role: ProfessionalRole, grantExpiresAtMillis: Long): Result<GuestInvite> {
        if (grantExpiresAtMillis <= System.currentTimeMillis()) {
            return Result.failure(PairingException(PairingError.GrantEnded))
        }
        return runProfessional {
            val user = authService.getCurrentUser()
                ?: throw PairingException(PairingError.Unknown("Not signed in"))
            // The family on screen, and no fallback: a professional is admitted to one named
            // family, and the rule refuses an invitation that names none.
            val familyId = selectedFamilySource.selected()?.familyId?.takeIf { it.isNotBlank() }
                ?: throw PairingException(PairingError.Unknown("No family selected"))
            val profile = firestore.collection(USERS).document(user.uid).get().await()
            val invite = GuestInvite(
                id = UUID.randomUUID().toString(),
                code = InviteCodeGenerator.generate(),
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
                    "toEmail" to "",
                    "status" to STATUS_PENDING,
                    "createdAt" to System.currentTimeMillis(),
                    "expiresAt" to invite.inviteExpiresAtMillis,
                    "acceptedBy" to null,
                    "kind" to KIND_PROFESSIONAL,
                    "professionalRole" to role.wire,
                    "professionalExpiresAt" to invite.grantExpiresAtMillis,
                    "familyId" to familyId
                )
            ).await()
            invite
        }
    }

    override suspend fun acceptInvite(code: String): Result<AcceptProfessionalResult> {
        val normalized = code.trim().uppercase()
        if (!InviteCodeGenerator.isValid(normalized)) {
            return Result.failure(PairingException(PairingError.NotFound))
        }
        return pairingFunctions.acceptProfessionalInvitation(code = normalized)
    }

    override fun observeFamilyGrants(): Flow<List<ProfessionalGrant>> {
        val myUid = currentUid() ?: return flowOf(emptyList())
        return observeGrants("familyParents", myUid, arrayContains = true)
    }

    override fun observeMyGrants(): Flow<List<ProfessionalGrant>> {
        val myUid = currentUid() ?: return flowOf(emptyList())
        return observeGrants("proUid", myUid, arrayContains = false)
    }

    override suspend fun consent(grantId: String): Result<Unit> = runProfessional {
        val myUid = currentUid() ?: throw PairingException(PairingError.Unknown("Not signed in"))
        // A field path rather than a dotted string: a uid is alphanumeric today, and a path built
        // from segments stays one key whatever it contains.
        firestore.collection(GRANTS).document(grantId)
            .update(FieldPath.of(FIELD_CONSENTS, myUid), System.currentTimeMillis())
            .await()
    }

    override suspend fun revoke(grantId: String): Result<Unit> = runProfessional {
        firestore.collection(GRANTS).document(grantId).delete().await()
    }

    override fun observeFamilyEvents(
        grant: ProfessionalGrant,
        from: LocalDate,
        to: LocalDate
    ): Flow<List<Event>> = callbackFlow {
        // Both filters, and in this shape: `familyId` is what the rule keys the grant on, and the
        // creator filter is what lets Firestore prove `ownerUid in familyParents` from the query's
        // structure alone (item 12). Without either the query is refused outright.
        val registration = firestore.collection(EVENTS)
            .whereEqualTo(FIELD_FAMILY_ID, grant.familyId)
            .whereIn(FIELD_CREATOR, grant.familyParents)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.w(TAG, "Professional events listener failed", error)
                    trySend(emptyList())
                    return@addSnapshotListener
                }
                val documents = snapshot?.documents.orEmpty().mapNotNull { it.data }
                trySend(ProfessionalEvents.from(documents, from, to))
            }
        awaitClose { registration.remove() }
    }

    override fun observeCustody(grant: ProfessionalGrant): Flow<SharedCustody?> =
        custodyDataSource.observeCustody(grant.familyId)
            .catch { e ->
                Log.w(TAG, "Professional custody listener failed", e)
                emit(null)
            }

    override fun observePlan(grant: ProfessionalGrant): Flow<Map<String, ParentingPlanEntry>> =
        planDataSource.observePlan(grant.familyId)
            .catch { e ->
                Log.w(TAG, "Professional plan listener failed", e)
                emit(emptyMap())
            }

    private fun observeGrants(
        field: String,
        uid: String,
        arrayContains: Boolean
    ): Flow<List<ProfessionalGrant>> = callbackFlow {
        val collection = firestore.collection(GRANTS)
        val query = if (arrayContains) {
            collection.whereArrayContains(field, uid)
        } else {
            collection.whereEqualTo(field, uid)
        }
        val registration = query.addSnapshotListener { snapshot, error ->
            if (error != null) {
                Log.w(TAG, "Professional grants listener failed", error)
                trySend(emptyList())
                return@addSnapshotListener
            }
            trySend(
                snapshot?.documents.orEmpty()
                    .mapNotNull { ProfessionalMappers.grantFrom(it.id, it.data) }
                    .sortedBy { it.expiresAtMillis }
            )
        }
        awaitClose { registration.remove() }
    }

    /** Normalizes a failure into a [PairingException]; cancellation propagates. */
    private suspend fun <T> runProfessional(block: suspend () -> T): Result<T> = try {
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
        const val TAG = "ProfessionalRepository"
        const val USERS = "users"
        const val INVITATIONS = "invitations"
        const val GRANTS = "professional_grants"
        const val EVENTS = "events"
        const val FIELD_CONSENTS = "consents"
        const val FIELD_FAMILY_ID = "familyId"
        const val FIELD_CREATOR = "createdByFirebaseUid"
        const val STATUS_PENDING = "pending"

        /** Marks the document as a professional invitation; `PROFESSIONAL_INVITATION` server-side. */
        const val KIND_PROFESSIONAL = "professional"

        /** How long the *offer* stays redeemable — a week, like the friend and guest codes. */
        const val INVITE_TTL_MILLIS = 7L * 24 * 60 * 60 * 1000
    }
}
