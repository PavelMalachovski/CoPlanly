package com.coparently.app.domain.support

import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.YearMonth
import kotlinx.datetime.minus
import kotlinx.datetime.yearMonth

/**
 * When a payer's phone reminds them of this month's child support (25 October 2026, owner
 * decision: "an optional reminder"). Pure, so the JVM tests reach every rule.
 */
object SupportReminderRule {

    /** How many days before the due day a reminder may come; the screen offers exactly these. */
    val DAYS_BEFORE: List<Int> = listOf(0, 1, 3, 7)

    /**
     * Whether to remind today.
     *
     * Only from [daysBefore] days before this month's due day, only while this month is not paid,
     * only for a month the terms cover, and only once a month — a reminder that came back every
     * few hours until the payer paid would be the nagging this app does not do.
     *
     * @param paidThisMonth Whether a payment for this month is recorded, confirmed or not.
     * @param lastReminded The month this phone last reminded about, or null.
     */
    fun shouldRemind(
        terms: SupportTerms,
        today: LocalDate,
        daysBefore: Int,
        paidThisMonth: Boolean,
        lastReminded: YearMonth?
    ): Boolean {
        val month = today.yearMonth
        if (month < terms.fromMonth || paidThisMonth || lastReminded == month) return false
        return today >= terms.dueDate(month).minus(daysBefore, DateTimeUnit.DAY)
    }
}
