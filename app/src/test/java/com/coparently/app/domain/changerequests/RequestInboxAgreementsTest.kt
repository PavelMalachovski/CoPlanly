package com.coparently.app.domain.changerequests

import com.coparently.app.domain.parentingplan.ParentingPlanCatalogue
import com.coparently.app.domain.parentingplan.ParentingPlanEntry
import com.coparently.app.domain.parentingplan.ParentingPlanPair
import com.coparently.app.domain.professionals.ProfessionalGrant
import com.coparently.app.domain.professionals.ProfessionalRole
import com.coparently.app.domain.support.SupportAgreement
import com.coparently.app.domain.support.SupportProposal
import com.coparently.app.domain.support.SupportTerms
import org.junit.Test
import java.math.BigDecimal
import java.time.YearMonth
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Child support, a professional's access and parenting plan answers in the Requests inbox (PR 2,
 * board SW-7): who each waits on, and that they count on Home's row but never on the calendar's
 * banner.
 */
class RequestInboxAgreementsTest {

    private val me = "u1"
    private val co = "u2"
    private val family = "u1__u2"
    private val scope = RequestScope(myUid = me, coParentUid = co, familyId = family, soleFamily = false)
    private val now = 1_000L

    private val terms = SupportTerms(BigDecimal("3000"), "CZK", 15, co, YearMonth.of(2026, 11))

    private fun support(by: String, familyId: String = family) =
        SupportAgreement(familyId, listOf(me, co), proposal = SupportProposal(terms, by, 1L))

    private fun grant(
        consents: Map<String, Long>,
        familyId: String = family,
        expiresAt: Long = 10_000L
    ) = ProfessionalGrant(
        id = "${familyId}__pro",
        familyId = familyId,
        familyParents = listOf(me, co),
        proUid = "pro",
        role = ProfessionalRole.MEDIATOR,
        name = "Dana",
        invitedBy = consents.keys.first(),
        grantedAtMillis = 1L,
        expiresAtMillis = expiresAt,
        consents = consents
    )

    private fun placed(agreements: FamilyAgreements) = RequestInbox().withAgreements(scope, agreements, now)

    @Test
    fun coParentsSupportProposalWaitsOnMe() {
        val inbox = placed(FamilyAgreements(support = support(by = co)))
        assertNotNull(inbox.waitingOnMe.support)
        assertNull(inbox.waitingOnThem.support)
        assertEquals(1, inbox.waitingOnMe.count)
    }

    @Test
    fun mySupportProposalWaitsOnThem() {
        val inbox = placed(FamilyAgreements(support = support(by = me)))
        assertNull(inbox.waitingOnMe.support)
        assertNotNull(inbox.waitingOnThem.support)
    }

    @Test
    fun supportOfAnotherFamilyIgnored() {
        val inbox = placed(FamilyAgreements(support = support(by = co, familyId = "u1__u3")))
        assertNull(inbox.waitingOnMe.support)
        assertNull(inbox.waitingOnThem.support)
    }

    @Test
    fun grantAwaitingMyConsentWaitsOnMe() {
        val g = grant(mapOf(co to 1L))
        val inbox = placed(FamilyAgreements(professionals = listOf(g)))
        assertEquals(listOf(g), inbox.waitingOnMe.professionals)
        assertTrue(inbox.waitingOnThem.professionals.isEmpty())
    }

    @Test
    fun myConsentGivenWaitsOnThem() {
        val g = grant(mapOf(me to 1L))
        val inbox = placed(FamilyAgreements(professionals = listOf(g)))
        assertTrue(inbox.waitingOnMe.professionals.isEmpty())
        assertEquals(listOf(g), inbox.waitingOnThem.professionals)
    }

    @Test
    fun expiredGrantIgnored() {
        val inbox = placed(FamilyAgreements(professionals = listOf(grant(mapOf(co to 1L), expiresAt = 500L))))
        assertTrue(inbox.waitingOnMe.professionals.isEmpty())
        assertTrue(inbox.waitingOnThem.professionals.isEmpty())
    }

    @Test
    fun professionalOfAnotherFamilyIgnored() {
        val inbox = placed(FamilyAgreements(professionals = listOf(grant(mapOf(co to 1L), familyId = "u1__u3"))))
        assertTrue(inbox.waitingOnMe.professionals.isEmpty())
        assertTrue(inbox.waitingOnThem.professionals.isEmpty())
    }

    @Test
    fun activeGrantIgnored() {
        val inbox = placed(FamilyAgreements(professionals = listOf(grant(mapOf(co to 1L, me to 2L)))))
        assertTrue(inbox.waitingOnMe.professionals.isEmpty())
        assertTrue(inbox.waitingOnThem.professionals.isEmpty())
    }

    @Test
    fun planQuestionsCountAsOneRow() {
        val ids = ParentingPlanCatalogue.questions.map { it.id }
        val theirs = ParentingPlanEntry(answers = mapOf(ids[0] to "A", ids[1] to "B"))
        val inbox = placed(FamilyAgreements(plan = ParentingPlanPair(ParentingPlanEntry(), theirs)))
        assertEquals(2, inbox.waitingOnMe.planQuestions.size)
        assertEquals(1, inbox.waitingOnMe.count)
        assertTrue(inbox.waitingOnThem.planQuestions.isEmpty())
    }

    @Test
    fun agreementsNotInCalendarBanner() {
        val ids = ParentingPlanCatalogue.questions.map { it.id }
        val inbox = placed(
            FamilyAgreements(
                support = support(by = co),
                professionals = listOf(grant(mapOf(co to 1L))),
                plan = ParentingPlanPair(ParentingPlanEntry(), ParentingPlanEntry(answers = mapOf(ids[0] to "A")))
            )
        )
        assertEquals(3, inbox.waitingOnMe.count)
        assertEquals(0, inbox.waitingOnMe.countWithoutProposal)
    }
}
