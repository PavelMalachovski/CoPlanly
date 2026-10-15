package com.coparently.app.domain.changerequests

import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

private const val H = 3_600_000L
private const val T0 = 1_791_000_000_000L

/**
 * The states a request's clock puts it in (phase 3a, spec §4.1 and §4.4), and the arithmetic of the
 * last chance: the button is offered for a day after the deadline but never later than two hours
 * before the cap, and pressing it gives a day or what is left up to one hour before the cap.
 */
class RequestClockTest {

    @Suppress("LongParameterList") // one clock's fields, all defaulted
    private fun clock(
        answerBy: Long = T0,
        cap: Long? = null,
        proposer: String = "uA",
        responder: String = "uB",
        lastChanceAt: Long? = null,
        until: Long? = null,
        outcome: RequestClockOutcome? = null,
        reminders: List<Long> = emptyList()
    ) = RequestDeadline(
        id = "swap_uA_g-1",
        kind = RequestKind.SWAP,
        ref = "g-1",
        familyId = "uA__uB",
        proposerUid = proposer,
        responderUid = responder,
        answerByMillis = answerBy,
        capMillis = cap,
        reminderTimesMillis = reminders,
        lastChanceAtMillis = lastChanceAt,
        lastChanceUntilMillis = until,
        outcome = outcome
    )

    @Test
    fun `before its deadline a request is open`() {
        assertEquals(ClockState.OPEN, RequestClock.stateOf(clock(), T0 - 1))
    }

    @Test
    fun `with no cap the button is offered for a day after the deadline`() {
        val c = clock()
        assertEquals(ClockState.NO_ANSWER_LAST_CHANCE_POSSIBLE, RequestClock.stateOf(c, T0))
        assertEquals(24 * H, RequestClock.lastChanceWindowMillis(c, T0))
        assertEquals(T0 + 24 * H, RequestClock.buttonUntilMillis(c))
        // The button's own last moment is still inside the day; the day's end is not.
        assertEquals(ClockState.NO_ANSWER_LAST_CHANCE_POSSIBLE, RequestClock.stateOf(c, T0 + 24 * H - 1))
        assertEquals(ClockState.NO_ANSWER, RequestClock.stateOf(c, T0 + 24 * H))
        assertEquals(T0 + 24 * H, RequestClock.endMillis(c))
    }

    @Test
    fun `a near cap shortens the last chance and ends the button two hours before it`() {
        val c = clock(cap = T0 + 10 * H)
        assertEquals(9 * H, RequestClock.lastChanceWindowMillis(c, T0))
        assertEquals(T0 + 8 * H, RequestClock.buttonUntilMillis(c))
        assertEquals(90 * 60_000L, RequestClock.lastChanceWindowMillis(c, T0 + 7 * H + 30 * 60_000))
        // Even the last moment the button is offered leaves a last chance of more than an hour.
        assertEquals(H + 1, RequestClock.lastChanceWindowMillis(c, T0 + 8 * H - 1))
        assertEquals(ClockState.NO_ANSWER, RequestClock.stateOf(c, T0 + 8 * H))
    }

    @Test
    fun `a cap less than two hours after the deadline offers no button and ends at the deadline`() {
        val c = clock(cap = T0 + 90 * 60_000)
        assertEquals(ClockState.NO_ANSWER, RequestClock.stateOf(c, T0))
        assertNull(RequestClock.lastChanceWindowMillis(c, T0))
        assertEquals(T0, RequestClock.endMillis(c))
    }

    @Test
    fun `a last chance runs to its end, and the button never comes back`() {
        val c = clock(lastChanceAt = T0 + H, until = T0 + 25 * H)
        assertEquals(ClockState.LAST_CHANCE, RequestClock.stateOf(c, T0 + 2 * H))
        assertNull(RequestClock.lastChanceWindowMillis(c, T0 + 2 * H))
        assertEquals(ClockState.LAST_CHANCE, RequestClock.stateOf(c, T0 + 25 * H - 1))
        assertEquals(ClockState.NO_ANSWER, RequestClock.stateOf(c, T0 + 25 * H))
        assertEquals(T0 + 25 * H, RequestClock.endMillis(c))
    }

    @Test
    fun `a stored outcome is final whatever the time`() {
        val closed = clock(outcome = RequestClockOutcome.NO_ANSWER)
        assertEquals(ClockState.NO_ANSWER, RequestClock.stateOf(closed, T0 - H))
        assertEquals(ClockState.NO_ANSWER, RequestClock.stateOf(closed, T0 + H))
        assertNull(RequestClock.lastChanceWindowMillis(closed, T0 + H))
    }

    @Test
    fun `a clock governs a record only when it names the record's author and the other parent`() {
        val c = clock(proposer = "uA", responder = "uB")
        assertTrue(RequestClock.governs(c, "uA", "uB"))
        assertFalse(RequestClock.governs(c, "uB", "uA"))
        assertFalse(RequestClock.governs(c, "uA", "uC"))
    }

    @Test
    fun `a timed request knows its next reminder`() {
        val c = clock(reminders = listOf(T0 - 2 * H, T0 - H))
        assertEquals(T0 - H, TimedRequest(c, T0 - 90 * 60_000).nextReminderMillis)
        assertNull(TimedRequest(c, T0 - 30 * 60_000).nextReminderMillis)
        // A reminder that is due this very moment is not the next one: it is the one being shown.
        assertNull(TimedRequest(c, T0 - H).nextReminderMillis)
    }

    @Test
    fun `a timed request waits on the responder while it is open or in its last chance`() {
        val c = clock()
        val open = TimedRequest(c, T0 - H)
        assertEquals(ClockState.OPEN, open.state)
        assertTrue(open.waitsOnResponder)

        // Past the deadline the next move is the proposer's, and the button is what they are offered.
        val lastChancePossible = TimedRequest(c, T0 + H)
        assertEquals(ClockState.NO_ANSWER_LAST_CHANCE_POSSIBLE, lastChancePossible.state)
        assertEquals(24 * H, lastChancePossible.lastChanceWindowMillis)
        assertFalse(lastChancePossible.waitsOnResponder)

        val given = TimedRequest(clock(lastChanceAt = T0 + H, until = T0 + 25 * H), T0 + 2 * H)
        assertEquals(ClockState.LAST_CHANCE, given.state)
        assertNull(given.lastChanceWindowMillis)
        assertTrue(given.waitsOnResponder)

        val over = TimedRequest(c, T0 + 24 * H)
        assertEquals(ClockState.NO_ANSWER, over.state)
        assertFalse(over.waitsOnResponder)
    }

    @Test
    fun `the family's clocks are looked up by id at the moment they were read`() {
        val c = clock()
        val clocks = ClocksAt.of(listOf(c), T0)
        assertEquals(T0, clocks.timed(c.id)?.nowMillis)
        assertEquals(c, clocks.timed(c.id)?.clock)
        assertNull(clocks.timed("x"))
        assertNull(clocks.timed(null))
    }
}
