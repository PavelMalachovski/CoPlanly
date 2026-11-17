package com.coparently.app.data.requests

import com.coparently.app.domain.activity.ActivityEntityType
import com.coparently.app.domain.changerequests.RequestDeadline
import com.coparently.app.domain.changerequests.RequestDeadlineId
import com.coparently.app.domain.changerequests.RequestKind
import com.coparently.app.domain.repository.RequestDeadlineRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.coVerifyOrder
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.TimeZone
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneOffset

/**
 * [RequestClockSteps]: the step a request's clock runs on a phone when its time comes (phase 3a,
 * spec §4.3) — a reminder on the answering parent's phone. The deadline notice and the end are the
 * server's since MON-25, tested in `functions/test/`.
 *
 * The step reads the clock and the request's record again, so a request answered, withdrawn or
 * replaced meanwhile finds nothing to do. A clock or a record that cannot be read is tried again,
 * never taken as "nothing to do".
 */
class RequestClockStepsTest {

    private val repository = mockk<RequestDeadlineRepository>()
    private val records = mockk<RequestRecords>()
    private val notices = mockk<RequestClockNotices>(relaxed = true)
    private var now = SENT
    private val time = RequestTime(now = { now }, zoneOf = { TimeZone.UTC }, ticks = emptyFlow())

    private val steps = RequestClockSteps(repository, records, notices, time)

    @Before
    fun setUp() {
        coEvery { repository.reportReminderShown(any()) } returns true
        coEvery { notices.remind(any()) } returns true
    }

    // --- Reminders ---------------------------------------------------------------------------

    @Test
    fun `a reminder posts and counts while the request is open`() = runTest {
        val clock = clock(RequestKind.CHANGE)
        signedInAs(BOB)
        reads(clock, RecordStatus.PENDING)
        now = DAYTIME

        assertEquals(StepResult.DONE, steps.reminder(clock.id, plannedAtMillis = now - 5 * MINUTE))

        coVerifyOrder {
            notices.remind(clock)
            repository.reportReminderShown(clock)
        }
    }

    @Test
    fun `a reminder nothing showed is not counted`() = runTest {
        // Notification audit, 9 November 2026: notifications off on the answering phone, and the
        // proposer's card still said "Reminders: 2 of 3".
        val clock = clock(RequestKind.CHANGE)
        signedInAs(BOB)
        reads(clock, RecordStatus.PENDING)
        now = DAYTIME
        coEvery { notices.remind(clock) } returns false

        assertEquals(StepResult.DONE, steps.reminder(clock.id, plannedAtMillis = now - 5 * MINUTE))

        coVerify(exactly = 0) { repository.reportReminderShown(any()) }
    }

    // The plan's name, less the comma before "or": with it the declaration is a character over the
    // line limit, and a backticked name cannot be wrapped.
    @Test
    fun `a reminder posts nothing for an answered, past-deadline or ended-family request or another account's clock`() =
        runTest {
            val clock = clock(RequestKind.CHANGE)
            now = DAYTIME

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

            now = DAYTIME
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
        now = DAYTIME

        assertEquals(StepResult.DONE, steps.reminder(clock.id, plannedAtMillis = now - 31 * MINUTE))
        coVerify(exactly = 0) { notices.remind(any()) }
        coVerify(exactly = 0) { repository.reportReminderShown(any()) }

        assertEquals("half an hour late is still on time", StepResult.DONE, steps.reminder(clock.id, now - 30 * MINUTE))
        coVerify(exactly = 1) { notices.remind(clock) }
    }

    @Test
    fun `a reminder that fires in the answering parent's quiet hours posts nothing and counts nothing`() = runTest {
        // Planned for 20:50, outside the night, and held back by Doze past 21:00: the plan's quiet
        // hours are checked again at fire time, on this phone's clock (final review M-1).
        val clock = clock(RequestKind.CHANGE)
        signedInAs(BOB)
        reads(clock, RecordStatus.PENDING)
        now = utc(2026, 5, 6, 21, 30)

        assertEquals(StepResult.DONE, steps.reminder(clock.id, plannedAtMillis = now - 25 * MINUTE))
        coVerify(exactly = 0) { notices.remind(any()) }
        coVerify(exactly = 0) { repository.reportReminderShown(any()) }

        now = utc(2026, 5, 6, 20, 30)
        assertEquals("the evening before is not night", StepResult.DONE, steps.reminder(clock.id, now - 5 * MINUTE))
        coVerify(exactly = 1) { notices.remind(clock) }
        coVerify(exactly = 1) { repository.reportReminderShown(clock) }
    }

    @Test
    fun `a reminder whose clock or record cannot be read is tried again`() = runTest {
        val clock = clock(RequestKind.SWAP)
        signedInAs(BOB)
        now = DAYTIME
        coEvery { repository.get(clock.id) } returns null
        assertEquals(StepResult.RETRY, steps.reminder(clock.id, now))

        reads(clock, RecordStatus.UNKNOWN)
        assertEquals(StepResult.RETRY, steps.reminder(clock.id, now))
        coVerify(exactly = 0) { notices.remind(any()) }
    }

    private fun signedInAs(uid: String) {
        every { repository.signedInUid() } returns uid
    }

    /** [clock] is read back, and its record says [status] on this phone. */
    private fun reads(clock: RequestDeadline, status: RecordStatus, card: CardSubject? = CARD) {
        coEvery { repository.get(clock.id) } returns clock
        coEvery { records.look(clock) } returns RecordLook(status, card)
    }

    private fun utc(year: Int, month: Int, day: Int, hour: Int, minute: Int): Long =
        LocalDateTime.of(year, month, day, hour, minute).toInstant(ZoneOffset.UTC).toEpochMilli()

    private fun clock(kind: RequestKind): RequestDeadline {
        val ref = when (kind) {
            RequestKind.PROPOSAL -> "2026-05-05T09:00:00"
            RequestKind.SPLIT -> "1777990000000"
            RequestKind.SWAP, RequestKind.CHANGE, RequestKind.EVENT -> "ref-1"
        }
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

        /** 12:53 UTC the day after [SENT]: inside the window and outside the quiet hours. */
        const val DAYTIME = SENT + 20 * HOUR
        val CARD = CardSubject(ActivityEntityType.CHANGE_REQUEST, "ev-9", "Dentist")
    }
}
