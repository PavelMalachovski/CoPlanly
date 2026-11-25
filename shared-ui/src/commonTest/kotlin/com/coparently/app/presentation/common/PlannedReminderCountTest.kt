package com.coparently.app.presentation.common

import com.coparently.app.domain.changerequests.AnswerByBand
import com.coparently.app.domain.changerequests.AnswerByOffer
import com.coparently.app.domain.changerequests.ReminderPlan
import kotlin.test.Test
import kotlin.test.assertEquals

/** The swap sheet's summary and [AnswerByField]'s consequences line state one reminder count. */
class PlannedReminderCountTest {
    private val hour = 3_600_000L
    private val offer = AnswerByOffer(AnswerByBand.CHOICES, nowMillis = 1_000_000L)

    @Test
    fun `the count is the reminder plan's for the window from now to the deadline`() {
        for (hours in listOf(1L, 5L, 6L, 23L, 24L, 100L)) {
            assertEquals(
                ReminderPlan.countFor(hours * hour),
                plannedReminderCount(offer, offer.nowMillis + hours * hour)
            )
        }
        assertEquals(3, plannedReminderCount(offer, offer.nowMillis + 30 * hour))
        assertEquals(2, plannedReminderCount(offer, offer.nowMillis + 8 * hour))
        assertEquals(1, plannedReminderCount(offer, offer.nowMillis + 2 * hour))
    }
}
