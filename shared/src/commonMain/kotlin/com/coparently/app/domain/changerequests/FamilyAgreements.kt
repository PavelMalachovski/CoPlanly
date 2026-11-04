package com.coparently.app.domain.changerequests

import com.coparently.app.domain.parentingplan.ParentingPlanComparison
import com.coparently.app.domain.parentingplan.ParentingPlanPair
import com.coparently.app.domain.professionals.ProfessionalGrant
import com.coparently.app.domain.professionals.ProfessionalGrantPolicy
import com.coparently.app.domain.professionals.ProfessionalGrantStatus
import com.coparently.app.domain.support.SupportAgreement

/**
 * The three things beside the requests that need both parents' word (board SW-7), as one family's
 * documents hold them: its child support, its professional grants and both halves of its parenting
 * plan. Nulls and empties while a document has not arrived, or cannot be read.
 *
 * @property support `support_agreements/{familyId}`, item 49 of CLAUDE.md.
 * @property professionals The family's `professional_grants`, item 29.
 * @property plan Both halves of `parenting_plans/{familyId}`, item 21.
 */
data class FamilyAgreements(
    val support: SupportAgreement? = null,
    val professionals: List<ProfessionalGrant> = emptyList(),
    val plan: ParentingPlanPair? = null
)

/**
 * This inbox with [agreements] placed in its two waiting sections (board SW-7). Nothing enters the
 * history: an answered proposal or consent is that screen's own record.
 *
 * - **Child support** waits on whoever did not propose it.
 * - **A professional's access** waits on the parent whose consent is missing
 *   ([ProfessionalGrantStatus.WAITING_FOR_YOU]), and on the co-parent while only theirs is
 *   ([ProfessionalGrantStatus.WAITING_FOR_CO_PARENT] with this parent's consent given). An active
 *   or expired grant waits on nobody.
 * - **The parenting plan** waits on this parent alone
 *   ([ParentingPlanComparison.questionsAwaitingYou]).
 *
 * Only [scope]'s family counts: a document of another family is ignored, never placed.
 *
 * @param nowMillis The moment a grant's expiry is compared with.
 */
fun RequestInbox.withAgreements(scope: RequestScope, agreements: FamilyAgreements, nowMillis: Long): RequestInbox {
    val me = scope.myUid
    val support = agreements.support?.takeIf { it.familyId == scope.familyId }?.proposal
    val grants = agreements.professionals.filter { it.familyId == scope.familyId }
    val grantsOnMe = grants.filter {
        ProfessionalGrantPolicy.statusFor(it, me, nowMillis) == ProfessionalGrantStatus.WAITING_FOR_YOU
    }
    val grantsOnThem = grants.filter {
        me in it.consents &&
            ProfessionalGrantPolicy.statusFor(it, me, nowMillis) == ProfessionalGrantStatus.WAITING_FOR_CO_PARENT
    }
    val plan = agreements.plan
        ?.let { ParentingPlanComparison.questionsAwaitingYou(it.yours, it.theirs) }
        .orEmpty()
    return copy(
        waitingOnMe = waitingOnMe.copy(
            support = support?.takeIf { it.proposedBy != me },
            professionals = grantsOnMe,
            planQuestions = plan
        ),
        waitingOnThem = waitingOnThem.copy(
            support = support?.takeIf { it.proposedBy == me },
            professionals = grantsOnThem
        )
    )
}
