package com.coparently.app.domain.support

import kotlinx.datetime.LocalDate
import kotlinx.datetime.YearMonth
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Child support's months and its reminder (25 October 2026). */
class SupportTermsTest {

    private val terms = SupportTerms(
        amountMinor = 500_000L,
        currency = "CZK",
        dayOfMonth = 10,
        payerUid = "bob",
        fromMonth = YearMonth(2026, 8)
    )

    @Test
    fun `the months listed start at the agreement and stop at twelve`() {
        assertEquals(
            listOf(YearMonth(2026, 10), YearMonth(2026, 9), YearMonth(2026, 8)),
            terms.monthsUpTo(LocalDate(2026, 10, 25))
        )
        assertEquals(SupportTerms.MAX_MONTHS, terms.monthsUpTo(LocalDate(2028, 1, 1)).size)
        assertTrue(terms.monthsUpTo(LocalDate(2026, 7, 31)).isEmpty())
    }

    @Test
    fun `the payee is the other participant`() {
        assertEquals("alice", terms.payeeIn(listOf("alice", "bob")))
    }

    @Test
    fun `a reminder comes from its days before the due day - once a month - and never once paid`() {
        val october = YearMonth(2026, 10)
        assertFalse(SupportReminderRule.shouldRemind(terms, LocalDate(2026, 10, 6), 3, false, null))
        assertTrue(SupportReminderRule.shouldRemind(terms, LocalDate(2026, 10, 7), 3, false, null))
        assertTrue(SupportReminderRule.shouldRemind(terms, LocalDate(2026, 10, 20), 0, false, null))
        assertFalse(SupportReminderRule.shouldRemind(terms, LocalDate(2026, 10, 20), 0, true, null))
        assertFalse(SupportReminderRule.shouldRemind(terms, LocalDate(2026, 10, 20), 0, false, october))
        assertTrue(SupportReminderRule.shouldRemind(terms, LocalDate(2026, 11, 10), 0, false, october))
    }

    @Test
    fun `no reminder before the agreement's first month`() {
        assertFalse(SupportReminderRule.shouldRemind(terms, LocalDate(2026, 7, 28), 7, false, null))
    }
}
