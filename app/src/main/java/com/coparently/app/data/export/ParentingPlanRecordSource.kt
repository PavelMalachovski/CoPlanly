package com.coparently.app.data.export

import android.util.Log
import com.coparently.app.data.repository.ParentingPlanRepository
import com.coparently.app.domain.export.PlanSource
import com.coparently.app.domain.family.FamilyKey
import kotlinx.coroutines.CancellationException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Reads the parenting plan for a communication record (MON-5 in MON-3's export).
 *
 * The same rule as [CommunicationRecordSource]: the server first, this phone second, and the record
 * says which it got. The plan is read from `parenting_plans/{familyId}` with `Source.SERVER`; when
 * that fails the record prints this phone's copy and says so on its face, rather than leaving the
 * section out — a plan silently missing from a document handed to a mediator reads as "there is no
 * plan".
 *
 * Separate from [CommunicationRecordSource] because the plan is optional in an export and is not
 * bound to its period; the export flow asks for it only when the parent ticked it.
 */
@Singleton
class ParentingPlanRecordSource @Inject constructor(
    private val plans: ParentingPlanRepository
) {

    /**
     * The plan of the family [myUid] shares with [partnerUid], as the export prints it.
     *
     * An account with no co-parent has no family and therefore no plan: that is an honest empty
     * source, printed as "no parenting plan recorded", not a failure to read one.
     */
    suspend fun read(myUid: String, partnerUid: String?): PlanSource {
        val parentUids = listOfNotNull(myUid, partnerUid)
        val familyId = FamilyKey.orNull(myUid, partnerUid)
            ?: return PlanSource(parentUids, emptyMap(), serverReached = true, unsentHere = false)
        val local = plans.localCopy(familyId)
        val remote = try {
            plans.serverHalves(familyId)
        } catch (e: CancellationException) {
            throw e
        } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
            Log.w(TAG, "Export could not read the parenting plan from the server; printing this phone's copy", e)
            null
        }
        return PlanSource(
            parentUids = parentUids,
            halves = remote ?: local.halves,
            serverReached = remote != null,
            // Only meaningful for the server's copy: this phone's own copy already holds the edits.
            unsentHere = remote != null && myUid in local.unsentAuthors
        )
    }

    private companion object {
        const val TAG = "CommunicationRecord"
    }
}
