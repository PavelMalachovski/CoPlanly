package com.coparently.app.data.requests

import com.coparently.app.domain.changerequests.ReminderPlan
import com.coparently.app.domain.changerequests.RequestClock
import com.coparently.app.domain.changerequests.RequestClockOutcome
import com.coparently.app.domain.changerequests.RequestDeadline
import com.coparently.app.domain.changerequests.RequestDeadlineId
import com.coparently.app.domain.changerequests.RequestKind
import com.coparently.app.domain.notification.RequestClockScheduler
import com.coparently.app.domain.repository.RequestDeadlineRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.ZoneId

/**
 * [RequestClockKeeper]: what each full sync, and each listener snapshot while the app runs, does
 * with this parent's open clocks (phase 3a, spec §4.3, §4.5 and §5.3).
 *
 * The answering phone delivers a clock once: it plans the reminders in its own zone, records the
 * delivery with the plan, and schedules them. The proposer's phone keeps the deadline and the end
 * scheduled, and runs an end that is already due. A clock of a family this parent has left, or one
 * whose id does not spell its own fields, is left alone.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class RequestClockKeeperTest {

    private val repository = mockk<RequestDeadlineRepository>()
    private val steps = mockk<RequestClockSteps>()
    private val scheduler = mockk<RequestClockScheduler>(relaxed = true)
    private var now = SENT + HOUR
    private val time = RequestTime(now = { now }, zoneOf = { PRAGUE }, ticks = emptyFlow())

    /**
     * A keeper whose deliveries run on the test's background scope. `runCurrent()`, not
     * `advanceUntilIdle()`, runs them: the latter does not wait for background work.
     */
    private fun TestScope.keeper() = RequestClockKeeper(repository, steps, scheduler, time, backgroundScope)

    @Before
    fun setUp() {
        coEvery { repository.markDelivered(any(), any(), any()) } returns true
        coEvery { steps.end(any()) } returns StepResult.DONE
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
            ReminderPlan.plan(SENT, ANSWER_BY, now, ZoneId.of("Pacific/Kiritimati")),
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
    fun `the proposer's deadline and end are scheduled, and an end already due runs now`() = runTest {
        val open = clockByBob(ref = "open")
        val pastDeadline = clockByBob(ref = "past")
        val due = clockByBob(ref = "due")
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
        val closedChange = clockByBob(ref = "closed").copy(outcome = RequestClockOutcome.NO_ANSWER)
        now = SENT + HOUR
        coEvery { repository.openFor(BOB, now - FOUR_DAYS) } returns listOf(
            open,
            pastDeadline.copy(answerByMillis = now - HOUR),
            due.copy(answerByMillis = now - 3 * DAY),
            closedProposal,
            closedChange
        )

        keeper().sweep(BOB, setOf(ALICE))

        verify(exactly = 1) { scheduler.scheduleDeadline(open.id, open.answerByMillis) }
        verify(exactly = 1) { scheduler.scheduleEnd(open.id, RequestClock.endMillis(open)) }
        verify(exactly = 0) { scheduler.scheduleDeadline(pastDeadline.id, any()) }
        verify(exactly = 1) { scheduler.scheduleEnd(pastDeadline.id, any()) }
        coVerify(exactly = 1) { steps.end(due.id) }
        verify(exactly = 0) { scheduler.scheduleEnd(due.id, any()) }
        coVerify(exactly = 1) { steps.end(closedProposal.id) }
        coVerify(exactly = 0) { steps.end(closedChange.id) }
        verify(exactly = 0) { scheduler.scheduleEnd(closedChange.id, any()) }
    }

    @Test
    fun `an end whose server read never answers leaves the sweep within its bound and closes nothing`() = runTest {
        // The sweep runs inside performFullSync, so an end that hangs on the network held the whole
        // sync (final review M-3). Real steps, a server read that never answers: the sweep gives the
        // step up at its bound, closes nothing and leaves the end to its own job.
        val records = mockk<RequestRecords>()
        val notices = mockk<RequestNotices>(relaxed = true)
        val telling = mockk<RequestTelling>(relaxed = true)
        val realSteps = RequestClockSteps(repository, records, notices, telling, scheduler, time)
        val due = clockByBob()
        val endAt = RequestClock.endMillis(due)
        now = endAt + MINUTE
        coEvery { repository.openFor(BOB, now - FOUR_DAYS) } returns listOf(due)
        every { repository.signedInUid() } returns BOB
        coEvery { repository.get(due.id) } returns due
        coEvery { repository.close(any()) } returns true
        coEvery { records.lookOnServer(due) } coAnswers { awaitCancellation() }

        val started = currentTime
        withTimeout(TEN_MINUTES) {
            RequestClockKeeper(repository, realSteps, scheduler, time, backgroundScope).sweep(BOB, setOf(ALICE))
        }

        assertTrue(currentTime - started <= RequestClockKeeper.SWEEP_STEP_TIMEOUT_MILLIS)
        coVerify(exactly = 0) { repository.close(any()) }
        coVerify(exactly = 0) { notices.noAnswer(any()) }
        coVerify(exactly = 0) { telling.unanswered(any(), any()) }
        verify(exactly = 1) { scheduler.scheduleEnd(due.id, endAt) }
    }

    @Test
    fun `a clock of a family this parent is no longer in is left alone`() = runTest {
        now = SENT + HOUR
        coEvery { repository.openFor(BOB, now - FOUR_DAYS) } returns
            listOf(clock(), clockByBob().copy(answerByMillis = now - 3 * DAY))

        keeper().sweep(BOB, setOf(CAROL))

        coVerify(exactly = 0) { repository.markDelivered(any(), any(), any()) }
        coVerify(exactly = 0) { steps.end(any()) }
        verify(exactly = 0) { scheduler.scheduleReminder(any(), any()) }
        verify(exactly = 0) { scheduler.scheduleDeadline(any(), any()) }
        verify(exactly = 0) { scheduler.scheduleEnd(any(), any()) }
    }

    @Test
    fun `a clock whose id does not match its fields is left alone`() = runTest {
        // Alice could write a clock "for" one of Bob's requests under her own id: it must not set
        // Bob's deadline or close his request.
        val forged = clockByBob().copy(id = RequestDeadlineId.of(RequestKind.CHANGE, ALICE, "ref-1", FAMILY))
        val wrongRef = clock().copy(ref = "another")
        now = SENT + HOUR
        coEvery { repository.openFor(BOB, now - FOUR_DAYS) } returns
            listOf(forged.copy(answerByMillis = now - 3 * DAY), wrongRef)

        keeper().sweep(BOB, setOf(ALICE))

        coVerify(exactly = 0) { repository.markDelivered(any(), any(), any()) }
        coVerify(exactly = 0) { steps.end(any()) }
        verify(exactly = 0) { scheduler.scheduleEnd(any(), any()) }
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
        const val TEN_MINUTES = 10 * MINUTE

        /** 2026-05-05T16:53:20Z. */
        const val SENT = 1_778_000_000_000L
        const val ANSWER_BY = SENT + 3 * DAY
        val PRAGUE: ZoneId = ZoneId.of("Europe/Prague")
    }
}
