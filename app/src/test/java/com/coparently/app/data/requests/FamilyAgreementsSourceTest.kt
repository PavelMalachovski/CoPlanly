package com.coparently.app.data.requests

import app.cash.turbine.test
import com.coparently.app.data.repository.ParentingPlanRepository
import com.coparently.app.data.support.SupportAgreementStore
import com.coparently.app.domain.changerequests.RequestScope
import com.coparently.app.domain.parentingplan.ParentingPlanEntry
import com.coparently.app.domain.parentingplan.ParentingPlanPair
import com.coparently.app.domain.professionals.ProfessionalGrant
import com.coparently.app.domain.professionals.ProfessionalRole
import com.coparently.app.domain.repository.ProfessionalRepository
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Test
import java.io.IOException
import kotlin.test.assertEquals

/**
 * The family on screen's child support, professional grants and plan halves (board SW-7): each
 * read by the family's id, and none able to hold the inbox back by failing or by not answering.
 */
class FamilyAgreementsSourceTest {

    private val scope = RequestScope(myUid = "u1", coParentUid = "u2", familyId = "u1__u2", soleFamily = false)

    private fun grant(familyId: String) = ProfessionalGrant(
        id = "${familyId}__pro",
        familyId = familyId,
        familyParents = listOf("u1", "u2"),
        proUid = "pro",
        role = ProfessionalRole.LAWYER,
        name = "Dana",
        invitedBy = "u2",
        grantedAtMillis = 1L,
        expiresAtMillis = Long.MAX_VALUE,
        consents = mapOf("u2" to 1L)
    )

    @Test
    fun failingSupportListenerStillReady() = runTest {
        val plan = ParentingPlanPair(ParentingPlanEntry(), null)
        val source = FamilyAgreementsSource(
            supportStore = mockk<SupportAgreementStore> {
                every { observe("u1__u2") } returns flow { throw IOException("offline") }
            },
            professionalRepository = mockk<ProfessionalRepository> {
                every { observeFamilyGrants() } returns flowOf(listOf(grant("u1__u2"), grant("u1__u3")))
            },
            planRepository = mockk<ParentingPlanRepository> {
                every { observe("u1__u2", "u1") } returns flowOf(plan)
            }
        )

        source.observe(scope).test {
            var last = awaitItem()
            while (last.plan == null || last.professionals.isEmpty()) last = awaitItem()
            assertEquals(null, last.support)
            assertEquals(listOf(grant("u1__u2")), last.professionals)
            assertEquals(plan, last.plan)
            cancelAndIgnoreRemainingEvents()
        }
    }
}
