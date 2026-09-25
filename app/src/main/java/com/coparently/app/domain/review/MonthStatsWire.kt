package com.coparently.app.domain.review

import kotlin.math.roundToLong

/**
 * The `stats` a month-summary request sends to the `aiAssist` callable: a [MonthReview]'s figures,
 * each parent **named** (never "mom"/"dad"), as plain maps, lists, numbers and strings.
 *
 * Only numbers and the two names leave the phone — no event title, no expense note, no message.
 * The server validates this shape and **refuses an unknown key**, so it is exactly:
 *
 * ```
 * {
 *   month: "YYYY-MM",
 *   daysWithParent: [{name, days}],                 // 1–2 entries, days summing to ≤ the month
 *   handovers, swapsProposed, swapsAccepted,        // accepted ≤ proposed
 *   eventsCount,
 *   expensesByCurrency: [{currency, total}],
 *   balanceByCurrency: [{currency, amount, owedBy, owedTo}],  // names "" only when amount is 0
 *   holidayFairness: [{name, days}]                 // optional: only when the month had any
 * }
 * ```
 *
 * A name is at most [NAME_MAX] characters with no `<` or `>`; amounts are rounded to cents. The
 * key names are pinned by `MonthReviewCalculatorTest` — change them only with the server.
 */
object MonthStatsWire {

    /** The longest name the server accepts. */
    const val NAME_MAX = 60

    private const val CENTS = 100.0

    /**
     * Whether [review] can be summarised at all: the server needs at least one parent's days, so a
     * month without a schedule has nothing to send and the screen offers no summary for it.
     */
    fun canSend(review: MonthReview): Boolean = review.daysBySlot.isNotEmpty()

    /**
     * The wire form of [review].
     *
     * @param nameForSlot The name to print for a slot — `ParentNames.labelFor`
     * @param nameForUid The name to print for a uid — `ParentNames.labelForUid`
     */
    fun of(
        review: MonthReview,
        nameForSlot: (String) -> String,
        nameForUid: (String) -> String
    ): Map<String, Any> = buildMap<String, Any> {
        put("month", review.month.toString())
        put("daysWithParent", review.daysBySlot.named(nameForSlot))
        put("handovers", review.handovers)
        put("swapsProposed", review.swapDaysOffered)
        put("swapsAccepted", review.swapDaysAgreed)
        put("eventsCount", review.sharedEvents)
        put(
            "expensesByCurrency",
            review.money.map { mapOf("currency" to it.currency, "total" to cents(it.total)) }
        )
        put("balanceByCurrency", review.money.map { money -> money.balance(nameForUid) })
        if (review.specialDaysBySlot.isNotEmpty()) {
            put("holidayFairness", review.specialDaysBySlot.named(nameForSlot))
        }
    }

    private fun Map<String, Int>.named(nameForSlot: (String) -> String): List<Map<String, Any>> =
        entries.sortedBy { it.key }.map { (slot, days) -> mapOf("name" to clean(nameForSlot(slot)), "days" to days) }

    private fun MonthMoney.balance(nameForUid: (String) -> String): Map<String, Any> {
        val transfer = settlement
        return mapOf(
            "currency" to currency,
            "amount" to (transfer?.let { cents(it.amount) } ?: 0.0),
            "owedBy" to (transfer?.let { clean(nameForUid(it.fromUid)) } ?: ""),
            "owedTo" to (transfer?.let { clean(nameForUid(it.toUid)) } ?: "")
        )
    }

    /** A name as the server accepts it: no angle brackets, at most [NAME_MAX] characters. */
    private fun clean(name: String): String =
        name.replace("<", "").replace(">", "").trim().take(NAME_MAX)

    private fun cents(amount: Double): Double = (amount * CENTS).roundToLong() / CENTS
}
