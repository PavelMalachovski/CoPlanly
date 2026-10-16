package com.coparently.app.e2e

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.coparently.app.data.notification.RequestClockWork
import com.coparently.app.data.repository.PatternSubmission
import com.coparently.app.data.requests.StepResult
import com.coparently.app.domain.activity.ActivityKind
import com.coparently.app.domain.changerequests.AnswerByPick
import com.coparently.app.domain.changerequests.ClocksAt
import com.coparently.app.domain.changerequests.RequestClockOutcome
import com.coparently.app.domain.changerequests.RequestDeadline
import com.coparently.app.domain.changerequests.RequestDeadlineId
import com.coparently.app.domain.changerequests.RequestInbox
import com.coparently.app.domain.changerequests.RequestKind
import com.coparently.app.domain.changerequests.RequestOutcome
import com.coparently.app.domain.changerequests.RequestScope
import com.coparently.app.domain.changerequests.RequestStart
import com.coparently.app.domain.changerequests.RequestSubject
import com.coparently.app.domain.chat.ConversationKey
import com.coparently.app.domain.custody.CustodyKey
import com.coparently.app.domain.custody.DayOverride
import com.coparently.app.domain.custody.DayOverrideTransition
import com.coparently.app.domain.custody.SharedCustody
import com.coparently.app.domain.model.CustodyModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.UUID

/**
 * Request deadlines between two phones (phase 3a, "no request is left hanging"): a clock started
 * with an offer, received and opened on the other phone, given a last chance after its deadline,
 * and ended — answered in time, or with nobody answering.
 *
 * Every clock write goes through the production `RequestDeadlineRepositoryImpl` against the real
 * `firestore.rules`, and every step through the production `RequestClockKeeper`,
 * `RequestClockActions` and `RequestClockSteps` of the phone that runs it. What WorkManager would do
 * at a step's time the test does itself: each phone's [RecordingClockScheduler] records the steps,
 * and the test runs the one it wants when it wants it.
 *
 * What it proves:
 * - **An offer with a deadline starts its clock, and its push says when** (`answerBy`), and the
 *   answering phone records the delivery with the reminders it planned, and schedules those.
 * - **Opening the request is recorded** on the proposer's side.
 * - **The last chance is given only after the deadline**, reaches the answering parent as the
 *   `request_last_chance` push read back from `notification_queue`, and an answer under it wins:
 *   the end closes nothing, and the inbox's history says Accepted.
 * - **A proposal nobody answers is closed as "No answer"** at its deadline when the cap leaves no
 *   room for a last chance (ruling 2), withdrawn so it stops blocking the co-parent, posted to the
 *   thread as a `REQUEST_UNANSWERED` card, listed as No answer in the co-parent's history — and the
 *   co-parent can propose again.
 *
 * **Short deadlines, started directly.** A preset is three days at the least, so the clocks this
 * test lets run out are written straight through `RequestDeadlineRepository.start`, with a deadline
 * [SHORT_DEADLINE_MILLIS] ahead. `firestore.rules` asks only that a deadline be after the server's
 * time and that a cap come after it, so the start goes through the same rule an offer's does.
 *
 * What it cannot: the reminders' and the deadline's jobs firing on their own (WorkManager's time),
 * the notifications and cards as drawn, and FCM's delivery of the push.
 */
@RunWith(AndroidJUnit4::class)
class TwoParentRequestDeadlinesTest : TwoParentTest() {

    @Test
    fun aSwapWithADeadlineIsDeliveredOpenedGivenALastChanceAndAnswered() = runBlocking<Unit> {
        val agreed = agreeFirstPattern()
        val familyId = CustodyKey.of(alice.uid, bob.uid)

        EmulatorEnvironment.step("deadlines: Alice offers a day with the default deadline")
        val firstDay = LocalDate.now().plusDays(FIRST_OFFER_DAYS_AHEAD).toString()
        val firstGroup = offerDay(firstDay, otherSlotOn(agreed, firstDay), AnswerByPick.DEFAULT)
        val firstId = RequestDeadlineId.of(RequestKind.SWAP, alice.uid, firstGroup, familyId)
        val started = awaitClock(alice, firstId) { true }
        val offered = EmulatorEnvironment.awaitQueuedPush(bob.uid, "day_swap_offered")
        assertEquals(started.answerByMillis.toString(), offered["answerBy"])

        EmulatorEnvironment.step("deadlines: Bob's sync delivers the clock")
        awaitClock(bob, firstId) { true }
        bob.requestClockKeeper.sweep(bob.uid, setOf(alice.uid))
        val delivered = awaitClock(alice, firstId) { it.deliveredAtMillis != null }
        assertEquals(delivered.reminderTimesMillis.size, delivered.remindersPlanned)
        assertTrue(
            "a three-day deadline plans one to three reminders",
            delivered.reminderTimesMillis.size in 1..MAX_REMINDERS
        )
        val reminders = bob.requestScheduler.scheduled
            .filter { it.clockId == firstId && it.step == RequestClockWork.STEP_REMINDER }
        assertEquals(delivered.remindersPlanned, reminders.size)

        EmulatorEnvironment.step("deadlines: Bob opens the request")
        assertTrue(bob.requestClockActions.markSeen(firstId))
        assertNotNull(awaitClock(alice, firstId) { it.seenAtMillis != null }.seenAtMillis)

        val secondDay = LocalDate.now().plusDays(SECOND_OFFER_DAYS_AHEAD)
        val secondId = lastChanceOnASecondOffer(agreed, familyId, secondDay)
        answerTheLastChance(familyId, secondDay, secondId)
    }

    @Test
    fun aProposalNobodyAnswersClosesAsNoAnswerAndIsWithdrawn() = runBlocking<Unit> {
        agreeFirstPattern()
        val familyId = CustodyKey.of(alice.uid, bob.uid)

        EmulatorEnvironment.step("deadlines: Alice proposes a 2-2-3 with no deadline")
        assertEquals(
            PatternSubmission.PROPOSED,
            alice.custodyRepository.createTwoTwoThree(START, momStartsFirst = true)
        )
        val proposedAt = checkNotNull(awaitShared(alice) { it.proposal?.proposedBy == alice.uid }.proposal).proposedAt

        EmulatorEnvironment.step("deadlines: Alice starts a clock that leaves no room for a last chance")
        val now = System.currentTimeMillis()
        val clock = checkNotNull(
            alice.requestDeadlineRepository.start(
                RequestStart(
                    kind = RequestKind.PROPOSAL,
                    ref = proposedAt,
                    familyId = familyId,
                    responderUid = bob.uid,
                    answerByMillis = now + SHORT_DEADLINE_MILLIS,
                    capMillis = now + SHORT_CAP_MILLIS
                )
            )
        ) { "the rules refused the proposal's clock" }
        assertEquals(RequestDeadlineId.of(RequestKind.PROPOSAL, alice.uid, proposedAt, familyId), clock.id)

        EmulatorEnvironment.step("deadlines: the deadline passes and Alice's phone ends the proposal")
        waitUntilPast(clock.answerByMillis)
        assertEquals(StepResult.DONE, awaitStep { alice.requestClockSteps.end(clock.id) })
        val closed = awaitClock(alice, clock.id) { it.outcome != null }
        assertEquals(RequestClockOutcome.NO_ANSWER, closed.outcome)
        assertNotNull(closed.closedAtMillis)

        EmulatorEnvironment.step("deadlines: the proposal is withdrawn and Bob is told")
        val shared = awaitShared(bob) { it.proposal == null }
        awaitCard(ActivityKind.REQUEST_UNANSWERED)
        val bobsClock = awaitClock(bob, clock.id) { it.outcome != null }
        val inbox = RequestInbox.build(
            scope = checkNotNull(RequestScope.of(bob.uid, alice.uid, setOf(alice.uid))),
            changeRequests = emptyList(),
            events = emptyList(),
            overrides = bob.custodyRepository.observeDayOverrides().first(),
            shared = shared,
            today = LocalDate.now(),
            clocks = ClocksAt.of(listOf(bobsClock), System.currentTimeMillis())
        )
        assertTrue(
            "Bob's history lists the proposal as No answer: ${inbox.history}",
            inbox.history.any {
                it.outcome == RequestOutcome.NO_ANSWER && it.subject == RequestSubject.CustodySchedule &&
                    it.key == "custody_$proposedAt"
            }
        )
        assertNull(inbox.waitingOnMe.proposal)

        EmulatorEnvironment.step("deadlines: Bob can propose again")
        assertEquals(
            PatternSubmission.PROPOSED,
            bob.custodyRepository.createWeekOnWeekOff(START, momFirst = false)
        )
        awaitShared(alice) { it.proposal?.proposedBy == bob.uid }
    }

    // ---- the last chance ------------------------------------------------------------------------

    /**
     * Alice offers [day] with no deadline, starts its clock directly with a deadline moments away
     * and the cap at the day's start, and gives the last chance once the deadline has passed.
     *
     * @return The clock's id.
     */
    private suspend fun lastChanceOnASecondOffer(agreed: CustodyModel, familyId: String, day: LocalDate): String {
        EmulatorEnvironment.step("deadlines: Alice offers a second day and starts a short clock")
        val date = day.toString()
        val group = offerDay(date, otherSlotOn(agreed, date), answerBy = null)
        val id = RequestDeadlineId.of(RequestKind.SWAP, alice.uid, group, familyId)
        // An offer with no pick starts no clock: the rules answer a missing one with a denial.
        assertNull(alice.requestDeadlineRepository.get(id))
        val clock = checkNotNull(
            alice.requestDeadlineRepository.start(
                RequestStart(
                    kind = RequestKind.SWAP,
                    ref = group,
                    familyId = familyId,
                    responderUid = bob.uid,
                    answerByMillis = System.currentTimeMillis() + SHORT_DEADLINE_MILLIS,
                    capMillis = day.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli(),
                    firstDate = day,
                    dayCount = 1
                )
            )
        ) { "the rules refused the offer's clock" }
        assertEquals(id, clock.id)

        EmulatorEnvironment.step("deadlines: the deadline passes and Alice gives a last chance")
        waitUntilPast(clock.answerByMillis)
        assertTrue(awaitTrue { alice.requestClockActions.giveLastChance(id) })
        val given = awaitClock(alice, id) { it.lastChanceUntilMillis != null }
        val push = EmulatorEnvironment.awaitQueuedPush(bob.uid, "request_last_chance")
        assertEquals(given.lastChanceUntilMillis.toString(), push["answerBy"])
        assertEquals(familyId, push["familyId"])
        return id
    }

    /** Bob accepts [day] under its last chance: the end closes nothing, and the history says Accepted. */
    private suspend fun answerTheLastChance(familyId: String, day: LocalDate, id: String) {
        EmulatorEnvironment.step("deadlines: Bob accepts under the last chance")
        val date = day.toString()
        val now = LocalDateTime.now().toString()
        bob.custodyRepository.applyDayOverridesForDates(
            dates = listOf(date),
            transform = { current, each ->
                if (DayOverrideTransition.awaitsAnswerFrom(current, each, bob.uid)) {
                    DayOverrideTransition.decideGroup(current, listOf(each), bob.uid, now, true)
                } else {
                    Result.success(current)
                }
            }
        ).getOrThrow()
        val overrides = awaitOverrides(alice) { it[date]?.isAccepted == true }

        assertEquals(StepResult.DONE, alice.requestClockSteps.end(id))
        val clock = checkNotNull(alice.requestDeadlineRepository.get(id))
        assertNull("an answered request is never closed", clock.outcome)

        val inbox = RequestInbox.build(
            scope = checkNotNull(RequestScope.of(alice.uid, bob.uid, setOf(bob.uid))),
            changeRequests = emptyList(),
            events = emptyList(),
            overrides = overrides,
            shared = null,
            today = LocalDate.now(),
            clocks = ClocksAt.of(listOf(clock), System.currentTimeMillis())
        )
        assertTrue(
            "Alice's history lists the second offer as accepted: ${inbox.history}",
            inbox.history.any {
                it.outcome == RequestOutcome.ACCEPTED && it.subject == RequestSubject.DaySwap(day, 1)
            }
        )
        assertEquals(familyId, clock.familyId)
    }

    // ---- the pattern and offers -----------------------------------------------------------------

    /** The pair's first schedule, as `TwoParentCustodyTest.agreeFirstPattern` sets it. */
    private suspend fun agreeFirstPattern(): CustodyModel {
        EmulatorEnvironment.step("deadlines: Alice sets the first pattern")
        assertEquals(
            PatternSubmission.ACTIVATED,
            alice.custodyRepository.createWeekOnWeekOff(START, momFirst = true)
        )
        val model = checkNotNull(alice.custodyRepository.getActiveModelSync())
        assertTrue(EmulatorEnvironment.documentExists("custody_models/${CustodyKey.of(alice.uid, bob.uid)}"))
        withTimeout(EmulatorParent.WAIT_MS) {
            bob.custodyRepository.getActiveModel().first { it?.id == model.id }
        }
        return model
    }

    /**
     * Alice offers [date] to [toSlot] as the calendar does (`applyDayOverrides`, one group id per
     * offer), with the deadline [answerBy] or none.
     *
     * @return The offer's group id, which names its clock.
     */
    private suspend fun offerDay(date: String, toSlot: String, answerBy: AnswerByPick?): String {
        val groupId = UUID.randomUUID().toString()
        val atIso = LocalDateTime.now().toString()
        alice.custodyRepository.applyDayOverrides(
            date = date,
            transform = { current ->
                DayOverrideTransition.offer(current, date, toSlot, alice.uid, atIso, groupId = groupId)
            },
            answerBy = answerBy
        ).getOrThrow()
        return groupId
    }

    /** The slot that would take [date] from whoever the agreed pattern gives it to. */
    private fun otherSlotOn(model: CustodyModel, date: String): String =
        if (model.getCustodyFor(LocalDate.parse(date)) == "mom") "dad" else "mom"

    // ---- waits ----------------------------------------------------------------------------------

    /** The clock [id] as [parent] reads it from the server, once [condition] holds. */
    private suspend fun awaitClock(
        parent: EmulatorParent,
        id: String,
        condition: (RequestDeadline) -> Boolean
    ): RequestDeadline = withTimeout(EmulatorParent.WAIT_MS) {
        flow {
            while (true) {
                emit(parent.requestDeadlineRepository.get(id))
                delay(POLL_MS)
            }
        }.filterNotNull().first { condition(it) }
    }

    /** The pair's document as [parent]'s shared listener delivers it, once [condition] holds. */
    private suspend fun awaitShared(parent: EmulatorParent, condition: (SharedCustody) -> Boolean): SharedCustody =
        withTimeout(EmulatorParent.WAIT_MS) {
            checkNotNull(parent.custodyRepository.observeShared().first { it != null && condition(it) })
        }

    /** [parent]'s day swaps, as its Room holds them, once [condition] holds. */
    private suspend fun awaitOverrides(
        parent: EmulatorParent,
        condition: (Map<String, DayOverride>) -> Boolean
    ): Map<String, DayOverride> = withTimeout(EmulatorParent.WAIT_MS) {
        parent.custodyRepository.observeDayOverrides().first { condition(it) }
    }

    /** Waits until Bob's thread with Alice holds a card of [kind]. */
    private suspend fun awaitCard(kind: ActivityKind) {
        withTimeout(EmulatorParent.WAIT_MS) {
            bob.messageRepository.observeMessages(ConversationKey.of(alice.uid, bob.uid))
                .first { messages -> messages.any { it.activity?.kind == kind } }
        }
    }

    /**
     * Sleeps until this phone's clock is a moment past [millis]. The server's clock decides what
     * the rules allow after a deadline, so the steps that follow are retried for a bounded while.
     */
    private suspend fun waitUntilPast(millis: Long) {
        val wait = millis + PAST_MARGIN_MS - System.currentTimeMillis()
        if (wait > 0) delay(wait)
    }

    /**
     * Runs [block] until it answers true, for at most [EmulatorParent.WAIT_MS]: a write the rules
     * judge by the server's clock may be refused a moment after the deadline, and a refused write
     * changes nothing, so it is asked again.
     */
    private suspend fun awaitTrue(block: suspend () -> Boolean): Boolean = withTimeout(EmulatorParent.WAIT_MS) {
        while (!block()) delay(POLL_MS)
        true
    }

    /** Runs a clock step until it is no longer [StepResult.RETRY], as the worker's retries would. */
    private suspend fun awaitStep(block: suspend () -> StepResult): StepResult = withTimeout(EmulatorParent.WAIT_MS) {
        var result = block()
        while (result == StepResult.RETRY) {
            delay(POLL_MS)
            result = block()
        }
        result
    }

    private companion object {
        /** A Monday; the pattern's anchor, as in `TwoParentCustodyTest`. */
        val START: LocalDate = LocalDate.of(2026, 10, 5)

        /** The default deadline is three days, so the offer's day lies well beyond it. */
        const val FIRST_OFFER_DAYS_AHEAD = 10L

        /** The second offer, on another day than the first. */
        const val SECOND_OFFER_DAYS_AHEAD = 12L

        /** A deadline the test waits out: 45 seconds after the clock is started. */
        const val SHORT_DEADLINE_MILLIS = 45_000L

        /** A cap 60 seconds out: under two hours past the deadline, so no last chance is offered. */
        const val SHORT_CAP_MILLIS = 60_000L

        /** How far past a deadline this phone's clock must be before the test acts on it. */
        const val PAST_MARGIN_MS = 1_000L

        /** A clock plans three reminders at most. */
        const val MAX_REMINDERS = 3

        const val POLL_MS = 500L
    }
}
