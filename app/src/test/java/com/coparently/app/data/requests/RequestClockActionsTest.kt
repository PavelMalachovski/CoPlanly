package com.coparently.app.data.requests

import com.coparently.app.domain.activity.ActivityEntityType
import com.coparently.app.domain.changerequests.RequestClock
import com.coparently.app.domain.changerequests.RequestDeadline
import com.coparently.app.domain.changerequests.RequestDeadlineId
import com.coparently.app.domain.changerequests.RequestKind
import com.coparently.app.domain.notification.RequestClockScheduler
import com.coparently.app.domain.repository.RequestDeadlineRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.coVerifyOrder
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.ZoneOffset

/**
 * [RequestClockActions]: the two things a parent does to a request's clock from a card (phase 3a,
 * spec §4.4, §4.5) — the answering parent opening it, once, and the proposer giving one last chance
 * while the button is offered.
 *
 * Both read the clock again rather than trusting the card that was drawn, and a last chance asks the
 * server whether the request still waits, because this phone's copy may not yet hold the answer.
 */
class RequestClockActionsTest {

    private val repository = mockk<RequestDeadlineRepository>()
    private val records = mockk<RequestRecords>()
    private val telling = mockk<RequestTelling>(relaxed = true)
    private val scheduler = mockk<RequestClockScheduler>(relaxed = true)
    private var now = SENT
    private val time = RequestTime(now = { now }, zoneOf = { ZoneOffset.UTC }, ticks = emptyFlow())

    private val actions = RequestClockActions(repository, records, telling, scheduler, time)

    @Before
    fun setUp() {
        coEvery { repository.markSeen(any(), any()) } returns true
        coEvery { repository.giveLastChance(any(), any()) } returns true
    }

    @Test
    fun `the answering parent marks a request seen once`() = runTest {
        val clock = clock()
        signedInAs(BOB)
        coEvery { repository.get(clock.id) } returns clock
        now = SENT + 2 * HOUR

        assertTrue(actions.markSeen(clock.id))
        coVerify(exactly = 1) { repository.markSeen(clock, SENT + 2 * HOUR) }

        // Already seen: nothing is written again.
        coEvery { repository.get(clock.id) } returns clock.copy(seenAtMillis = SENT + 2 * HOUR)
        assertFalse(actions.markSeen(clock.id))
        coVerify(exactly = 1) { repository.markSeen(any(), any()) }

        // The proposer opening their own request is not "seen".
        coEvery { repository.get(clock.id) } returns clock
        signedInAs(ALICE)
        assertFalse(actions.markSeen(clock.id))
        coVerify(exactly = 1) { repository.markSeen(any(), any()) }

        // A slow phone never reports "seen" before the clock was created.
        signedInAs(BOB)
        now = SENT - 3 * MINUTE
        assertTrue(actions.markSeen(clock.id))
        coVerify(exactly = 1) { repository.markSeen(clock, SENT) }
    }

    @Test
    fun `the proposer gives one last chance — the clock, the push, the card, the end, in that order`() = runTest {
        val clock = clock()
        signedInAs(ALICE)
        coEvery { repository.get(clock.id) } returns clock
        coEvery { records.lookOnServer(clock) } returns RecordLook(RecordStatus.PENDING, CARD)
        now = ANSWER_BY + 2 * HOUR
        val until = now + RequestClock.LAST_CHANCE_MILLIS

        assertTrue(actions.giveLastChance(clock.id))

        coVerifyOrder {
            repository.giveLastChance(clock, until)
            telling.lastChance(clock, CARD, until)
            scheduler.scheduleEnd(clock.id, until)
        }
    }

    @Test
    fun `no last chance once the button's time is over, for an answered request, or for the answering parent`() =
        runTest {
            val clock = clock()
            coEvery { repository.get(clock.id) } returns clock
            coEvery { records.lookOnServer(clock) } returns RecordLook(RecordStatus.PENDING, CARD)

            signedInAs(ALICE)
            now = RequestClock.buttonUntilMillis(clock)
            assertFalse("the button's time is over", actions.giveLastChance(clock.id))

            now = ANSWER_BY - HOUR
            assertFalse("before the deadline", actions.giveLastChance(clock.id))

            now = ANSWER_BY + HOUR
            coEvery { records.lookOnServer(clock) } returns RecordLook(RecordStatus.ANSWERED, CARD)
            assertFalse("answered on the server", actions.giveLastChance(clock.id))

            coEvery { records.lookOnServer(clock) } returns RecordLook(RecordStatus.UNKNOWN, null)
            assertFalse("the server cannot be reached", actions.giveLastChance(clock.id))

            coEvery { records.lookOnServer(clock) } returns RecordLook(RecordStatus.PENDING, CARD)
            coEvery { repository.get(clock.id) } returns clock.copy(lastChanceUntilMillis = ANSWER_BY + 24 * HOUR)
            assertFalse("given once already", actions.giveLastChance(clock.id))

            coEvery { repository.get(clock.id) } returns clock
            signedInAs(BOB)
            assertFalse("the answering parent", actions.giveLastChance(clock.id))

            coVerify(exactly = 0) { repository.giveLastChance(any(), any()) }
            coVerify(exactly = 0) { telling.lastChance(any(), any(), any()) }
            verify(exactly = 0) { scheduler.scheduleEnd(any(), any()) }

            // A write the rules refuse — a phone clock running fast — tells nobody and schedules nothing.
            signedInAs(ALICE)
            coEvery { repository.giveLastChance(any(), any()) } returns false
            assertFalse("refused", actions.giveLastChance(clock.id))
            coVerify(exactly = 0) { telling.lastChance(any(), any(), any()) }
            verify(exactly = 0) { scheduler.scheduleEnd(any(), any()) }
        }

    private fun signedInAs(uid: String) {
        every { repository.signedInUid() } returns uid
    }

    private fun clock(): RequestDeadline = RequestDeadline(
        id = RequestDeadlineId.of(RequestKind.CHANGE, ALICE, "ref-1", FAMILY),
        kind = RequestKind.CHANGE,
        ref = "ref-1",
        familyId = FAMILY,
        proposerUid = ALICE,
        responderUid = BOB,
        answerByMillis = ANSWER_BY,
        createdAtMillis = SENT
    )

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
