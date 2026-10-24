package com.coparently.app.domain.custody

import com.coparently.app.domain.model.CustodyModel
import com.coparently.app.domain.model.CustodyModelType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime

/**
 * A schedule change after pairing applies from its start date forward (owner decision,
 * 24 October 2026): the days before it keep the schedule that was in force, as history layers.
 */
class CustodyHistoryTest {

    private val old = CustodyModel(
        id = "old",
        modelType = CustodyModelType.WEEK_ON_WEEK_OFF,
        patternDays = 14,
        momDayIndices = (0..6).toSet(),
        startDate = LocalDate.of(2026, 8, 3)
    )

    private fun new(start: LocalDate) = old.copy(id = "new", momDayIndices = (7..13).toSet(), startDate = start)

    private fun day(month: Int, dayOfMonth: Int, year: Int = 2026) = LocalDate.of(year, month, dayOfMonth)

    @Test
    fun `accepted after its start date, the new schedule starts on the acceptance day`() {
        val incoming = new(day(10, 1))
        val frozen = CustodyHistory.freeze(old, incoming, acceptedOn = day(10, 10))

        assertEquals(old.getCustodyFor(day(10, 9)), frozen.getCustodyFor(day(10, 9)))
        assertEquals(old.getCustodyFor(day(10, 1)), frozen.getCustodyFor(day(10, 1)))
        assertEquals(incoming.getCustodyFor(day(10, 10)), frozen.getCustodyFor(day(10, 10)))
        // The two patterns disagree on those days, or the test proves nothing.
        assertTrue(old.getCustodyFor(day(10, 9)) != incoming.getCustodyFor(day(10, 9)))
    }

    @Test
    fun `accepted before its start date, the old schedule runs to the day before it`() {
        val incoming = new(day(11, 1))
        val frozen = CustodyHistory.freeze(old, incoming, acceptedOn = day(10, 10))

        // 25 October, inside the stretch the old pattern keeps, is a day the two patterns disagree on.
        assertEquals(old.getCustodyFor(day(10, 25)), frozen.getCustodyFor(day(10, 25)))
        assertTrue(old.getCustodyFor(day(10, 25)) != incoming.getCustodyFor(day(10, 25)))
        assertEquals(incoming.getCustodyFor(day(11, 1)), frozen.getCustodyFor(day(11, 1)))
        assertTrue(frozen.seasonalLayers.filter { it.isHistory }.maxOf { it.toDate } == day(10, 31))
    }

    @Test
    fun `the same pattern makes no history`() {
        val frozen = CustodyHistory.freeze(old, old.copy(id = "x"), day(10, 10))

        assertTrue(frozen.seasonalLayers.none { it.isHistory })
    }

    @Test
    fun `history reaches back to the old anchor and no further than three years`() {
        val ancient = old.copy(startDate = LocalDate.of(2020, 1, 6))
        val frozen = CustodyHistory.freeze(ancient, new(day(10, 10)), day(10, 10))
        val history = frozen.seasonalLayers.filter { it.isHistory }.sortedBy { it.fromDate }

        assertEquals(LocalDate.of(2023, 10, 10), history.first().fromDate)
        assertEquals(day(10, 9), history.last().toDate)
        history.forEach { assertTrue(it.spanDays <= SeasonalLayer.MAX_LAYER_DAYS) }
        history.zipWithNext().forEach { (a, b) -> assertEquals(a.toDate.plusDays(1), b.fromDate) }
    }

    @Test
    fun `history does not start before the old anchor`() {
        val frozen = CustodyHistory.freeze(old, new(day(10, 10)), day(10, 10))
        val history = frozen.seasonalLayers.filter { it.isHistory }

        assertEquals(old.startDate, history.minOf { it.fromDate })
    }

    @Test
    fun `a second change freezes only its own stretch`() {
        val second = new(day(10, 10))
        val first = CustodyHistory.freeze(old, second, day(10, 10))
        val third = old.copy(id = "third", momDayIndices = setOf(0, 1, 2), startDate = day(2, 1, 2027))
            .copy(seasonalLayers = first.seasonalLayers)
        val frozen = CustodyHistory.freeze(first, third, day(2, 1, 2027))

        val firstLayers = first.seasonalLayers.filter { it.isHistory }
        assertTrue(frozen.seasonalLayers.containsAll(firstLayers))
        assertEquals(old.getCustodyFor(day(9, 1)), frozen.getCustodyFor(day(9, 1)))
        assertEquals(second.getCustodyFor(day(12, 1)), frozen.getCustodyFor(day(12, 1)))
        assertEquals(third.getCustodyFor(day(2, 1, 2027)), frozen.getCustodyFor(day(2, 1, 2027)))
        val newOnes = frozen.seasonalLayers.filter { it.isHistory } - firstLayers.toSet()
        assertEquals(day(10, 10), newOnes.minOf { it.fromDate })
    }

    @Test
    fun `a change that starts sooner cuts short the history of one still waiting to start`() {
        // Accepted on 10 October to start on 1 November: the old pattern holds until then. A
        // second change accepted on 20 October, starting at once, ends that stretch on the 19th.
        val waiting = new(day(11, 1))
        val first = CustodyHistory.freeze(old, waiting, day(10, 10))
        val sooner = old.copy(id = "sooner", momDayIndices = setOf(0, 1, 2), startDate = day(10, 20))
            .copy(seasonalLayers = first.seasonalLayers)
        val frozen = CustodyHistory.freeze(first, sooner, day(10, 20))

        assertEquals(old.getCustodyFor(day(10, 19)), frozen.getCustodyFor(day(10, 19)))
        (20..31).forEach { d ->
            val expected = sooner.copy(seasonalLayers = emptyList()).getCustodyFor(day(10, d))
            assertEquals("Oct $d", expected, frozen.getCustodyFor(day(10, d)))
        }
        assertTrue(frozen.seasonalLayers.filter { it.isHistory }.all { it.toDate < day(10, 20) })
    }

    @Test
    fun `two stretches that end on the same effective date keep both after a later change`() {
        // A starts 1 Nov (accepted 10 Oct); B starts 20 Oct (accepted 15 Oct) and cuts A's history
        // short; C starts 1 Nov again (accepted 18 Oct) and freezes B over 20-31 Oct. Every stretch
        // must survive D, accepted on 1 Feb 2027.
        val a = new(day(11, 1))
        val afterA = CustodyHistory.freeze(old, a, day(10, 10))
        val b = old.copy(id = "b", momDayIndices = setOf(0, 1, 2), startDate = day(10, 20))
            .copy(seasonalLayers = afterA.seasonalLayers)
        val afterB = CustodyHistory.freeze(afterA, b, day(10, 15))
        val c = old.copy(id = "c", momDayIndices = setOf(3, 4), startDate = day(11, 1))
            .copy(seasonalLayers = afterB.seasonalLayers)
        val afterC = CustodyHistory.freeze(afterB, c, day(10, 18))
        val d = old.copy(id = "d", momDayIndices = setOf(5), startDate = day(2, 1, 2027))
            .copy(seasonalLayers = afterC.seasonalLayers)
        val afterD = CustodyHistory.freeze(afterC, d, day(2, 1, 2027))

        val bBase = b.copy(seasonalLayers = emptyList())
        (20..31).forEach { dd ->
            assertEquals("Oct $dd", bBase.getCustodyFor(day(10, dd)), afterD.getCustodyFor(day(10, dd)))
        }
        val ids = afterD.seasonalLayers.map { it.id }
        assertEquals(ids.size, ids.toSet().size)
    }

    @Test
    fun `a real layer on a past date still decides it`() {
        val july = SeasonalLayer.allWith("summer", "Summer", day(7, 1)..day(8, 31), ContactWindow.SLOT_TWO)
        val outgoing = old.copy(startDate = LocalDate.of(2026, 1, 5), seasonalLayers = listOf(july))
        val incoming = new(day(10, 10)).copy(seasonalLayers = listOf(july))
        val frozen = CustodyHistory.freeze(outgoing, incoming, day(10, 10))

        assertEquals(ContactWindow.SLOT_TWO, frozen.getCustodyFor(day(7, 15)))
        assertEquals(ContactWindow.SLOT_TWO, frozen.getCustodyFor(day(7, 13)))
    }

    @Test
    fun `the frozen past keeps its contact windows`() {
        val window = ContactWindow(dayIndex = 9, start = LocalTime.of(15, 0), end = LocalTime.of(19, 0), parent = "dad")
        val outgoing = old.copy(contactWindows = listOf(window))
        val frozen = CustodyHistory.freeze(outgoing, new(day(10, 10)), day(10, 10))
        // 2026-08-12 is index 9 of the old cycle anchored on 2026-08-03.
        assertEquals(listOf(window), frozen.contactWindowsOn(day(8, 12)))
    }

    @Test
    fun `the layer budget drops the oldest history, never a real layer`() {
        val reals = (1..30).map { d ->
            SeasonalLayer.allWith("real-$d", "Day $d", day(1, d, 2027)..day(1, d, 2027), ContactWindow.SLOT_ONE)
        }
        val ancient = old.copy(startDate = LocalDate.of(2020, 1, 6))
        val frozen = CustodyHistory.freeze(ancient, new(day(10, 10)).copy(seasonalLayers = reals), day(10, 10))

        assertTrue(frozen.seasonalLayers.containsAll(reals))
        assertTrue(frozen.seasonalLayers.size <= SeasonalLayerCodec.MAX_LAYERS)
        assertEquals(day(10, 9), frozen.seasonalLayers.filter { it.isHistory }.maxOf { it.toDate })
    }

    @Test
    fun `history layers carry the lowest priority and the hist- id`() {
        val frozen = CustodyHistory.freeze(old, new(day(10, 10)), day(10, 10))
        val history = frozen.seasonalLayers.filter { it.isHistory }

        assertTrue(history.isNotEmpty())
        history.forEach {
            assertEquals(SeasonalLayer.PRIORITY_RANGE.first, it.priority)
            assertEquals("hist-${it.fromDate.toString().replace("-", "")}", it.id)
        }
        // And they survive the wire, as any layer must.
        val wire = frozen.seasonalLayersWire()
        assertEquals(history.toSet(), SeasonalLayerCodec.decodeAll(wire).layers.filter { it.isHistory }.toSet())
    }

    @Test
    fun `the preview shows nothing before the effective date`() {
        assertEquals(day(10, 24), CustodyHistory.effectiveFrom(new(day(10, 1)), day(10, 24)))
        assertEquals(day(11, 1), CustodyHistory.effectiveFrom(new(day(11, 1)), day(10, 24)))
    }
}
