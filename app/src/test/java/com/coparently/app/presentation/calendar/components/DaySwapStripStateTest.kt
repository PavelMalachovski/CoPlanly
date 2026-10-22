package com.coparently.app.presentation.calendar.components

import com.coparently.app.domain.custody.DayOverride
import com.coparently.app.domain.custody.DayOverrideStatus
import org.junit.Test
import java.time.LocalDate
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** What Day view's strip says about a day's swap (21 October 2026, Zuzana's test). */
class DaySwapStripStateTest {

    private val today = LocalDate.of(2026, 10, 22)
    private val day = LocalDate.of(2026, 10, 24)

    private fun swap(status: DayOverrideStatus, by: String = "alice") = DayOverride(
        toParent = "dad",
        requestedBy = by,
        requestedAt = "2026-10-21T10:00:00",
        status = status,
        decidedBy = "bob".takeIf { status != DayOverrideStatus.PENDING },
        decidedAt = "2026-10-21T11:00:00".takeIf { status != DayOverrideStatus.PENDING }
    )

    @Test
    fun `my offer waits and can be withdrawn, theirs waits on me`() {
        assertEquals(
            DaySwapStripState.OfferedByMe("dad"),
            DaySwapStripState.of(swap(DayOverrideStatus.PENDING), "alice", day, today)
        )
        assertEquals(
            DaySwapStripState.OfferedToMe("alice", "dad"),
            DaySwapStripState.of(swap(DayOverrideStatus.PENDING), "bob", day, today)
        )
    }

    @Test
    fun `an agreed day can be changed while it is ahead, not after`() {
        val agreed = swap(DayOverrideStatus.ACCEPTED)

        assertEquals(DaySwapStripState.Agreed("dad", canChange = true), DaySwapStripState.of(agreed, "bob", day, today))
        assertEquals(
            DaySwapStripState.Agreed("dad", canChange = false),
            DaySwapStripState.of(agreed, "bob", today.minusDays(1), today)
        )
    }

    @Test
    fun `a declined day with nothing agreed, no swap or no account draws nothing`() {
        assertNull(DaySwapStripState.of(swap(DayOverrideStatus.DECLINED), "alice", day, today))
        assertNull(DaySwapStripState.of(null, "alice", day, today))
        assertNull(DaySwapStripState.of(swap(DayOverrideStatus.PENDING), null, day, today))
    }
}
