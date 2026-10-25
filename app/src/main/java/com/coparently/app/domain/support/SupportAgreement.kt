package com.coparently.app.domain.support

import java.math.BigDecimal
import java.time.LocalDate
import java.time.YearMonth

/**
 * What a child support agreement says (25 October 2026, owner decision: "a fixed amount once a
 * month"): how much, in which currency, on which day of the month, who pays, and from which month.
 *
 * @property dayOfMonth 1–28, so every month has the day.
 * @property fromMonth The first month the agreement covers; earlier months are not listed.
 */
data class SupportTerms(
    val amount: BigDecimal,
    val currency: String,
    val dayOfMonth: Int,
    val payerUid: String,
    val fromMonth: YearMonth
) {
    /** The parent paid: whichever of [participants] does not pay. */
    fun payeeIn(participants: List<String>): String? = participants.singleOrNull { it != payerUid }

    /** The day this agreement's payment falls due in [month]. */
    fun dueDate(month: YearMonth): LocalDate = month.atDay(dayOfMonth.coerceIn(1, MAX_DAY))

    /** The months to list up to [today]: from [fromMonth], the last [MAX_MONTHS] at most, newest first. */
    fun monthsUpTo(today: LocalDate): List<YearMonth> {
        val now = YearMonth.from(today)
        if (now.isBefore(fromMonth)) return emptyList()
        return generateSequence(now) { it.minusMonths(1) }
            .takeWhile { !it.isBefore(fromMonth) }
            .take(MAX_MONTHS)
            .toList()
    }

    /** The bounds the rules repeat. */
    companion object {
        /** The last day every month has. */
        const val MAX_DAY = 28

        /** How many months the screen lists. */
        const val MAX_MONTHS = 12
    }
}

/**
 * A change of the terms one parent proposed and the other has not answered yet.
 *
 * @property proposedBy The proposer's uid.
 * @property proposedAtMillis When, on the proposer's phone.
 */
data class SupportProposal(val terms: SupportTerms, val proposedBy: String, val proposedAtMillis: Long)

/**
 * A family's child support (`support_agreements/{familyId}`): the terms in force, if any, and at
 * most one proposal. Money is never set for somebody else: even the first terms are a proposal
 * the other parent accepts as it stands.
 */
data class SupportAgreement(
    val familyId: String,
    val participants: List<String>,
    val agreement: SupportTerms? = null,
    val proposal: SupportProposal? = null
)
