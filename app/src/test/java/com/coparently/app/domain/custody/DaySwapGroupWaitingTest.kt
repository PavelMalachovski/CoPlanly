package com.coparently.app.domain.custody

import org.junit.Test
import java.time.LocalDate
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * The days of an offer still waiting on the reader (owner decision, 28 October 2026): after a
 * part-answer by day, the card and its main buttons show and decide only those.
 */
class DaySwapGroupWaitingTest {

    private val nov6 = LocalDate.of(2026, 11, 6)
    private val nov7 = LocalDate.of(2026, 11, 7)
    private val nov8 = LocalDate.of(2026, 11, 8)

    private fun day(date: LocalDate, status: DayOverrideStatus = DayOverrideStatus.PENDING, by: String = "lana") =
        DaySwap(
            date,
            DayOverride(toParent = "dad", requestedBy = by, requestedAt = "2026-10-28T10:00:00", status = status)
        )

    @Test
    fun `a day already answered is not waiting`() {
        val group = DaySwapGroup(listOf(day(nov6), day(nov7, DayOverrideStatus.ACCEPTED), day(nov8)))
        assertEquals(listOf(nov6, nov8), group.waitingOn("pavel")?.swaps?.map { it.date })
    }

    @Test
    fun `an offer answered day by day to the end waits on nobody`() {
        val group = DaySwapGroup(listOf(day(nov6, DayOverrideStatus.DECLINED), day(nov7, DayOverrideStatus.ACCEPTED)))
        assertNull(group.waitingOn("pavel"))
    }

    @Test
    fun `an offer the reader made never waits on them`() {
        assertNull(DaySwapGroup(listOf(day(nov6, by = "pavel"))).waitingOn("pavel"))
    }

    @Test
    fun `the mini months mark only the days still waiting`() {
        val group = DaySwapGroup(listOf(day(nov6, DayOverrideStatus.DECLINED), day(nov7), day(nov8)))
        assertEquals(mapOf(nov7 to "dad", nov8 to "dad"), group.pendingDays)
    }
}
