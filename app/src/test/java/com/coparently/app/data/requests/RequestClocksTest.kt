package com.coparently.app.data.requests

import app.cash.turbine.test
import com.coparently.app.domain.changerequests.ClocksAt
import com.coparently.app.domain.changerequests.RequestDeadline
import com.coparently.app.domain.changerequests.RequestKind
import com.coparently.app.domain.repository.RequestDeadlineRepository
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneId
import java.time.ZoneOffset

/**
 * [RequestClocks] and [RequestTime]: the family's clocks seen at a moment, and the minute that
 * moves the moment on (phase 3a, spec §4.1).
 *
 * Nothing in the app asks the time itself: a card is a function of the clocks and of one `now`, so
 * every card on a screen agrees. What moves a card from "open" to "no answer" with nothing written
 * and nothing received is this stream re-reading the same clocks a minute later.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class RequestClocksTest {

    private val clock = RequestDeadline(
        id = "change_uidA_cr-1",
        kind = RequestKind.CHANGE,
        ref = "cr-1",
        familyId = FAMILY,
        proposerUid = "uidA",
        responderUid = "uidB",
        answerByMillis = START + HOUR
    )

    private val keeper = mockk<RequestClockKeeper>(relaxed = true)

    @Test
    fun `a tick re-reads the same clocks at the new time`() = runTest {
        var now = START
        val ticks = MutableSharedFlow<Unit>()
        val time = RequestTime(now = { now }, zoneOf = { ZoneOffset.UTC }, ticks = ticks)
        val repository = mockk<RequestDeadlineRepository>()
        every { repository.observeFamily(ME, FAMILY) } returns flowOf(listOf(clock))

        RequestClocks(repository, time, keeper).observeFamily(ME, FAMILY).test {
            ticks.emit(Unit)
            assertEquals("the clocks, seen when they arrived", ClocksAt.of(listOf(clock), START), awaitItem())

            now = START + 2 * HOUR
            ticks.emit(Unit)
            assertEquals(
                "the same clocks a tick later: nothing was written, the moment moved",
                ClocksAt.of(listOf(clock), START + 2 * HOUR),
                awaitItem()
            )
        }
    }

    @Test
    fun `a new snapshot is seen at the time it arrived, without waiting for a tick`() = runTest {
        var now = START
        val ticks = MutableSharedFlow<Unit>(replay = 1)
        ticks.emit(Unit)
        val snapshots = MutableSharedFlow<List<RequestDeadline>>()
        val repository = mockk<RequestDeadlineRepository>()
        every { repository.observeFamily(ME, FAMILY) } returns snapshots
        val time = RequestTime(now = { now }, zoneOf = { ZoneOffset.UTC }, ticks = ticks)

        RequestClocks(repository, time, keeper).observeFamily(ME, FAMILY).test {
            snapshots.emit(listOf(clock))
            assertEquals(ClocksAt.of(listOf(clock), START), awaitItem())

            now = START + 5 * MINUTE
            snapshots.emit(emptyList())
            assertEquals(
                "a withdrawn request's clock is gone, at the time that was seen",
                ClocksAt.of(emptyList(), START + 5 * MINUTE),
                awaitItem()
            )
        }
    }

    @Test
    fun `an undelivered clock addressed to this parent is handed to the keeper`() = runTest {
        // While the app runs, the listener is how this phone first sees a request's clock (spec
        // §5.3): it is delivered at once rather than at the next sync.
        val time = RequestTime(now = { START }, zoneOf = { ZoneOffset.UTC }, ticks = flowOf(Unit))
        val toMe = clock.copy(id = "change_uidA_cr-2", ref = "cr-2")
        val delivered = clock.copy(deliveredAtMillis = START)
        val mine = clock.copy(id = "change_uidB_cr-3", ref = "cr-3", proposerUid = ME, responderUid = "uidA")
        val repository = mockk<RequestDeadlineRepository>()
        every { repository.observeFamily(ME, FAMILY) } returns flowOf(listOf(toMe, delivered, mine))

        RequestClocks(repository, time, keeper).observeFamily(ME, FAMILY).test {
            awaitItem()
            awaitComplete()
        }

        verify(exactly = 1) { keeper.deliverSoon(ME, listOf(toMe)) }
    }

    @Test
    fun `the clock reads the system's time and zone unless it is given others`() {
        val before = System.currentTimeMillis()
        val production = RequestTime()
        val after = System.currentTimeMillis()

        assertTrue(production.nowMillis() in before..after + SLACK_MILLIS)
        assertEquals(ZoneId.systemDefault(), production.zone())

        val fixed = RequestTime(now = { START }, zoneOf = { ZoneId.of("Pacific/Kiritimati") }, ticks = flowOf())
        assertEquals(START, fixed.nowMillis())
        assertEquals(ZoneId.of("Pacific/Kiritimati"), fixed.zone())
    }

    @Test
    fun `the minute ticks at once, then on each minute boundary`() = runTest {
        // 20 seconds past a minute: the second tick is 40 seconds on, the rest a minute apart.
        val seen = mutableListOf<Long>()

        minuteTicks(now = { START + testScheduler.currentTime }).take(4).collect {
            seen += testScheduler.currentTime
        }

        assertEquals(listOf(0L, 40_000L, 100_000L, 160_000L), seen)
    }

    @Test
    fun `a tick on the boundary itself waits a whole minute rather than spinning`() = runTest {
        val onTheMinute = START - START % MINUTE
        val seen = mutableListOf<Long>()

        minuteTicks(now = { onTheMinute + testScheduler.currentTime }).take(3).collect {
            seen += testScheduler.currentTime
        }

        assertEquals(listOf(0L, MINUTE, 2 * MINUTE), seen)
    }

    private companion object {
        const val ME = "uidB"
        const val FAMILY = "uidA__uidB"
        const val MINUTE = 60_000L
        const val HOUR = 60 * MINUTE

        /** 2026-05-05T16:53:20Z: 20 seconds past a minute. */
        const val START = 1_778_000_000_000L

        /** Two reads of the system clock in a row, with a margin for a slow runner. */
        const val SLACK_MILLIS = 5_000L
    }
}
