package com.coparently.app.data.family

import com.coparently.app.data.remote.firebase.FirestoreParentingPlanDataSource
import com.coparently.app.data.support.SupportAgreementStore
import com.coparently.app.domain.repository.ProfessionalRepository
import javax.inject.Inject

/**
 * The three listeners [OtherFamiliesSignals] adds per other family for board SW-7 — child support,
 * professional grants and the parenting plan — each read for one family by its id.
 *
 * @property supportStore `support_agreements/{familyId}`, by id.
 * @property professionalRepository The family's grants naming this parent, keyed by family.
 * @property planDataSource `parenting_plans/{familyId}`, by id.
 */
class AgreementSignalSources @Inject constructor(
    val supportStore: SupportAgreementStore,
    val professionalRepository: ProfessionalRepository,
    val planDataSource: FirestoreParentingPlanDataSource
)
