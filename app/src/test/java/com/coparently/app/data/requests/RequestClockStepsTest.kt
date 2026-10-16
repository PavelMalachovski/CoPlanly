package com.coparently.app.data.requests

import com.coparently.app.domain.activity.ActivityEntityType
import com.coparently.app.domain.changerequests.RequestClock
import com.coparently.app.domain.changerequests.RequestClockOutcome
import com.coparently.app.domain.changerequests.RequestDeadline
import com.coparently.app.domain.changerequests.RequestDeadlineId
import com.coparently.app.domain.changerequests.RequestKind
import com.coparently.app.domain.notification.RequestClockScheduler
import com.coparently.app.domain.repository.RequestDeadlineRepository
import io.mockk.clearMocks
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.coVerifyOrder
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import java.time.ZoneOffset

/**
 * [RequestClockSteps]: the three steps a request's clock runs when their time comes (phase 3a,
 * spec §4.3–§4.4) — a reminder on the answering parent's phone, the deadline and the end on the
 * proposer's.
 *
 * Each step reads the clock and the request's record again, so a request answered, withdrawn or
 * replaced meanwhile finds nothing to do, and every card and push goes to the clock's family, never
 * the family on screen (spec §5.6). A clock or a record that cannot be read is tried again, never
 * taken as "nothing to do".
 */
class RequestClockStepsTest {

    private val repository = mockk<RequestDeadlineRepository>()
    private val records = mockk<RequestRecords>()
    private val notices = mockk<RequestNotices>(relaxed = true)
    private val telling = mockk<RequestTelling>(relaxed = true)
    private val scheduler = mockk<RequestClockScheduler>(relaxed = true)
    private var now = SENT
    private val time = RequestTime(now = { now }, zoneOf = { ZoneOffset.UTC }, ticks = emptyFlow())

    private val steps = RequestClockSteps(repository, records, notices, telling, scheduler, time)

    @Before
    fun setUp() {
        coEvery { repository.reportReminderShown(any()) } returns true
        coEvery { repository.close(any()) } returns true
        coEvery { records.withdrawUnansweredProposal(any()) } returns true
    }

    // --- Reminders ---------------------------------------------------------------------------

    @Test
    fun `a reminder posts and counts while the request is open`() = runTest {
        val clock = clock(RequestKind.CHANGE)
        signedInAs(BOB)
        reads(clock, RecordStatus.PENDING)
        now = SENT + 10 * HOUR

        assertEquals(StepResult.DONE, steps.reminder(clock.id, plannedAtMillis = now - 5 * MINUTE))

        coVerifyOrder {
            notices.remind(clock)
            repository.reportReminderShown(clock)
        }
    }

    // The plan's name, less the comma before "or": with it the declaration is a character over the
    // line limit, and a backticked name cannot be wrapped.
    @Test
    fun `a reminder posts nothing for an answered, past-deadline or ended-family request or another account's clock`() =
        runTest {
            val clock = clock(RequestKind.CHANGE)
            now = SENT + 10 * HOUR

            signedInAs(BOB)
            reads(clock, RecordStatus.ANSWERED)
            assertEquals("answered", StepResult.DONE, steps.reminder(clock.id, now))

            reads(clock, RecordStatus.GONE)
            assertEquals(
                "the family has ended, or the request was replaced",
                StepResult.DONE,
                steps.reminder(clock.id, now)
            )

            reads(clock, RecordStatus.PENDING)
            now = clock.answerByMillis + MINUTE
            assertEquals("past the deadline", StepResult.DONE, steps.reminder(clock.id, now))

            now = SENT + 10 * HOUR
            signedInAs(ALICE)
            assertEquals("the proposer's own phone", StepResult.DONE, steps.reminder(clock.id, now))
            signedInAs("uidZ")
            assertEquals("another account signed in since", StepResult.DONE, steps.reminder(clock.id, now))

            coVerify(exactly = 0) { notices.remind(any()) }
            coVerify(exactly = 0) { repository.reportReminderShown(any()) }
        }

    @Test
    fun `a reminder more than half an hour late posts nothing and counts nothing`() = runTest {
        val clock = clock(RequestKind.CHANGE)
        signedInAs(BOB)
        reads(clock, RecordStatus.PENDING)
        now = SENT + 10 * HOUR

        assertEquals(StepResult.DONE, steps.reminder(clock.id, plannedAtMillis = now - 31 * MINUTE))
        coVerify(exactly = 0) { notices.remind(any()) }
        coVerify(exactly = 0) { repository.reportReminderShown(any()) }

        assertEquals("half an hour late is still on time", StepResult.DONE, steps.reminder(clock.id, now - 30 * MINUTE))
        coVerify(exactly = 1) { notices.remind(clock) }
    }

    @Test
    fun `a reminder whose clock or record cannot be read is tried again`() = runTest {
        val clock = clock(RequestKind.SWAP)
        signedInAs(BOB)
        now = SENT + 10 * HOUR
        coEvery { repository.get(clock.id) } returns null
        assertEquals(StepResult.RETRY, steps.reminder(clock.id, now))

        reads(clock, RecordStatus.UNKNOWN)
        assertEquals(StepResult.RETRY, steps.reminder(clock.id, now))
        coVerify(exactly = 0) { notices.remind(any()) }
    }

    // --- The deadline ------------------------------------------------------------------------

    @Test
    fun `at the deadline the proposer is told once and the end is scheduled`() = runTest {
        val clock = clock(RequestKind.SWAP)
        signedInAs(ALICE)
        reads(clock, RecordStatus.PENDING)
        now = clock.answerByMillis + MINUTE

        assertEquals(StepResult.DONE, steps.deadline(clock.id))

        coVerify(exactly = 1) { notices.deadlinePassed(clock) }
        verify(exactly = 1) { scheduler.scheduleEnd(clock.id, RequestClock.endMillis(clock)) }
        coVerify(exactly = 0) { repository.close(any()) }
        coVerify(exactly = 0) { telling.unanswered(any(), any()) }
    }

    @Test
    fun `at the deadline an answered request tells nobody`() = runTest {
        val clock = clock(RequestKind.SWAP)
        signedInAs(ALICE)
        reads(clock, RecordStatus.ANSWERED)
        now = clock.answerByMillis + MINUTE

        assertEquals(StepResult.DONE, steps.deadline(clock.id))

        coVerify(exactly = 0) { notices.deadlinePassed(any()) }
        verify(exactly = 0) { scheduler.scheduleEnd(any(), any()) }
    }

    @Test
    fun `with no last chance possible the deadline goes straight to the end`() = runTest {
        // A cap an hour after the deadline leaves no room for a last chance: the request ends at
        // the deadline itself.
        val clock = clock(RequestKind.CHANGE).copy(capMillis = ANSWER_BY + HOUR)
        signedInAs(ALICE)
        reads(clock, RecordStatus.PENDING)
        now = clock.answerByMillis + MINUTE

        assertEquals(StepResult.DONE, steps.deadline(clock.id))

        coVerify(exactly = 0) { notices.deadlinePassed(any()) }
        coVerifyOrder {
            repository.close(clock)
            notices.noAnswer(clock)
            telling.unanswered(clock, CARD)
        }
    }

    @Test
    fun `a deadline that fires on the answering phone, or before its time, does nothing`() = runTest {
        val clock = clock(RequestKind.CHANGE)
        reads(clock, RecordStatus.PENDING)
        now = clock.answerByMillis + MINUTE
        signedInAs(BOB)
        assertEquals(StepResult.DONE, steps.deadline(clock.id))

        // An hour early: the job cannot re-enqueue its own name while it runs; the keeper's next
        // sweep schedules it again.
        signedInAs(ALICE)
        now = clock.answerByMillis - HOUR
        assertEquals(StepResult.DONE, steps.deadline(clock.id))
        // Seconds early: a short retry is enough.
        now = clock.answerByMillis - 10_000L
        assertEquals(StepResult.RETRY, steps.deadline(clock.id))

        coVerify(exactly = 0) { notices.deadlinePassed(any()) }
    }

    // --- The end -----------------------------------------------------------------------------

    @Test
    fun `the end closes, tells, posts its card to the clock's family and withdraws a proposal`() = runTest {
        val clock = clock(RequestKind.PROPOSAL)
        val card = CardSubject(ActivityEntityType.CUSTODY_PROPOSAL, FAMILY, "")
        signedInAs(ALICE)
        reads(clock, RecordStatus.PENDING, card)
        now = RequestClock.endMillis(clock) + MINUTE

        assertEquals(StepResult.DONE, steps.end(clock.id))

        coVerifyOrder {
            repository.close(clock)
            notices.noAnswer(clock)
            // The clock carries its family; the telling names its responder from it.
            telling.unanswered(clock, card)
            records.withdrawUnansweredProposal(clock)
        }
    }

    @Test
    fun `the end leaves an answered request open`() = runTest {
        val clock = clock(RequestKind.CHANGE)
        signedInAs(ALICE)
        reads(clock, RecordStatus.ANSWERED)
        now = RequestClock.endMillis(clock) + MINUTE

        assertEquals(StepResult.DONE, steps.end(clock.id))

        coVerify(exactly = 0) { repository.close(any()) }
        coVerify(exactly = 0) { notices.noAnswer(any()) }
        coVerify(exactly = 0) { telling.unanswered(any(), any()) }
    }

    @Test
    fun `the end retries when the record cannot be read`() = runTest {
        val clock = clock(RequestKind.SWAP)
        signedInAs(ALICE)
        now = RequestClock.endMillis(clock) + MINUTE
        coEvery { repository.get(clock.id) } returns null
        assertEquals("a clock that cannot be read", StepResult.RETRY, steps.end(clock.id))

        reads(clock, RecordStatus.UNKNOWN)
        assertEquals(StepResult.RETRY, steps.end(clock.id))

        // A closure the server refused right after the deadline is "try again shortly".
        reads(clock, RecordStatus.PENDING)
        coEvery { repository.close(clock) } returns false
        assertEquals(StepResult.RETRY, steps.end(clock.id))

        coVerify(exactly = 0) { notices.noAnswer(any()) }
        coVerify(exactly = 0) { telling.unanswered(any(), any()) }
    }

    @Test
    fun `a closed proposal still pending is withdrawn on the next try`() = runTest {
        val clock = clock(RequestKind.PROPOSAL)
        signedInAs(ALICE)
        reads(clock, RecordStatus.PENDING, CARD)
        now = RequestClock.endMillis(clock) + MINUTE
        coEvery { records.withdrawUnansweredProposal(clock) } returns false

        assertEquals("the withdrawal failed", StepResult.RETRY, steps.end(clock.id))

        val closed = clock.copy(outcome = RequestClockOutcome.NO_ANSWER, closedAtMillis = now)
        clearMocks(notices, telling, answers = false)
        reads(closed, RecordStatus.PENDING, CARD)
        coEvery { records.withdrawUnansweredProposal(closed) } returns true

        assertEquals(StepResult.DONE, steps.end(closed.id))
        coVerify(exactly = 1) { records.withdrawUnansweredProposal(closed) }
        coVerify(exactly = 1) { repository.close(any()) }
        coVerify(exactly = 0) { notices.noAnswer(any()) }
        coVerify(exactly = 0) { telling.unanswered(any(), any()) }

        // Once it is no longer pending, there is nothing left to do.
        reads(closed, RecordStatus.GONE)
        assertEquals(StepResult.DONE, steps.end(closed.id))
        coVerify(exactly = 1) { records.withdrawUnansweredProposal(closed) }
    }

    @Test
    fun `an end that fires early reschedules itself`() = runTest {
        // A last chance was given after this end job was scheduled: the end moved.
        val clock = clock(RequestKind.CHANGE).copy(lastChanceUntilMillis = ANSWER_BY + 20 * HOUR)
        signedInAs(ALICE)
        reads(clock, RecordStatus.PENDING)
        now = ANSWER_BY + 2 * HOUR

        assertEquals(StepResult.DONE, steps.end(clock.id))

        verify(exactly = 1) { scheduler.scheduleEnd(clock.id, ANSWER_BY + 20 * HOUR) }
        coVerify(exactly = 0) { repository.close(any()) }

        // Seconds before its time, a short retry is enough.
        now = ANSWER_BY + 20 * HOUR - 10_000L
        assertEquals(StepResult.RETRY, steps.end(clock.id))
        coVerify(exactly = 0) { repository.close(any()) }
    }

    private fun signedInAs(uid: String) {
        every { repository.signedInUid() } returns uid
    }

    private fun reads(clock: RequestDeadline, status: RecordStatus, card: CardSubject? = CARD) {
        coEvery { repository.get(clock.id) } returns clock
        coEvery { records.look(clock) } returns RecordLook(status, card)
    }

    private fun clock(kind: RequestKind): RequestDeadline {
        val ref = if (kind == RequestKind.PROPOSAL) "2026-05-05T09:00:00" else "ref-1"
        return RequestDeadline(
            id = RequestDeadlineId.of(kind, ALICE, ref, FAMILY),
            kind = kind,
            ref = ref,
            familyId = FAMILY,
            proposerUid = ALICE,
            responderUid = BOB,
            answerByMillis = ANSWER_BY,
            createdAtMillis = SENT
        )
    }

    private companion object {
        const val ALICE = "uidA"
        const val BOB = "uidB"
        const val FAMILY = "uidA__uidB"
        const val MINUTE = 60_000L
        const val HOUR = 60 * MINUTE
        const val SENT = 1_778_000_000_000L
        const val ANSWER_BY = SENT + 72 * HOUR
        val CARD = CardSubject(ActivityEntityType.CHANGE_REQUEST, "ev-9", "Dentist")
    }
}
