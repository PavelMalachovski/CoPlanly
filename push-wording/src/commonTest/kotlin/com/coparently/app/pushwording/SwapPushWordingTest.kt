package com.coparently.app.pushwording

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Which sentence a day-swap offer's push gets on the receiving phone (day swap design, 28 October
 * 2026): who offers which days, decided from the two date fields alone. The words themselves are
 * the golden test's and `PushNotificationTest`'s.
 */
class SwapPushWordingTest {

    private fun offer(vararg fields: Pair<String, String>) =
        mapOf("type" to "day_swap_group_offered") + fields

    @Test
    fun daysOnlyToTheReaderAreAnOffer() {
        assertEquals(
            SwapPushWording.Gives(listOf("2026-11-06", "2026-11-07")),
            swapPushWording("day_swap_group_offered", offer("datesToYou" to "2026-11-07, 2026-11-06"))
        )
    }

    @Test
    fun daysOnlyFromTheReaderAreAnAsk() {
        assertEquals(
            SwapPushWording.Asks(listOf("2026-11-14")),
            swapPushWording("day_swap_offered", offer("datesToSender" to "2026-11-14"))
        )
    }

    @Test
    fun daysBothWaysAreBoth() {
        assertEquals(
            SwapPushWording.Both(toYou = listOf("2026-11-06"), toSender = listOf("2026-11-14")),
            swapPushWording(
                "day_swap_offered",
                offer("datesToYou" to "2026-11-06", "datesToSender" to "2026-11-14")
            )
        )
    }

    @Test
    fun anOlderSendersOfferAndEveryOtherTypeKeepTheirSentence() {
        assertNull(swapPushWording("day_swap_group_offered", offer("dayCount" to "2")))
        assertNull(swapPushWording("day_swap_accepted", offer("datesToYou" to "2026-11-06")))
        assertNull(swapPushWording(null, offer("datesToYou" to "2026-11-06")))
    }

    @Test
    fun anUnreadableDateIsDroppedAndNoneLeftIsNoWording() {
        assertEquals(
            SwapPushWording.Gives(listOf("2026-11-06")),
            swapPushWording("day_swap_offered", offer("datesToYou" to "2026-11-06,soon,2026-02-30"))
        )
        assertNull(swapPushWording("day_swap_offered", offer("datesToYou" to "soon, later")))
    }

    @Test
    fun anIsoDateIsReadAsLocalDateParseReadsIt() {
        assertTrue(isIsoDate("2026-11-06"))
        assertTrue(isIsoDate("2028-02-29"))
        assertTrue(isIsoDate("2000-02-29"))
        assertTrue(isIsoDate("2026-12-31"))
        assertFalse(isIsoDate("2026-02-29"))
        assertFalse(isIsoDate("1900-02-29"))
        assertFalse(isIsoDate("2026-04-31"))
        assertFalse(isIsoDate("2026-13-01"))
        assertFalse(isIsoDate("2026-00-10"))
        assertFalse(isIsoDate("2026-01-00"))
        assertFalse(isIsoDate("2026-1-05"))
        assertFalse(isIsoDate(" 2026-01-05"))
        assertFalse(isIsoDate("2026-01-05T10:00"))
        assertFalse(isIsoDate(""))
    }
}
