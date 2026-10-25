package com.coparently.app.domain.support

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal
import java.time.LocalDate
import java.time.YearMonth

/** Child support's months and its reminder (25 October 2026). */
class SupportTermsTest {

    private val terms = SupportTerms(
        amount = BigDecimal("5000.00"),
        currency = "CZK",
        dayOfMonth = 10,
        payerUid = "bob",
        fromMonth = YearMonth.of(2026, 8)
    )

    @Test
    fun `the months listed start at the agreement and stop at twelve`() {
        assertEquals(
            listOf(YearMonth.of(2026, 10), YearMonth.of(2026, 9), YearMonth.of(2026, 8)),
            terms.monthsUpTo(LocalDate.of(2026, 10, 25))
        )
        assertEquals(SupportTerms.MAX_MONTHS, terms.monthsUpTo(LocalDate.of(2028, 1, 1)).size)
        assertTrue(terms.monthsUpTo(LocalDate.of(2026, 7, 31)).isEmpty())
    }

    @Test
    fun `the payee is the other participant`() {
        assertEquals("alice", terms.payeeIn(listOf("alice", "bob")))
    }

    @Test
    fun `a reminder comes from its days before the due day, once a month, and never once paid`() {
        val october = YearMonth.of(2026, 10)
        assertFalse(SupportReminderRule.shouldRemind(terms, LocalDate.of(2026, 10, 6), 3, false, null))
        assertTrue(SupportReminderRule.shouldRemind(terms, LocalDate.of(2026, 10, 7), 3, false, null))
        assertTrue(SupportReminderRule.shouldRemind(terms, LocalDate.of(2026, 10, 20), 0, false, null))
        assertFalse(SupportReminderRule.shouldRemind(terms, LocalDate.of(2026, 10, 20), 0, true, null))
        assertFalse(SupportReminderRule.shouldRemind(terms, LocalDate.of(2026, 10, 20), 0, false, october))
        assertTrue(SupportReminderRule.shouldRemind(terms, LocalDate.of(2026, 11, 10), 0, false, october))
    }

    @Test
    fun `no reminder before the agreement's first month`() {
        assertFalse(SupportReminderRule.shouldRemind(terms, LocalDate.of(2026, 7, 28), 7, false, null))
    }
}
