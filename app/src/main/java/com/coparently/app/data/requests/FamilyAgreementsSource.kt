package com.coparently.app.data.requests

import android.util.Log
import com.coparently.app.data.repository.ParentingPlanRepository
import com.coparently.app.data.support.SupportAgreementStore
import com.coparently.app.data.support.SupportRead
import com.coparently.app.domain.changerequests.FamilyAgreements
import com.coparently.app.domain.changerequests.RequestScope
import com.coparently.app.domain.parentingplan.ParentingPlanPair
import com.coparently.app.domain.professionals.ProfessionalGrant
import com.coparently.app.domain.repository.ProfessionalRepository
import com.coparently.app.domain.support.SupportAgreement
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import javax.inject.Inject

/**
 * One family's child support, professional grants and parenting plan (board SW-7): what the
 * Requests inbox places beside the requests ([com.coparently.app.domain.changerequests.withAgreements]).
 *
 * Each is read for [RequestScope.familyId] alone — the support and plan documents by that id, the
 * grants kept to it — so another family's can never be placed. Each starts empty and turns empty
 * when its listener fails: the requests are never held back by these three.
 */
class FamilyAgreementsSource @Inject constructor(
    private val supportStore: SupportAgreementStore,
    private val professionalRepository: ProfessionalRepository,
    private val planRepository: ParentingPlanRepository
) {

    /** [scope]'s family's three documents, live; an empty [FamilyAgreements] at once. */
    fun observe(scope: RequestScope): Flow<FamilyAgreements> = combine(
        supportStore.observe(scope.familyId)
            .map { (it as? SupportRead.Known)?.agreement }
            .orEmptyOnFailure<SupportAgreement?>(null, "child support"),
        professionalRepository.observeFamilyGrants()
            .map { grants -> grants.filter { it.familyId == scope.familyId } }
            .orEmptyOnFailure<List<ProfessionalGrant>>(emptyList(), "professional grants"),
        planRepository.observe(scope.familyId, scope.myUid)
            .orEmptyOnFailure<ParentingPlanPair?>(null, "parenting plan")
    ) { support, grants, plan -> FamilyAgreements(support, grants, plan) }
        .distinctUntilChanged()

    private fun <T> Flow<T>.orEmptyOnFailure(empty: T, what: String): Flow<T> =
        catch { e ->
            if (e is CancellationException) throw e
            Log.w(TAG, "The family's $what could not be read for the requests", e)
            emit(empty)
        }.onStart { emit(empty) }

    private companion object {
        const val TAG = "FamilyAgreementsSource"
    }
}
