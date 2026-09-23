package com.coparently.app.domain.repository

import com.coparently.app.data.remote.firebase.AcceptProfessionalResult
import com.coparently.app.domain.custody.SharedCustody
import com.coparently.app.domain.guests.GuestInvite
import com.coparently.app.domain.model.Event
import com.coparently.app.domain.parentingplan.ParentingPlanEntry
import com.coparently.app.domain.professionals.ProfessionalGrant
import com.coparently.app.domain.professionals.ProfessionalRole
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

/**
 * Professional access to one family (MON-18): a mediator, lawyer, guardian ad litem or therapist
 * reading the calendar, the parenting plan and the custody schedule — never chat, money or child
 * records — for a bounded time, once **both** parents have consented.
 *
 * Separate from [FriendRepository] for the reason the callables are separate: a friend and a
 * professional are admitted by different rules, and the code that admits one must not be one
 * edit away from admitting the other. Failures come back as `Result.failure` carrying a
 * `PairingException`, the shape the siblings use.
 *
 * Everything here is Firestore, observed live, and nothing is written to Room: the professional's
 * phone holds no copy of somebody else's family, and a parent's list is a view of the server's
 * grants, not a record this device keeps.
 */
interface ProfessionalRepository {

    /** The signed-in uid, or null — what a screen compares a grant's consents against. */
    fun currentUid(): String?

    /**
     * Offers [role] access to the family on screen, ending at [grantExpiresAtMillis], to whoever
     * redeems the returned code. Making the offer is this parent's consent; the co-parent's is
     * asked for once the code is redeemed.
     */
    suspend fun invite(role: ProfessionalRole, grantExpiresAtMillis: Long): Result<GuestInvite>

    /** Redeems a professional invitation by its short [code]. */
    suspend fun acceptInvite(code: String): Result<AcceptProfessionalResult>

    /**
     * Every professional grant over a family this parent is in — active, waiting or expired.
     * Expired ones stay listed until the nightly sweep removes them, so a parent sees that access
     * ended rather than a row silently vanishing.
     */
    fun observeFamilyGrants(): Flow<List<ProfessionalGrant>>

    /** Adds this parent's own consent to [grantId]. The rule refuses any other key. */
    suspend fun consent(grantId: String): Result<Unit>

    /** Ends [grantId]. Either parent may, alone. */
    suspend fun revoke(grantId: String): Result<Unit>

    /** The grants this account holds as a professional. */
    fun observeMyGrants(): Flow<List<ProfessionalGrant>>

    /**
     * The family's shared events overlapping [from]..[to], recurring ones expanded — what an
     * active [grant] may read. Private events never reach Firestore (CLAUDE.md item 3), and
     * tombstoned ones are dropped here.
     */
    fun observeFamilyEvents(grant: ProfessionalGrant, from: LocalDate, to: LocalDate): Flow<List<Event>>

    /** The family's custody schedule, or null while none is shared. */
    fun observeCustody(grant: ProfessionalGrant): Flow<SharedCustody?>

    /** Both halves of the family's parenting plan, keyed by the parent who wrote each. */
    fun observePlan(grant: ProfessionalGrant): Flow<Map<String, ParentingPlanEntry>>
}
