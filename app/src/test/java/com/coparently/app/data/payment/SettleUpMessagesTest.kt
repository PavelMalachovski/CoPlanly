package com.coparently.app.data.payment

import com.coparently.app.domain.activity.ActivityAnnouncement
import com.coparently.app.domain.activity.ActivityEntityType
import com.coparently.app.domain.activity.ActivityFallbackText
import com.coparently.app.domain.activity.ActivityKind
import org.junit.Test
import java.time.YearMonth
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Settle up is a card with the amount as a fact, not a sentence in the composer (tester report,
 * October 2026: the amount could be edited before it was sent).
 */
class SettleUpMessagesTest {

    private val october: YearMonth = YearMonth.of(2026, 10)

    @Test
    fun `a balance owed to me is an owed card with the month and a plain amount`() {
        assertEquals(
            ActivityAnnouncement(
                kind = ActivityKind.SETTLE_UP_OWED,
                entityType = ActivityEntityType.EXPENSE,
                entityId = "settle_2026-10_CZK",
                title = "2026-10",
                whenIso = "2026-10-01",
                amount = "1290.50",
                currency = "CZK"
            ),
            SettleUpMessages.cardFor(1290.5, "CZK", october)
        )
    }

    @Test
    fun `a balance I owe is an owing card with the amount unsigned`() {
        val card = SettleUpMessages.cardFor(-45.0, "EUR", october)

        assertEquals(ActivityKind.SETTLE_UP_OWING, card.kind)
        assertEquals("45.00", card.amount)
    }

    @Test
    fun `the payer is the reader of an owed card and the sender of an owing one`() {
        assertTrue(ActivityKind.SETTLE_UP_OWED.readerPays(readerIsSender = false))
        assertFalse(ActivityKind.SETTLE_UP_OWED.readerPays(readerIsSender = true))
        assertTrue(ActivityKind.SETTLE_UP_OWING.readerPays(readerIsSender = true))
        assertFalse(ActivityKind.SETTLE_UP_OWING.readerPays(readerIsSender = false))
        assertFalse(ActivityKind.EXPENSE_ADDED.readerPays(readerIsSender = false))
    }

    @Test
    fun `an older build shows the card as the sender would say it`() {
        assertEquals(
            "Settle up for 2026-10: you owe me 1290.50 CZK",
            ActivityFallbackText.of(SettleUpMessages.cardFor(1290.5, "CZK", october))
        )
        assertEquals(
            "Settle up for 2026-10: I owe you 45.00 EUR",
            ActivityFallbackText.of(SettleUpMessages.cardFor(-45.0, "EUR", october))
        )
    }
}
