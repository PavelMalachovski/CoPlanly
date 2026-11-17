package com.coparently.app.data.requests

import com.coparently.app.domain.changerequests.ReminderPlan
import com.coparently.app.domain.changerequests.RequestClockOutcome
import com.coparently.app.domain.changerequests.RequestDeadline
import com.coparently.app.domain.changerequests.RequestDeadlineId
import com.coparently.app.domain.changerequests.RequestKind
import com.coparently.app.domain.notification.RequestClockScheduler
import com.coparently.app.domain.repository.RequestDeadlineRepository
import io.mockk.Called
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.TimeZone
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Before
import org.junit.Test

/**
 * [RequestClockKeeper]: what each full sync, and each listener snapshot while the app runs, does
 * with this parent's open clocks (phase 3a, spec §4.3, §4.5 and §5.3).
 *
 * The answering phone delivers a clock once: it plans the reminders in its own zone, records the
 * delivery with the plan, and schedules them. The proposer's phone keeps nothing since MON-25: the
 * deadline notice and the end are the server's. A clock of a family this parent has left, or one
 * whose id does not spell its own fields, is left alone.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class RequestClockKeeperTest {

    private val repository = mockk<RequestDeadlineRepository>()
    private val scheduler = mockk<RequestClockScheduler>(relaxed = true)
    private var now = SENT + HOUR
    private val time = RequestTime(now = { now }, zoneOf = { PRAGUE }, ticks = emptyFlow())

    /**
     * A keeper whose deliveries run on the test's background scope. `runCurrent()`, not
     * `advanceUntilIdle()`, runs them: the latter does not wait for background work.
     */
    private fun TestScope.keeper() = RequestClockKeeper(repository, scheduler, time, backgroundScope)

    @Before
    fun setUp() {
        coEvery { repository.markDelivered(any(), any(), any()) } returns true
    }

    @Test
    fun `the answering phone plans its reminders in its own zone and records delivery once`() = runTest {
        val clock = clock()
        coEvery { repository.openFor(BOB, now - FOUR_DAYS) } returns listOf(clock)

        keeper().sweep(BOB, setOf(ALICE))

        val plan = ReminderPlan.plan(SENT, ANSWER_BY, now, PRAGUE)
        assertEquals("three reminders over three days", 3, plan.size)
        assertNotEquals(
            "Prague's evening is Kiritimati's night: the zone decides",
            ReminderPlan.plan(SENT, ANSWER_BY, now, TimeZone.of("Pacific/Kiritimati")),
            plan
        )
        coVerify(exactly = 1) { repository.markDelivered(clock, now, plan) }
        plan.forEach { verify(exactly = 1) { scheduler.scheduleReminder(clock.id, it) } }

        // The next sweep finds it delivered: no second mark, only the reminders still ahead.
        val delivered = clock.copy(deliveredAtMillis = now, remindersPlanned = plan.size, reminderTimesMillis = plan)
        now = plan.first() + MINUTE
        coEvery { repository.openFor(BOB, now - FOUR_DAYS) } returns listOf(delivered)
        keeper().sweep(BOB, setOf(ALICE))
        coVerify(exactly = 1) { repository.markDelivered(any(), any(), any()) }
    }

    @Test
    fun `a request received after its deadline is recorded as delivered with no reminders`() = runTest {
        val clock = clock()
        now = ANSWER_BY + HOUR
        coEvery { repository.openFor(BOB, now - FOUR_DAYS) } returns listOf(clock)

        keeper().sweep(BOB, setOf(ALICE))

        coVerify(exactly = 1) { repository.markDelivered(clock, now, emptyList()) }
        verify(exactly = 0) { scheduler.scheduleReminder(any(), any()) }
    }

    @Test
    fun `a delivery is never stamped before the clock was created`() = runTest {
        // This phone's clock runs a minute behind the server's.
        val clock = clock().copy(createdAtMillis = SENT)
        now = SENT - MINUTE

        val keeper = keeper()
        keeper.deliverSoon(BOB, listOf(clock))
        runCurrent()

        coVerify(exactly = 1) { repository.markDelivered(clock, SENT, any()) }
    }

    @Test
    fun `deliverSoon tries each clock once while an attempt is running, and again after one failed`() = runTest {
        val clock = clock()
        val gate = CompletableDeferred<Boolean>()
        coEvery { repository.markDelivered(clock, any(), any()) } coAnswers { gate.await() }
        val keeper = keeper()

        // The inbox, Home and the calendar each collect the same listener.
        repeat(3) { keeper.deliverSoon(BOB, listOf(clock)) }
        runCurrent()
        coVerify(exactly = 1) { repository.markDelivered(clock, any(), any()) }

        gate.complete(false)
        runCurrent()
        verify(exactly = 0) { scheduler.scheduleReminder(any(), any()) }

        coEvery { repository.markDelivered(clock, any(), any()) } returns true
        keeper.deliverSoon(BOB, listOf(clock))
        runCurrent()
        coVerify(exactly = 2) { repository.markDelivered(clock, any(), any()) }
        verify(atLeast = 1) { scheduler.scheduleReminder(clock.id, any()) }
    }

    @Test
    fun `deliverSoon leaves alone what is not this parent's to deliver`() = runTest {
        val keeper = keeper()
        keeper.deliverSoon(
            BOB,
            listOf(
                clock().copy(deliveredAtMillis = SENT),
                clock().copy(outcome = RequestClockOutcome.NO_ANSWER),
                clock().copy(id = "change_uidA_somebody-else's"),
                clockByBob()
            )
        )
        runCurrent()

        coVerify(exactly = 0) { repository.markDelivered(any(), any(), any()) }
    }

    @Test
    fun `every planned reminder still ahead is scheduled`() = runTest {
        val times = listOf(SENT + HOUR, SENT + 24 * HOUR, SENT + 48 * HOUR)
        val delivered = clock().copy(deliveredAtMillis = SENT, remindersPlanned = 3, reminderTimesMillis = times)
        now = SENT + 2 * HOUR
        coEvery { repository.openFor(BOB, now - FOUR_DAYS) } returns listOf(delivered)

        keeper().sweep(BOB, setOf(ALICE))

        verify(exactly = 0) { scheduler.scheduleReminder(delivered.id, times[0]) }
        verify(exactly = 1) { scheduler.scheduleReminder(delivered.id, times[1]) }
        verify(exactly = 1) { scheduler.scheduleReminder(delivered.id, times[2]) }
        coVerify(exactly = 0) { repository.markDelivered(any(), any(), any()) }
    }

    @Test
    fun `the proposer's clocks are left to the server - the sweep schedules and runs nothing for them`() = runTest {
        // MON-25: the deadline notice and the end run on the server, so a phone keeps no job for a
        // request it sent — open, past its deadline, due to end or closed.
        val open = clockByBob(ref = "open")
        val closedProposal = RequestDeadline(
            id = RequestDeadlineId.of(RequestKind.PROPOSAL, BOB, "2026-05-05T09:00:00", FAMILY),
            kind = RequestKind.PROPOSAL,
            ref = "2026-05-05T09:00:00",
            familyId = FAMILY,
            proposerUid = BOB,
            responderUid = ALICE,
            answerByMillis = SENT,
            outcome = RequestClockOutcome.NO_ANSWER
        )
        now = SENT + HOUR
        coEvery { repository.openFor(BOB, now - FOUR_DAYS) } returns listOf(
            open,
            clockByBob(ref = "past").copy(answerByMillis = now - HOUR),
            clockByBob(ref = "due").copy(answerByMillis = now - 3 * DAY),
            closedProposal
        )

        keeper().sweep(BOB, setOf(ALICE))

        verify { scheduler wasNot Called }
        coVerify(exactly = 0) { repository.markDelivered(any(), any(), any()) }
        coVerify(exactly = 0) { repository.get(any()) }
    }

    @Test
    fun `a clock of a family this parent is no longer in is left alone`() = runTest {
        now = SENT + HOUR
        coEvery { repository.openFor(BOB, now - FOUR_DAYS) } returns
            listOf(clock(), clockByBob().copy(answerByMillis = now - 3 * DAY))

        keeper().sweep(BOB, setOf(CAROL))

        coVerify(exactly = 0) { repository.markDelivered(any(), any(), any()) }
        verify { scheduler wasNot Called }
    }

    @Test
    fun `a clock whose id does not match its fields is left alone`() = runTest {
        // A clock addressed to Bob whose id spells another proposer, or one that names another
        // record: neither is delivered, and no reminder is armed for it.
        val forged = clock().copy(id = RequestDeadlineId.of(RequestKind.CHANGE, BOB, "ref-1", FAMILY))
        val wrongRef = clock().copy(ref = "another")
        now = SENT + HOUR
        coEvery { repository.openFor(BOB, now - FOUR_DAYS) } returns listOf(forged, wrongRef)

        keeper().sweep(BOB, setOf(ALICE))

        coVerify(exactly = 0) { repository.markDelivered(any(), any(), any()) }
        verify { scheduler wasNot Called }
    }

    /** A change request Alice sent Bob, three days to answer. */
    private fun clock() = RequestDeadline(
        id = RequestDeadlineId.of(RequestKind.CHANGE, ALICE, "ref-1", FAMILY),
        kind = RequestKind.CHANGE,
        ref = "ref-1",
        familyId = FAMILY,
        proposerUid = ALICE,
        responderUid = BOB,
        answerByMillis = ANSWER_BY,
        createdAtMillis = SENT
    )

    /** A change request Bob sent Alice. */
    private fun clockByBob(ref: String = "ref-1") = RequestDeadline(
        id = RequestDeadlineId.of(RequestKind.CHANGE, BOB, ref, FAMILY),
        kind = RequestKind.CHANGE,
        ref = ref,
        familyId = FAMILY,
        proposerUid = BOB,
        responderUid = ALICE,
        answerByMillis = ANSWER_BY,
        createdAtMillis = SENT
    )

    private companion object {
        const val ALICE = "uidA"
        const val BOB = "uidB"
        const val CAROL = "uidC"
        const val FAMILY = "uidA__uidB"
        const val MINUTE = 60_000L
        const val HOUR = 60 * MINUTE
        const val DAY = 24 * HOUR
        const val FOUR_DAYS = 4 * DAY

        /** 2026-05-05T16:53:20Z. */
        const val SENT = 1_778_000_000_000L
        const val ANSWER_BY = SENT + 3 * DAY
        val PRAGUE: TimeZone = TimeZone.of("Europe/Prague")
    }
}
