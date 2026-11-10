@file:OptIn(ExperimentalUuidApi::class)

package com.coparently.app.data.repository

import com.coparently.app.data.family.SelectedFamilySource
import com.coparently.app.data.platform.logger
import com.coparently.app.data.remote.firebase.DocumentPath
import com.coparently.app.data.remote.firebase.DocumentQuery
import com.coparently.app.data.remote.firebase.FieldPath
import com.coparently.app.data.remote.firebase.FirestoreClient
import com.coparently.app.data.remote.firebase.FirestoreCustodyDataSource
import com.coparently.app.data.remote.firebase.FirestoreParentingPlanDataSource
import com.coparently.app.data.remote.firebase.PairingException
import com.coparently.app.data.remote.firebase.PairingFunctions
import com.coparently.app.data.remote.firebase.SignedInUser
import com.coparently.app.domain.custody.SharedCustody
import com.coparently.app.domain.guests.GuestInvite
import com.coparently.app.domain.model.Event
import com.coparently.app.domain.model.PairingError
import com.coparently.app.domain.pairing.InviteCodeGenerator
import com.coparently.app.domain.parentingplan.ParentingPlanEntry
import com.coparently.app.domain.professionals.AcceptProfessionalResult
import com.coparently.app.domain.professionals.ProfessionalGrant
import com.coparently.app.domain.professionals.ProfessionalRole
import com.coparently.app.domain.repository.ProfessionalRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.datetime.LocalDate
import kotlin.time.Clock
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

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
 *
 * On [FirestoreClient] and [SignedInUser] since MON-24 phase 2 (Task 8d): the same documents,
 * queries and listeners as the Android SDK calls it made. In `:shared-data` since Task 8e, when
 * [ProfessionalRepository] and [ProfessionalEvents] moved to kotlinx-datetime's `LocalDate`; its ids are
 * `Uuid.random()`'s text and its clock `Clock.System`, as [GuestRepositoryImpl]'s.
 */
class ProfessionalRepositoryImpl(
    private val firestore: FirestoreClient,
    private val authService: SignedInUser,
    private val pairingFunctions: PairingFunctions,
    private val selectedFamilySource: SelectedFamilySource,
    private val custodyDataSource: FirestoreCustodyDataSource,
    private val planDataSource: FirestoreParentingPlanDataSource
) : ProfessionalRepository {

    override fun currentUid(): String? = authService.getCurrentUid()

    override suspend fun invite(role: ProfessionalRole, grantExpiresAtMillis: Long): Result<GuestInvite> {
        if (grantExpiresAtMillis <= nowMillis()) {
            return Result.failure(PairingException(PairingError.GrantEnded))
        }
        return runProfessional {
            val uid = authService.getCurrentUid()
                ?: throw PairingException(PairingError.Unknown("Not signed in"))
            val email = authService.getCurrentEmail().orEmpty()
            // The family on screen, and no fallback: a professional is admitted to one named
            // family, and the rule refuses an invitation that names none.
            val familyId = selectedFamilySource.selected()?.familyId?.takeIf { it.isNotBlank() }
                ?: throw PairingException(PairingError.Unknown("No family selected"))
            val profile = firestore.get(DocumentPath(USERS, uid))
            val invite = GuestInvite(
                id = Uuid.random().toString(),
                code = InviteCodeGenerator.generate(),
                childInfoId = "",
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
                    "toEmail" to "",
                    "status" to STATUS_PENDING,
                    "createdAt" to nowMillis(),
                    "expiresAt" to invite.inviteExpiresAtMillis,
                    "acceptedBy" to null,
                    "kind" to KIND_PROFESSIONAL,
                    "professionalRole" to role.wire,
                    "professionalExpiresAt" to invite.grantExpiresAtMillis,
                    "familyId" to familyId
                )
            )
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

    override fun observeFamilyGrants(familyId: String?): Flow<List<ProfessionalGrant>> {
        val myUid = currentUid() ?: return flowOf(emptyList())
        if (familyId != null) return observeGrants("familyParents", myUid, arrayContains = true, familyId = familyId)
        // Every grant naming this parent, the one list query the rule admits a parent, kept to the
        // family on screen as the family friends are: a parent in two families sees each family's
        // professionals under that family, and consents to or revokes only what that family's
        // screen shows. It listed both families' mediators and lawyers on either screen.
        return combine(
            observeGrants("familyParents", myUid, arrayContains = true),
            selectedFamilySource.observe(myUid)
        ) { grants, family ->
            val familyId = family?.familyId
            if (familyId == null) emptyList() else grants.filter { it.familyId == familyId }
        }
    }

    override fun observeMyGrants(): Flow<List<ProfessionalGrant>> {
        val myUid = currentUid() ?: return flowOf(emptyList())
        return observeGrants("proUid", myUid, arrayContains = false)
    }

    override suspend fun consent(grantId: String): Result<Unit> = runProfessional {
        val myUid = currentUid() ?: throw PairingException(PairingError.Unknown("Not signed in"))
        // A field path rather than a dotted string: a uid is alphanumeric today, and a path built
        // from segments stays one key whatever it contains.
        firestore.update(DocumentPath(GRANTS, grantId), FieldPath(FIELD_CONSENTS, myUid), nowMillis())
    }

    override suspend fun revoke(grantId: String): Result<Unit> = runProfessional {
        firestore.delete(DocumentPath(GRANTS, grantId))
    }

    override fun observeFamilyEvents(
        grant: ProfessionalGrant,
        from: LocalDate,
        to: LocalDate
    ): Flow<List<Event>> {
        // Both filters, and in this shape: `familyId` is what the rule keys the grant on, and the
        // creator filter is what lets Firestore prove `ownerUid in familyParents` from the query's
        // structure alone (item 12). Without either the query is refused outright.
        val query = DocumentQuery(EVENTS)
            .whereEqualTo(FIELD_FAMILY_ID, grant.familyId)
            .whereIn(FIELD_CREATOR, grant.familyParents)
        return firestore.observe(query)
            .map { snapshot -> ProfessionalEvents.from(snapshot.mapNotNull { it.data }, from, to) }
            .catch { error ->
                logger.w(TAG, "Professional events listener failed", error)
                emit(emptyList())
                // Not closed, as the SDK listener it replaced was not: nothing more arrives.
                awaitCancellation()
            }
    }

    override fun observeCustody(grant: ProfessionalGrant): Flow<SharedCustody?> =
        custodyDataSource.observeCustody(grant.familyId)
            .catch { e ->
                logger.w(TAG, "Professional custody listener failed", e)
                emit(null)
            }

    override fun observePlan(grant: ProfessionalGrant): Flow<Map<String, ParentingPlanEntry>> =
        planDataSource.observePlan(grant.familyId)
            .catch { e ->
                logger.w(TAG, "Professional plan listener failed", e)
                emit(emptyMap())
            }

    private fun observeGrants(
        field: String,
        uid: String,
        arrayContains: Boolean,
        familyId: String? = null
    ): Flow<List<ProfessionalGrant>> {
        val collection = DocumentQuery(GRANTS)
        val byPerson = if (arrayContains) {
            collection.whereArrayContains(field, uid)
        } else {
            collection.whereEqualTo(field, uid)
        }
        val query = familyId?.let { byPerson.whereEqualTo(FIELD_FAMILY_ID, it) } ?: byPerson
        return firestore.observe(query)
            .map { documents ->
                documents
                    .mapNotNull { ProfessionalMappers.grantFrom(it.id, it.data) }
                    .sortedBy { it.expiresAtMillis }
            }
            .catch { error ->
                logger.w(TAG, "Professional grants listener failed", error)
                emit(emptyList())
                // Not closed, as the SDK listener it replaced was not: nothing more arrives.
                awaitCancellation()
            }
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

    private fun nowMillis(): Long = Clock.System.now().toEpochMilliseconds()

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
