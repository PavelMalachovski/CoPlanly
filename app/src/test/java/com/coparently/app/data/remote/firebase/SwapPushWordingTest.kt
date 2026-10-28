package com.coparently.app.data.remote.firebase

import org.junit.Test
import java.time.LocalDate
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Which sentence a day-swap offer's push gets on the receiving phone (day swap design, 28 October
 * 2026): who offers which days, decided from the two date fields alone. The words themselves are
 * `PushNotificationTest`'s, on a device.
 */
class SwapPushWordingTest {

    private val nov6 = LocalDate.of(2026, 11, 6)
    private val nov7 = LocalDate.of(2026, 11, 7)
    private val nov14 = LocalDate.of(2026, 11, 14)

    private fun offer(vararg fields: Pair<String, String>) =
        mapOf(PushPayload.TYPE to PushPayload.DAY_SWAP_GROUP_OFFERED) + fields

    @Test
    fun `days only to the reader are an offer`() {
        assertEquals(
            SwapPushWording.Gives(listOf(nov6, nov7)),
            swapPushWording(
                PushPayload.DAY_SWAP_GROUP_OFFERED,
                offer(PushPayload.DATES_TO_YOU to "2026-11-06,2026-11-07")
            )
        )
    }

    @Test
    fun `days only to the sender are an ask`() {
        assertEquals(
            SwapPushWording.Asks(listOf(nov14)),
            swapPushWording(PushPayload.DAY_SWAP_OFFERED, offer(PushPayload.DATES_TO_SENDER to "2026-11-14"))
        )
    }

    @Test
    fun `days each way are a swap`() {
        assertEquals(
            SwapPushWording.Both(toYou = listOf(nov6), toSender = listOf(nov14)),
            swapPushWording(
                PushPayload.DAY_SWAP_GROUP_OFFERED,
                offer(PushPayload.DATES_TO_YOU to "2026-11-06", PushPayload.DATES_TO_SENDER to "2026-11-14")
            )
        )
    }

    @Test
    fun withoutFieldsIsNull() {
        assertNull(swapPushWording(PushPayload.DAY_SWAP_GROUP_OFFERED, offer(PushPayload.DAY_COUNT to "2")))
    }

    @Test
    fun `an answer is never worded by its days`() {
        assertNull(swapPushWording(PushPayload.DAY_SWAP_ACCEPTED, offer(PushPayload.DATES_TO_YOU to "2026-11-06")))
        assertNull(swapPushWording(null, offer(PushPayload.DATES_TO_YOU to "2026-11-06")))
    }

    @Test
    fun `a garbled date is dropped, and nothing readable is no wording`() {
        assertEquals(
            SwapPushWording.Gives(listOf(nov6)),
            swapPushWording(PushPayload.DAY_SWAP_OFFERED, offer(PushPayload.DATES_TO_YOU to "2026-11-06,soon"))
        )
        assertNull(swapPushWording(PushPayload.DAY_SWAP_OFFERED, offer(PushPayload.DATES_TO_YOU to "soon, later")))
    }
}
