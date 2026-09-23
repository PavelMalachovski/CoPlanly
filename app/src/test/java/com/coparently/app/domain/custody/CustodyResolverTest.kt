package com.coparently.app.domain.custody

import com.coparently.app.domain.model.CustodyModel
import com.coparently.app.domain.model.CustodyModelType
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The ordering that decides whose day it is, and the handover walk that depends on it.
 *
 * The last two cases are the ones that would otherwise ship broken. A swap both creates a
 * handover on the day it moves and removes the one it displaced, and `HandoverCalculator`
 * reading the pattern directly fails at neither compile time nor run time — it simply tells the
 * home screen a date that is wrong, on exactly the days a swap touched, while the grid beside it
 * paints the swap correctly.
 */
class CustodyResolverTest {

    /** Week on, week off: slot 1 holds days 0-6 of a 14-day cycle, slot 2 the rest. */
    private val model = CustodyModel(
        id = "m1",
        modelType = CustodyModelType.WEEK_ON_WEEK_OFF,
        patternDays = 14,
        momDayIndices = (0..6).toSet(),
        startDate = LocalDate.of(2026, 8, 31) // a Monday
    )

    private val momDay: LocalDate = LocalDate.of(2026, 9, 2) // index 2 -> mom
    private val noLegacy: (LocalDate) -> String? = { null }

    private fun override(
        toParent: String,
        status: DayOverrideStatus
    ) = DayOverride(
        toParent = toParent,
        requestedBy = "uid-mom",
        requestedAt = "2026-08-23T10:00:00",
        status = status,
        decidedBy = "uid-dad".takeIf { status != DayOverrideStatus.PENDING },
        decidedAt = "2026-08-23T11:00:00".takeIf { status != DayOverrideStatus.PENDING }
    )

    private fun custodyFor(
        overrides: Map<String, DayOverride> = emptyMap(),
        activeModel: CustodyModel? = model,
        legacy: (LocalDate) -> String? = noLegacy
    ) = CustodyResolver.resolver(activeModel, overrides, legacy)

    @Test
    fun `the pattern answers when nothing overrides it`() {
        assertEquals("mom", custodyFor()(momDay))
    }

    @Test
    fun `an accepted override wins over the pattern`() {
        val overrides = mapOf(momDay.toString() to override("dad", DayOverrideStatus.ACCEPTED))

        assertEquals("dad", custodyFor(overrides)(momDay))
    }

    @Test
    fun `a pending override changes nothing, because nobody has agreed to it`() {
        val overrides = mapOf(momDay.toString() to override("dad", DayOverrideStatus.PENDING))

        assertEquals("mom", custodyFor(overrides)(momDay))
    }

    @Test
    fun `a declined override changes nothing`() {
        val overrides = mapOf(momDay.toString() to override("dad", DayOverrideStatus.DECLINED))

        assertEquals("mom", custodyFor(overrides)(momDay))
    }

    @Test
    fun `an override for another date leaves this one alone`() {
        val overrides = mapOf(
            momDay.plusDays(3).toString() to override("dad", DayOverrideStatus.ACCEPTED)
        )

        assertEquals("mom", custodyFor(overrides)(momDay))
    }

    @Test
    fun `with no model the legacy schedule is still consulted`() {
        val resolve = custodyFor(activeModel = null, legacy = { "dad" })

        assertEquals("dad", resolve(momDay))
    }

    @Test
    fun `an accepted override wins even when only the legacy schedule exists`() {
        val overrides = mapOf(momDay.toString() to override("mom", DayOverrideStatus.ACCEPTED))
        val resolve = custodyFor(overrides, activeModel = null, legacy = { "dad" })

        assertEquals("mom", resolve(momDay))
    }

    @Test
    fun `with neither the answer is null rather than a guess`() {
        assertNull(custodyFor(activeModel = null)(momDay))
    }

    @Test
    fun `a handover day is one whose custody differs from the day before`() {
        // The cycle starts on 31 August, so 7 September is the first day slot 2 holds.
        val switchDay = LocalDate.of(2026, 9, 7)

        assertTrue(CustodyResolver.isHandoverDay(custodyFor(), switchDay))
        assertFalse(CustodyResolver.isHandoverDay(custodyFor(), switchDay.plusDays(1)))
    }

    @Test
    fun `an unanswered day is not a handover, it is an unknown`() {
        assertFalse(
            CustodyResolver.isHandoverDay(custodyFor(activeModel = null), momDay)
        )
    }

    @Test
    fun `nextHandoverFrom finds a handover created by an accepted swap`() {
        // 2 September is mom's and so is the 3rd, so without a swap the next handover is the 7th.
        val plain = HandoverCalculator.nextHandoverFrom(model, momDay)
        assertEquals(LocalDate.of(2026, 9, 7), plain?.date)

        val swapped = HandoverCalculator.nextHandoverFrom(
            model,
            momDay,
            mapOf(momDay.plusDays(1).toString() to override("dad", DayOverrideStatus.ACCEPTED))
        )

        assertEquals(LocalDate.of(2026, 9, 3), swapped?.date)
        assertEquals("mom", swapped?.fromParent)
        assertEquals("dad", swapped?.toParent)
    }

    @Test
    fun `nextHandoverFrom no longer reports a handover a swap removed`() {
        // The 7th is the pattern's switch day. Swapping it back to slot 1 removes that handover,
        // so the next one is the 8th - the day the pattern's own answer resumes.
        val swapped = HandoverCalculator.nextHandoverFrom(
            model,
            momDay,
            mapOf(LocalDate.of(2026, 9, 7).toString() to override("mom", DayOverrideStatus.ACCEPTED))
        )

        assertEquals(LocalDate.of(2026, 9, 8), swapped?.date)
    }

    @Test
    fun `a pending swap does not move the handover`() {
        val pending = HandoverCalculator.nextHandoverFrom(
            model,
            momDay,
            mapOf(momDay.plusDays(1).toString() to override("dad", DayOverrideStatus.PENDING))
        )

        assertEquals(LocalDate.of(2026, 9, 7), pending?.date)
    }

    @Test
    fun `a contact window with the parent who does not have the day is shown`() {
        // momDay is index 2, slot 1's day: an afternoon with slot 2 is the news.
        val afternoon = window(dayIndex = 2, parent = "dad")
        val withWindows = model.copy(contactWindows = listOf(afternoon))

        val windowsFor = CustodyResolver.contactWindowsResolver(withWindows, custodyFor(activeModel = withWindows))

        assertEquals(listOf(afternoon), windowsFor(momDay))
        assertTrue(windowsFor(momDay.plusDays(1)).isEmpty())
    }

    @Test
    fun `a contact window naming the day's own parent is dropped`() {
        val withWindows = model.copy(contactWindows = listOf(window(dayIndex = 2, parent = "mom")))

        val windowsFor = CustodyResolver.contactWindowsResolver(withWindows, custodyFor(activeModel = withWindows))

        assertTrue(windowsFor(momDay).isEmpty())
    }

    @Test
    fun `an accepted swap that hands the day to the window's parent drops the window`() {
        // The same rule the calendar grid followed inline before the lookup moved here: once the
        // day is slot 2's, an afternoon with slot 2 is not an afternoon with anybody new.
        val withWindows = model.copy(contactWindows = listOf(window(dayIndex = 2, parent = "dad")))
        val overrides = mapOf(momDay.toString() to override("dad", DayOverrideStatus.ACCEPTED))

        val windowsFor = CustodyResolver.contactWindowsResolver(
            withWindows,
            custodyFor(overrides, activeModel = withWindows)
        )

        assertTrue(windowsFor(momDay).isEmpty())
    }

    @Test
    fun `no pattern means no contact windows`() {
        val windowsFor = CustodyResolver.contactWindowsResolver(null, custodyFor(activeModel = null))

        assertTrue(windowsFor(momDay).isEmpty())
    }

    private fun window(dayIndex: Int, parent: String) = ContactWindow(
        dayIndex = dayIndex,
        start = LocalTime.parse("15:00"),
        end = LocalTime.parse("19:00"),
        parent = parent
    )
}
