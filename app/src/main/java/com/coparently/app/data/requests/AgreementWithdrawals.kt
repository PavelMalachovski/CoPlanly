package com.coparently.app.data.requests

import com.coparently.app.data.support.SupportAgreementStore
import com.coparently.app.domain.repository.ProfessionalRepository
import com.coparently.app.domain.support.SupportAgreement
import javax.inject.Inject

/**
 * What the Requests inbox's Withdraw does on the sender's own child support proposal and on a
 * professional's access still waiting for the co-parent's consent (board SW-7) — the same writes
 * the Child support and Professionals screens make, so the two places cannot differ.
 */
class AgreementWithdrawals @Inject constructor(
    private val supportStore: SupportAgreementStore,
    private val professionalRepository: ProfessionalRepository
) {

    /** Withdraws this parent's own proposal in [agreement]; the agreed terms stay. */
    suspend fun withdrawSupport(agreement: SupportAgreement): Boolean = supportStore.clearProposal(agreement)

    /** Ends the grant [grantId]: with one parent's consent withdrawn there is nothing left to wait on. */
    suspend fun withdrawProfessional(grantId: String): Boolean = professionalRepository.revoke(grantId).isSuccess
}
