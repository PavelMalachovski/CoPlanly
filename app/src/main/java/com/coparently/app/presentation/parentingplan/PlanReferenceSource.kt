package com.coparently.app.presentation.parentingplan

import com.coparently.app.data.repository.ParentingPlanRepository
import com.coparently.app.domain.family.FamilyKey
import com.coparently.app.domain.parentingplan.CitationStatus
import com.coparently.app.domain.parentingplan.ParentingPlanEntry
import com.coparently.app.domain.parentingplan.PlanCitationCodec
import com.coparently.app.domain.parentingplan.PlanReference
import com.coparently.app.domain.parentingplan.PlanScheduleLink
import com.coparently.app.domain.repository.UserRepository
import com.coparently.app.presentation.common.ParentsSource
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The parenting plan as the custody screens need it (MON-21): the agreed answer an editor quotes,
 * and whether a proposal's citation still matches the plan.
 *
 * One place, so the custody editor, the seasonal-layer editor and the proposal card derive the
 * family and the two halves the same way. Everything it decides is [PlanScheduleLink]'s; this only
 * reads.
 */
@Singleton
class PlanReferenceSource @Inject constructor(
    private val repository: ParentingPlanRepository,
    private val userRepository: UserRepository,
    private val parentsSource: ParentsSource
) {

    /**
     * The agreed answer to [questionId] for the editor to quote, or null when it is not an agreed
     * schedule question — or when this account has no co-parent to have agreed with.
     *
     * Read once, from this phone's Room copy: the plan screen that opened the editor showed the
     * agreement from the same rows a moment ago. The family comes from
     * [ParentsSource.coParentUid], the cheap accessor a save path is meant to use (CLAUDE.md
     * item 17), because the citation this returns rides on a save.
     */
    suspend fun referenceFor(questionId: String): PlanReference? {
        val myUid = userRepository.getCurrentUserId() ?: return null
        val familyId = FamilyKey.orNull(myUid, parentsSource.coParentUid()) ?: return null
        val halves = repository.localCopy(familyId).halves
        return PlanScheduleLink.reference(
            questionId = questionId,
            yours = halves[myUid] ?: ParentingPlanEntry(),
            theirs = halves.entries.firstOrNull { it.key != myUid }?.value
        )
    }

    /**
     * What the parent answering a proposal is told about its citation [wire], live — an edit to
     * the plan while the card is on screen turns "from the parenting plan" into "changed since".
     *
     * A proposal with no citation, or one this build cannot read, never opens a listener.
     *
     * @param myUid The signed-in parent, reading the proposal.
     * @param proposerUid The parent who made it; the plan is the family the two of them share.
     */
    fun observeCitation(wire: String?, myUid: String, proposerUid: String): Flow<CitationStatus> {
        if (PlanCitationCodec.decode(wire) == null) return flowOf(CitationStatus.None)
        val familyId = FamilyKey.orNull(myUid, proposerUid) ?: return flowOf(CitationStatus.None)
        return repository.observe(familyId, myUid).map { pair ->
            PlanScheduleLink.citationStatus(wire, pair.yours, pair.theirs)
        }
    }
}
