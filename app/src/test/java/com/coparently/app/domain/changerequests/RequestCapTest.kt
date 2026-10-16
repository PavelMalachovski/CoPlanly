package com.coparently.app.domain.changerequests

import com.coparently.app.domain.custody.ChildScheduleOverride
import com.coparently.app.domain.custody.ContactWindow
import com.coparently.app.domain.model.CustodyModel
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import kotlin.test.assertEquals
import kotlin.test.assertNull

private val ZONE: ZoneId = ZoneId.of("Europe/Prague")

/** [text], a local date-time in Prague, as epoch millis. */
private fun at(text: String): Long = LocalDateTime.parse(text).atZone(ZONE).toInstant().toEpochMilli()

/** Saturday 10 October 2026, 10:07. */
private val NOW = at("2026-10-10T10:07")

private val TODAY: LocalDate = LocalDate.of(2026, 10, 10)

/**
 * The first moment a request would change something (phase 3a, spec §4.2): what a deadline and its
 * last chance must end before.
 */
class RequestCapTest {

    private fun date(month: Int, day: Int): LocalDate = LocalDate.of(2026, month, day)

    private fun local(text: String): LocalDateTime = LocalDateTime.parse(text)

    /** Monday 5 October is day 0: the first week is slot 1's, the second the other parent's. */
    private val agreed = CustodyModel.weekOnWeekOff("agreed", date(10, 5))

    @Test
    fun `a swap's cap is the start of its earliest day`() {
        val dates = listOf(date(10, 21), date(10, 16), date(10, 17))

        assertEquals(at("2026-10-16T00:00"), RequestCap.forSwap(dates, ZONE))
        assertNull(RequestCap.forSwap(emptyList(), ZONE))

        // An offer for today starts at 00:00 today, which is behind: the form then reads it as too soon.
        val forToday = RequestCap.forSwap(listOf(TODAY), ZONE)
        assertEquals(at("2026-10-10T00:00"), forToday)
        assertEquals(AnswerByBand.TOO_SOON, AnswerBy.offer(NOW, forToday, ZONE).band)
    }

    @Test
    fun `a change request's cap is the earlier start still ahead`() {
        assertEquals(
            at("2026-10-13T18:00"),
            RequestCap.forChange(local("2026-10-15T17:30"), local("2026-10-13T18:00"), NOW, ZONE)
        )
        // A series: its stored start is its first occurrence, long past, so only the proposed one counts.
        assertEquals(
            at("2026-10-20T09:00"),
            RequestCap.forChange(local("2025-01-01T09:00"), local("2026-10-20T09:00"), NOW, ZONE)
        )
        // Moved to a time already gone: the event's own start is what would change.
        assertEquals(
            at("2026-10-15T17:30"),
            RequestCap.forChange(local("2026-10-15T17:30"), local("2026-10-09T18:00"), NOW, ZONE)
        )
        // Both in the past, or beginning this very minute: nothing ahead would change.
        assertNull(RequestCap.forChange(local("2025-01-01T09:00"), local("2026-10-09T18:00"), NOW, ZONE))
        assertNull(RequestCap.forChange(local("2026-10-10T10:07"), local("2026-10-10T10:07"), NOW, ZONE))
    }

    @Test
    fun `an event's cap is its start until it begins`() {
        assertEquals(at("2026-10-13T09:00"), RequestCap.forEvent(local("2026-10-13T09:00"), NOW, ZONE))
        assertEquals(at("2026-10-10T18:30"), RequestCap.forEvent(local("2026-10-10T18:30"), NOW, ZONE))
        // It has begun, at this very minute or before: nothing ahead.
        assertNull(RequestCap.forEvent(local("2026-10-10T10:07"), NOW, ZONE))
        assertNull(RequestCap.forEvent(local("2026-10-01T09:00"), NOW, ZONE))
    }

    @Test
    fun `a proposal's cap is the first day anything changes, today or later`() {
        // No agreed schedule to change, and a copy of it: nothing would change.
        assertNull(RequestCap.forProposal(null, agreed, TODAY, ZONE))
        assertNull(RequestCap.forProposal(agreed, agreed.copy(id = "proposed"), TODAY, ZONE))

        // The same pattern anchored 14 days later is a whole cycle on: the same days, so no cap.
        val oneCycleOn = CustodyModel.weekOnWeekOff("proposed", date(10, 19))
        assertNull(RequestCap.forProposal(agreed, oneCycleOn, TODAY, ZONE))

        // The weeks the other way round differ on every day, today included: its 00:00, behind now.
        val swapped = CustodyModel.weekOnWeekOff("proposed", date(10, 19), momFirst = false)
        assertEquals(at("2026-10-10T00:00"), RequestCap.forProposal(agreed, swapped, TODAY, ZONE))

        // One day moves before today (8 October) and one after (14 October); the later one caps, and the
        // earlier one does not, however soon its fortnightly repeat (22 October) comes.
        val oneDayEachSide = CustodyModel.custom("proposed", date(10, 5), 14, (0..6).toSet() - 3 + 9)
        assertEquals(at("2026-10-14T00:00"), RequestCap.forProposal(agreed, oneDayEachSide, TODAY, ZONE))
        assertEquals(at("2026-10-22T00:00"), RequestCap.forProposal(agreed, oneDayEachSide, date(10, 15), ZONE))

        // A schedule with no cycle (an unvalidated document from the other phone) cannot be compared: no cap,
        // and no division by zero.
        val noCycle = CustodyModel.custom("broken", date(10, 5), 0, emptySet())
        assertNull(RequestCap.forProposal(noCycle, agreed, TODAY, ZONE))
        assertNull(RequestCap.forProposal(agreed, noCycle, TODAY, ZONE))
    }

    @Test
    fun `a change to an afternoon or to a child's own days caps a proposal too`() {
        // A contact window on cycle day 4: Friday 9 October is behind us, so the first one is 23 October.
        val afternoon = ContactWindow(4, LocalTime.of(15, 0), LocalTime.of(19, 0), "dad")
        val withWindow = agreed.copy(contactWindows = listOf(afternoon))
        assertEquals(agreed.getCustodyFor(date(10, 23)), withWindow.getCustodyFor(date(10, 23)))
        assertEquals(at("2026-10-23T00:00"), RequestCap.forProposal(agreed, withWindow, TODAY, ZONE))
        assertEquals(at("2026-10-23T00:00"), RequestCap.forProposal(withWindow, agreed, TODAY, ZONE))

        // A child who stays with slot 1 follows the family until 11 October and differs from 12 October,
        // when the family's week changes hands. No family day moves: ruling 10 counts the child's own.
        val staysPut = ChildScheduleOverride("child-1", 14, (0..13).toSet(), date(10, 5))
        val withChild = agreed.copy(childOverrides = listOf(staysPut))
        assertEquals(at("2026-10-12T00:00"), RequestCap.forProposal(agreed, withChild, TODAY, ZONE))
        // Either way round: a child brought back to the family's schedule changes on the same day.
        assertEquals(at("2026-10-12T00:00"), RequestCap.forProposal(withChild, agreed, TODAY, ZONE))
        assertNull(RequestCap.forProposal(withChild, withChild.copy(id = "proposed"), TODAY, ZONE))
    }

    @Test
    fun `a change to only one child's afternoon caps a proposal too`() {
        // Child 1 stays with slot 1 every day. No family day, no family afternoon and no day of the child's
        // moves below: only the child's own afternoon differs.
        val staysPut = ChildScheduleOverride("child-1", 14, (0..13).toSet(), date(10, 5))
        val withoutAfternoon = agreed.copy(childOverrides = listOf(staysPut))
        // The child is with slot 1 on cycle day 8 (Tuesday 13 October, then every fortnight), so an afternoon
        // with the other parent that day is one they would see.
        val ownAfternoon = staysPut.copy(contactWindows = listOf(afternoon(8, "dad")))
        val withAfternoon = agreed.copy(childOverrides = listOf(ownAfternoon))
        assertEquals(withoutAfternoon.getCustodyFor(date(10, 13)), withAfternoon.getCustodyFor(date(10, 13)))
        assertEquals(at("2026-10-13T00:00"), RequestCap.forProposal(withoutAfternoon, withAfternoon, TODAY, ZONE))
        // Either way round: taking the afternoon away changes the same day.
        assertEquals(at("2026-10-13T00:00"), RequestCap.forProposal(withAfternoon, withoutAfternoon, TODAY, ZONE))
        // From the 14th, the next one is a fortnight later.
        assertEquals(
            at("2026-10-27T00:00"),
            RequestCap.forProposal(withoutAfternoon, withAfternoon, date(10, 14), ZONE)
        )
        // An afternoon with the parent who already has the child that day is drawn nowhere: no change.
        val sameParent = staysPut.copy(contactWindows = listOf(afternoon(8, "mom")))
        val unseenForChild = agreed.copy(childOverrides = listOf(sameParent))
        assertNull(RequestCap.forProposal(withoutAfternoon, unseenForChild, TODAY, ZONE))

        // A child with a schedule of its own in only one of the two follows the family in the other, so the
        // family's afternoons are its afternoons there. This child has the family's days and one afternoon
        // with slot 1 on a day of the other parent.
        val familyDays = ChildScheduleOverride("child-1", 14, (0..6).toSet(), date(10, 5))
        val withOwnAfternoon = agreed.copy(
            childOverrides = listOf(familyDays.copy(contactWindows = listOf(afternoon(8, "mom"))))
        )
        assertEquals(at("2026-10-13T00:00"), RequestCap.forProposal(agreed, withOwnAfternoon, TODAY, ZONE))

        // The family's afternoon on a day of its own parent (cycle day 1) is drawn nowhere, so a child that
        // stops following the family loses nothing: the same family, the same days, no cap.
        val unseen = agreed.copy(contactWindows = listOf(afternoon(1, "mom")))
        val unseenChildAlone = unseen.copy(childOverrides = listOf(familyDays))
        assertNull(RequestCap.forProposal(unseen, unseenChildAlone, TODAY, ZONE))
        // With the other parent it is seen: the child no longer has it from 20 October (the 6th is behind us),
        // although the family's windows, and the child's days, are the same in both.
        val seen = agreed.copy(contactWindows = listOf(afternoon(1, "dad")))
        val seenChildAlone = seen.copy(childOverrides = listOf(familyDays))
        assertEquals(at("2026-10-20T00:00"), RequestCap.forProposal(seen, seenChildAlone, TODAY, ZONE))
    }

    /** 15:00 to 19:00 on cycle day [dayIndex], with [parent]. */
    private fun afternoon(dayIndex: Int, parent: String) =
        ContactWindow(dayIndex, LocalTime.of(15, 0), LocalTime.of(19, 0), parent)
}
