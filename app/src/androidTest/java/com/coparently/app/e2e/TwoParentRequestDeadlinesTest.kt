package com.coparently.app.e2e

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.coparently.app.data.notification.RequestClockWork
import com.coparently.app.data.remote.firebase.PushPayload
import com.coparently.app.data.repository.PatternSubmission
import com.coparently.app.data.repository.RatioSubmission
import com.coparently.app.domain.activity.ActivityEntityType
import com.coparently.app.domain.activity.ActivityKind
import com.coparently.app.domain.changerequests.AnswerByPick
import com.coparently.app.domain.changerequests.ClocksAt
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
import com.coparently.app.domain.expenses.FamilySettings
import com.coparently.app.domain.expenses.SplitRatio
import com.coparently.app.domain.model.CustodyModel
import com.coparently.app.domain.model.Message
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.plus
import kotlinx.datetime.toJavaLocalDate
import kotlinx.datetime.toKotlinLocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.UUID

/**
 * Request deadlines between two phones (phase 3a, "no request is left hanging"): a clock started
 * with an offer, received and opened on the other phone, given a last chance after its deadline,
 * and answered under it.
 *
 * Every clock write goes through the production `RequestDeadlineRepositoryImpl` against the real
 * `firestore.rules`, and every step through the production `RequestClockKeeper` and
 * `RequestClockActions` of the phone that runs it. What WorkManager would do at a reminder's time
 * the test does itself: each phone's [RecordingClockScheduler] records the reminders.
 *
 * **The deadline notice and the end are the server's since MON-25**, so a request nobody answers is
 * closed, withdrawn and carded by the scheduled function, not by a phone; `TwoParentServerClocksTest`
 * runs that between two parents.
 *
 * What it proves:
 * - **An offer with a deadline starts its clock, and its push says when** (`answerBy`), and the
 *   answering phone records the delivery with the reminders it planned, and schedules those.
 * - **Opening the request is recorded** on the proposer's side.
 * - **The last chance is given only after the deadline**, reaches the answering parent as the
 *   `request_last_chance` push read back from `notification_queue`, and an answer under it is
 *   listed as Accepted in the inbox's history; the server's sweep, run past the last chance's end
 *   (`EmulatorEnvironment.awaitServerClock`), settles the clock and closes nothing.
 * - **An expense split proposal is a request too** (October 2026): proposed through the production
 *   `FamilySettingsRepository` with a deadline, its clock reaches the co-parent, its push says when
 *   to answer by, and its `SPLIT_PROPOSED` card reaches their thread.
 *
 * **Short deadlines, started directly.** A preset is three days at the least, so the clocks this
 * test lets run out are written straight through `RequestDeadlineRepository.start`, with a deadline
 * [SHORT_DEADLINE_MILLIS] ahead. `firestore.rules` asks only that a deadline be after the server's
 * time and that a cap come after it, so the start goes through the same rule an offer's does.
 *
 * What it cannot: the reminders' jobs firing on their own (WorkManager's time),
 * the notifications and cards as drawn, and FCM's delivery of the push.
 */
@RunWith(AndroidJUnit4::class)
class TwoParentRequestDeadlinesTest : TwoParentTest() {

    @Test
    fun aSwapWithADeadlineIsDeliveredOpenedGivenALastChanceAndAnswered() = runBlocking<Unit> {
        val agreed = agreeFirstPattern()
        val familyId = CustodyKey.of(alice.uid, bob.uid)

        EmulatorEnvironment.step("deadlines: Alice offers a day with the default deadline")
        val firstDay = currentDay().plus(FIRST_OFFER_DAYS_AHEAD, DateTimeUnit.DAY).toString()
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

        val secondDay = currentDay().plus(SECOND_OFFER_DAYS_AHEAD, DateTimeUnit.DAY)
        val secondId = lastChanceOnASecondOffer(agreed, familyId, secondDay)
        answerTheLastChance(familyId, secondDay, secondId)
    }

    @Test
    fun aSplitProposalWithADeadlineReachesAlice() = runBlocking<Unit> {
        val familyId = CustodyKey.of(alice.uid, bob.uid)

        EmulatorEnvironment.step("deadlines: Alice sets the pair's first split")
        assertEquals(
            RatioSubmission.APPLIED,
            alice.familySettingsRepository.submitRatio(SplitRatio.EVEN, familyId).getOrThrow()
        )
        awaitSettings(bob, familyId) { it.ratio == SplitRatio.EVEN }

        proposeASplitWithADeadline(familyId)
    }

    // ---- the expense split ----------------------------------------------------------------------

    /**
     * Bob proposes a split with the default deadline: its clock, its push with the deadline and its
     * card reach Alice's phone.
     */
    private suspend fun proposeASplitWithADeadline(familyId: String) {
        EmulatorEnvironment.step("deadlines: Bob proposes a new split with the default deadline")
        assertEquals(
            RatioSubmission.PROPOSED,
            bob.familySettingsRepository.submitRatio(SIXTY_FORTY, familyId, AnswerByPick.DEFAULT).getOrThrow()
        )
        val first = checkNotNull(awaitSettings(alice, familyId) { it.proposal?.proposedBy == bob.uid }.proposal)
        val firstId = RequestDeadlineId.forSplit(first, familyId)
        val started = awaitClock(alice, firstId) { true }
        assertEquals(RequestKind.SPLIT, started.kind)
        assertEquals(alice.uid, started.responderUid)
        assertNull("a split changes no day ahead, so its clock has no cap", started.capMillis)
        val pushed = EmulatorEnvironment.awaitQueuedPush(alice.uid, PushPayload.SPLIT_RATIO_PROPOSED)
        assertEquals(started.answerByMillis.toString(), pushed["answerBy"])
        val card = awaitCard(ActivityKind.SPLIT_PROPOSED, reader = alice)
        assertEquals(ActivityEntityType.SPLIT_PROPOSAL, card.activity?.entityType)
        assertEquals(familyId, card.activity?.entityId)
        assertEquals(SIXTY_FORTY.momShareBasisPoints.toString(), card.activity?.amount)
        assertTrue(card.content, card.content.startsWith("Proposed a new expense split"))
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
                    capMillis = day.toJavaLocalDate().atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli(),
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

    /**
     * Bob accepts [day] under its last chance: when the server's sweep reaches the last chance's end
     * it finds the answer and settles the clock without closing it, and the history says Accepted.
     */
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

        EmulatorEnvironment.step("deadlines: the server sweeps past the last chance and closes nothing")
        val lastChanceUntil = checkNotNull(checkNotNull(alice.requestDeadlineRepository.get(id)).lastChanceUntilMillis)
        val settled = EmulatorEnvironment.awaitServerClock(id, lastChanceUntil + PAST_MARGIN_MS) {
            it["serverSettledAt"] != null
        }
        assertNull("the server settled the answered request without closing it", settled["serverToldNoAnswerAt"])
        val clock = checkNotNull(alice.requestDeadlineRepository.get(id))
        assertNull("an answered request is never closed", clock.outcome)
        assertFalse(
            "nobody is told that an answered request went unanswered",
            EmulatorEnvironment.hasQueuedPush(alice.uid, PushPayload.REQUEST_NO_ANSWER)
        )

        val inbox = RequestInbox.build(
            scope = checkNotNull(RequestScope.of(alice.uid, bob.uid, setOf(bob.uid))),
            changeRequests = emptyList(),
            events = emptyList(),
            overrides = overrides,
            shared = null,
            today = currentDay(),
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

    /** [parent]'s day swaps, as its Room holds them, once [condition] holds. */
    private suspend fun awaitOverrides(
        parent: EmulatorParent,
        condition: (Map<String, DayOverride>) -> Boolean
    ): Map<String, DayOverride> = withTimeout(EmulatorParent.WAIT_MS) {
        parent.custodyRepository.observeDayOverrides().first { condition(it) }
    }

    /** Waits until [reader]'s copy of the thread between Alice and Bob holds a card of [kind]; that card. */
    private suspend fun awaitCard(kind: ActivityKind, reader: EmulatorParent = bob): Message =
        withTimeout(EmulatorParent.WAIT_MS) {
            reader.messageRepository.observeMessages(ConversationKey.of(alice.uid, bob.uid))
                .first { messages -> messages.any { it.activity?.kind == kind } }
                .first { it.activity?.kind == kind }
        }

    /** The family's money agreement as [parent]'s listener delivers it, once [condition] holds. */
    private suspend fun awaitSettings(
        parent: EmulatorParent,
        familyId: String,
        condition: (FamilySettings) -> Boolean
    ): FamilySettings = withTimeout(EmulatorParent.WAIT_MS) {
        checkNotNull(
            parent.familySettingsRepository.observeSettingsOf(familyId).first { it != null && condition(it) }
        )
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

    private companion object {
        /** A Monday; the pattern's anchor, as in `TwoParentCustodyTest`. */
        val START: LocalDate = LocalDate(2026, 10, 5)

        /** Today on this phone, as the custody domain's kotlinx-datetime day (MON-24). */
        fun currentDay(): LocalDate = java.time.LocalDate.now().toKotlinLocalDate()

        /** The default deadline is three days, so the offer's day lies well beyond it. */
        const val FIRST_OFFER_DAYS_AHEAD = 10L

        /** The second offer, on another day than the first. */
        const val SECOND_OFFER_DAYS_AHEAD = 12L

        /** A deadline the test waits out: 45 seconds after the clock is started. */
        const val SHORT_DEADLINE_MILLIS = 45_000L

        /** How far past a deadline this phone's clock must be before the test acts on it. */
        const val PAST_MARGIN_MS = 1_000L

        /** A clock plans three reminders at most. */
        const val MAX_REMINDERS = 3

        const val POLL_MS = 500L

        /** The split Bob proposes, as slot 1's share. */
        val SIXTY_FORTY = SplitRatio(6_000)
    }
}
