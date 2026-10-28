package com.coparently.app.data.repository

import com.coparently.app.data.remote.firebase.PushPayload
import com.coparently.app.domain.custody.DayOverrideStatus
import com.coparently.app.domain.custody.SwapSides
import org.junit.Test
import java.time.LocalDate
import kotlin.test.assertEquals
import kotlin.test.assertFalse

/**
 * The push about a day swap (day swap design, 28 October 2026): an offer names its days each way,
 * from the addressee's side, and who sent it, so the other phone can say "Lana offers you 2 days"
 * without opening the app.
 */
class SwapPushTest {

    private val nov6 = LocalDate.of(2026, 11, 6)
    private val nov7 = LocalDate.of(2026, 11, 7)
    private val nov14 = LocalDate.of(2026, 11, 14)

    @Test
    fun `an offer carries the days each way and its sender`() {
        val push = swapPush(
            "2026-11-06",
            DayOverrideStatus.PENDING,
            3,
            sides = SwapSides(toOther = listOf(nov6, nov7), toOfferer = listOf(nov14)),
            actorName = "Lana"
        )!!
        assertEquals(PushPayload.DAY_SWAP_GROUP_OFFERED, push[PushPayload.TYPE])
        assertEquals("3", push[PushPayload.DAY_COUNT])
        assertEquals("2026-11-06,2026-11-07", push[PushPayload.DATES_TO_YOU])
        assertEquals("2026-11-14", push[PushPayload.DATES_TO_SENDER])
        assertEquals("Lana", push[PushPayload.ACTOR])
    }

    @Test
    fun `a side with no days is left out`() {
        val push = swapPush(
            "2026-11-06",
            DayOverrideStatus.PENDING,
            1,
            sides = SwapSides(toOther = listOf(nov6), toOfferer = emptyList())
        )!!
        assertEquals(PushPayload.DAY_SWAP_OFFERED, push[PushPayload.TYPE])
        assertFalse(PushPayload.DATES_TO_SENDER in push)
        assertFalse(PushPayload.ACTOR in push)
    }

    @Test
    fun `an answer names its sender but no days`() {
        val push = swapPush(
            "2026-11-06",
            DayOverrideStatus.ACCEPTED,
            2,
            sides = SwapSides(toOther = listOf(nov6, nov7), toOfferer = emptyList()),
            actorName = "Bob"
        )!!
        assertEquals("day_swap_group_accepted", push[PushPayload.TYPE])
        assertFalse(PushPayload.DATES_TO_YOU in push)
        assertEquals("Bob", push[PushPayload.ACTOR])
    }

    @Test
    fun `a caller that passes nothing gets the push it got before`() {
        assertEquals(
            mapOf(
                PushPayload.TYPE to "day_swap_group_offered",
                PushPayload.DATE to "2026-11-06",
                PushPayload.DAY_COUNT to "2"
            ),
            swapPush("2026-11-06", DayOverrideStatus.PENDING, 2)
        )
        assertEquals(
            mapOf(PushPayload.TYPE to "day_swap_offered", PushPayload.DATE to "2026-11-06"),
            swapPush("2026-11-06", DayOverrideStatus.PENDING, 1)
        )
    }

    @Test
    fun `a withdrawal has no push and a long name is cut to the rule's bound`() {
        assertEquals(null, swapPush("2026-11-06", DayOverrideStatus.CANCELLED, 1))
        val push = swapPush("2026-11-06", DayOverrideStatus.DECLINED, 1, actorName = "x".repeat(150))!!
        assertEquals(100, push.getValue(PushPayload.ACTOR).length)
    }
}
