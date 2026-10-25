package com.coparently.app.data.support

import com.coparently.app.domain.payment.PaymentQr
import com.coparently.app.domain.support.SupportAgreement
import com.coparently.app.domain.support.SupportProposal
import com.coparently.app.domain.support.SupportTerms
import java.time.YearMonth

/**
 * The wire form of `support_agreements/{familyId}` (25 October 2026), in both directions. The
 * amount is a two-decimal string, as on every payment; the day an integer the rules bound to
 * 1–28; the first month `yyyy-MM`.
 */
object SupportAgreementDocument {

    /** [terms] as the agreement map: what an acceptance copies from the proposal, field for field. */
    fun termsMap(terms: SupportTerms): Map<String, Any> = mapOf(
        "amount" to PaymentQr.amountText(terms.amount),
        "currency" to terms.currency,
        "dayOfMonth" to terms.dayOfMonth,
        "payerUid" to terms.payerUid,
        "fromMonth" to terms.fromMonth.toString()
    )

    /** [proposal] as its map. */
    fun proposalMap(proposal: SupportProposal): Map<String, Any> =
        termsMap(proposal.terms) +
            mapOf("proposedBy" to proposal.proposedBy, "proposedAtMillis" to proposal.proposedAtMillis)

    /** The agreement [data] describes, or null when it cannot be read. */
    fun read(familyId: String, data: Map<String, Any?>): SupportAgreement? {
        val participants = (data["participants"] as? List<*>)?.mapNotNull { it as? String } ?: return null
        val proposal = (data["proposal"] as? Map<*, *>)?.let { map ->
            val terms = terms(map) ?: return@let null
            val by = map["proposedBy"] as? String ?: return@let null
            SupportProposal(terms, by, (map["proposedAtMillis"] as? Number)?.toLong() ?: 0L)
        }
        return SupportAgreement(
            familyId = familyId,
            participants = participants,
            agreement = (data["agreement"] as? Map<*, *>)?.let(::terms),
            proposal = proposal
        )
    }

    private fun terms(map: Map<*, *>): SupportTerms? = SupportTerms(
        amount = (map["amount"] as? String)?.toBigDecimalOrNull() ?: return null,
        currency = map["currency"] as? String ?: return null,
        dayOfMonth = (map["dayOfMonth"] as? Number)?.toInt() ?: return null,
        payerUid = map["payerUid"] as? String ?: return null,
        fromMonth = (map["fromMonth"] as? String)
            ?.let { runCatching { YearMonth.parse(it) }.getOrNull() }
            ?: return null
    )
}
