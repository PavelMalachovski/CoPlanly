package com.coparently.app.data.support

import com.coparently.app.domain.support.SupportProposal
import com.coparently.app.domain.support.SupportTerms
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.math.BigDecimal
import java.time.YearMonth

/** `support_agreements/{familyId}` as the rules bound it (25 October 2026). */
class SupportAgreementDocumentTest {

    private val terms = SupportTerms(BigDecimal("5000"), "CZK", 5, "bob", YearMonth.of(2026, 10))

    @Test
    fun `the terms are written as the rules bound them`() {
        assertEquals(
            mapOf(
                "amount" to "5000.00",
                "currency" to "CZK",
                "dayOfMonth" to 5,
                "payerUid" to "bob",
                "fromMonth" to "2026-10"
            ),
            SupportAgreementDocument.termsMap(terms)
        )
    }

    @Test
    fun `an agreement and a proposal read back as they were written`() {
        val proposal = SupportProposal(terms.copy(amount = BigDecimal("6000.00")), "alice", 7L)
        val read = SupportAgreementDocument.read(
            "alice__bob",
            mapOf(
                "participants" to listOf("alice", "bob"),
                "agreement" to SupportAgreementDocument.termsMap(terms),
                "proposal" to SupportAgreementDocument.proposalMap(proposal),
                "lastModifiedBy" to "alice"
            )
        )
        assertEquals(BigDecimal("5000.00"), read?.agreement?.amount)
        assertEquals(5, read?.agreement?.dayOfMonth)
        assertEquals(YearMonth.of(2026, 10), read?.agreement?.fromMonth)
        assertEquals("alice", read?.proposal?.proposedBy)
        assertEquals(BigDecimal("6000.00"), read?.proposal?.terms?.amount)
    }

    @Test
    fun `terms missing a field are not read, and a document without participants is not either`() {
        val broken = SupportAgreementDocument.termsMap(terms) - "fromMonth"
        val read = SupportAgreementDocument.read("f", mapOf("participants" to listOf("a", "b"), "agreement" to broken))
        assertNull(read?.agreement)
        assertNull(SupportAgreementDocument.read("f", mapOf("agreement" to broken)))
    }
}
