package com.coparently.app.e2e

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.coparently.app.data.remote.firebase.PushPayload
import com.coparently.app.data.repository.PatternSubmission
import com.coparently.app.data.repository.RatioSubmission
import com.coparently.app.domain.activity.ActivityEntityType
import com.coparently.app.domain.activity.ActivityKind
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
import com.coparently.app.domain.custody.DayOverrideTransition
import com.coparently.app.domain.custody.SharedCustody
import com.coparently.app.domain.expenses.FamilySettings
import com.coparently.app.domain.expenses.SplitRatio
import com.coparently.app.domain.model.ChangeRequest
import com.coparently.app.domain.model.ChangeRequestStatus
import com.coparently.app.domain.model.CustodyModel
import com.coparently.app.domain.model.Event
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
import kotlinx.datetime.toJavaLocalDateTime
import kotlinx.datetime.toKotlinLocalDate
import kotlinx.datetime.toKotlinLocalDateTime
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
 * The server ends a request's clock between two phones (MON-25, iPhone phase 4 part B): the
 * deadline push and the end that the proposer's phone ran from WorkManager until then, now run by
 * the scheduled `sweepRequestClocks` in the clock's own family.
 *
 * Every request and every clock is written through the production repositories of the phone that
 * makes it, against the real `firestore.rules`; the trigger `onRequestClockWritten` schedules each
 * clock on the Functions emulator as it would on the live project, and the sweep is the same
 * `sweepRequestClocksImpl` the schedule calls. Nothing on either phone runs a step.
 *
 * **How the sweep runs here.** The schedule fires every five minutes and a clock with no cap ends a
 * day after its deadline, neither of which a test can wait out; and the rules refuse a clock whose
 * deadline is not ahead of the server's time. So each clock is started with a deadline a minute
 * ahead, and [EmulatorEnvironment.awaitServerClock] calls `sweepRequestClocksOnEmulator` — exported
 * by `functions/index.js` only under the Functions and Firestore emulators, so a deploy never sees
 * it — with the moment past the deadline, or past the end, that the step is to judge at. From the
 * sweep's side the deadline is then in the past, as it is for the schedule a day later.
 *
 * What it proves:
 * - **A change request nobody answers is closed as "No answer"** at its end: the clock's outcome on
 *   both phones, the `REQUEST_UNANSWERED` card in the family's thread on both, the
 *   `request_no_answer` push to the proposer with the family — and the request itself is never set
 *   CANCELLED, so an older build's late answer still counts.
 * - **A custody proposal nobody answers is withdrawn**, the agreed pattern kept, the history says
 *   No answer, and the co-parent can propose again.
 * - **An expense split proposal nobody answers is withdrawn**, the agreed split kept, nothing
 *   recorded as decided.
 * - **A day swap nobody answers is closed as "No answer"** and stays offered, as a swap is never
 *   withdrawn at its end (`SwapScenarioTest#noAnswer` until MON-25).
 * - **The proposer is pushed at the deadline and at the end**: `request_deadline_passed` when the
 *   deadline passes with a last chance still possible, naming the parent who did not answer, and
 *   `request_no_answer` once the button's day has run out — each read back from
 *   `notification_queue`, addressed to the proposer, carrying the family.
 *
 * What it cannot: the schedule firing on its own, FCM's delivery of the pushes, and the cards and
 * notifications as drawn.
 */
@RunWith(AndroidJUnit4::class)
class TwoParentServerClocksTest : TwoParentTest() {

    @Test
    fun aChangeRequestPastItsEndIsClosedByTheServer() = runBlocking<Unit> {
        val familyId = CustodyKey.of(alice.uid, bob.uid)
        val request = sendRequest(insertSharedEvent("Dentist"))
        val clock = startClock(RequestKind.CHANGE, request.id, capMillis = null)
        assertEquals(RequestDeadlineId.forChange(request), clock.id)

        EmulatorEnvironment.step("server clocks: the server sweeps a day past the change request's deadline")
        endOnTheServer(clock)

        assertClosedOnBothPhones(clock.id)
        assertEquals(
            ActivityEntityType.CHANGE_REQUEST,
            awaitCard(ActivityKind.REQUEST_UNANSWERED, alice).activity?.entityType
        )
        assertEquals(request.eventId, awaitCard(ActivityKind.REQUEST_UNANSWERED, bob).activity?.entityId)
        assertNoAnswerPushed(familyId)
        assertFalse(
            "the end came after the button's day, so nothing was said at the deadline",
            EmulatorEnvironment.hasQueuedPush(alice.uid, PushPayload.REQUEST_DEADLINE_PASSED)
        )
        assertEquals(
            "a change request is never cancelled at its end",
            ChangeRequestStatus.PENDING,
            checkNotNull(bob.changeRequestRepository.fetchFromServer(request.id)).status
        )
    }

    @Test
    fun aCustodyProposalPastItsEndIsWithdrawnByTheServer() = runBlocking<Unit> {
        val agreed = agreeFirstPattern()
        val familyId = CustodyKey.of(alice.uid, bob.uid)

        EmulatorEnvironment.step("server clocks: Alice proposes a 2-2-3 and starts its clock")
        assertEquals(
            PatternSubmission.PROPOSED,
            alice.custodyRepository.createTwoTwoThree(START, momStartsFirst = true)
        )
        val proposedAt = checkNotNull(awaitShared(alice) { it.proposal?.proposedBy == alice.uid }.proposal).proposedAt
        val clock = startClock(RequestKind.PROPOSAL, proposedAt, capMillis = null)
        assertEquals(RequestDeadlineId.of(RequestKind.PROPOSAL, alice.uid, proposedAt, familyId), clock.id)

        EmulatorEnvironment.step("server clocks: the server ends the proposal and withdraws it")
        endOnTheServer(clock)
        assertClosedOnBothPhones(clock.id)
        val shared = awaitShared(bob) { it.proposal == null }
        awaitShared(alice) { it.proposal == null }
        assertEquals(
            ActivityEntityType.CUSTODY_PROPOSAL,
            awaitCard(ActivityKind.REQUEST_UNANSWERED, bob).activity?.entityType
        )
        awaitCard(ActivityKind.REQUEST_UNANSWERED, alice)
        assertNoAnswerPushed(familyId)
        assertEquals("the agreed pattern stays", agreed.id, checkNotNull(bob.custodyRepository.getActiveModelSync()).id)

        val inbox = RequestInbox.build(
            scope = checkNotNull(RequestScope.of(bob.uid, alice.uid, setOf(alice.uid))),
            changeRequests = emptyList(),
            events = emptyList(),
            overrides = bob.custodyRepository.observeDayOverrides().first(),
            shared = shared,
            today = currentDay(),
            clocks = ClocksAt.of(
                listOf(checkNotNull(bob.requestDeadlineRepository.get(clock.id))),
                System.currentTimeMillis()
            )
        )
        assertTrue(
            "Bob's history lists the proposal as No answer: ${inbox.history}",
            inbox.history.any {
                it.outcome == RequestOutcome.NO_ANSWER && it.subject == RequestSubject.CustodySchedule &&
                    it.key == "custody_$proposedAt"
            }
        )
        assertNull(inbox.waitingOnMe.proposal)

        EmulatorEnvironment.step("server clocks: Bob can propose again")
        assertEquals(
            PatternSubmission.PROPOSED,
            bob.custodyRepository.createWeekOnWeekOff(START, momFirst = false)
        )
        awaitShared(alice) { it.proposal?.proposedBy == bob.uid }
    }

    @Test
    fun aSplitProposalPastItsEndIsWithdrawnByTheServer() = runBlocking<Unit> {
        val familyId = CustodyKey.of(alice.uid, bob.uid)

        EmulatorEnvironment.step("server clocks: Alice sets the first split and proposes another")
        assertEquals(
            RatioSubmission.APPLIED,
            alice.familySettingsRepository.submitRatio(SplitRatio.EVEN, familyId).getOrThrow()
        )
        awaitSettings(bob, familyId) { it.ratio == SplitRatio.EVEN }
        assertEquals(
            RatioSubmission.PROPOSED,
            alice.familySettingsRepository.submitRatio(SIXTY_FORTY, familyId).getOrThrow()
        )
        val proposal = checkNotNull(awaitSettings(bob, familyId) { it.proposal?.proposedBy == alice.uid }.proposal)
        val clock = startClock(RequestKind.SPLIT, proposal.proposedAtMillis.toString(), capMillis = null)
        assertEquals(RequestDeadlineId.forSplit(proposal, familyId), clock.id)

        EmulatorEnvironment.step("server clocks: the server ends the split and withdraws it")
        endOnTheServer(clock)
        assertClosedOnBothPhones(clock.id)
        val settings = awaitSettings(bob, familyId) { it.proposal == null }
        assertEquals("the agreed split stays", SplitRatio.EVEN, settings.ratio)
        assertNull("nobody answered, so nothing was decided", settings.lastDecision)
        awaitSettings(alice, familyId) { it.proposal == null }
        assertEquals(
            ActivityEntityType.SPLIT_PROPOSAL,
            awaitCard(ActivityKind.REQUEST_UNANSWERED, bob).activity?.entityType
        )
        assertEquals(familyId, awaitCard(ActivityKind.REQUEST_UNANSWERED, alice).activity?.entityId)
        assertNoAnswerPushed(familyId)
    }

    @Test
    fun aDaySwapPastItsEndIsClosedAsNoAnswer() = runBlocking<Unit> {
        val agreed = agreeFirstPattern()
        val familyId = CustodyKey.of(alice.uid, bob.uid)

        EmulatorEnvironment.step("server clocks: Alice offers a day and starts its clock")
        val day = currentDay().plus(OFFER_DAYS_AHEAD, DateTimeUnit.DAY)
        val date = day.toString()
        val group = offerDay(date, otherSlotOn(agreed, date))
        val clock = startClock(
            RequestKind.SWAP,
            group,
            capMillis = day.toJavaLocalDate().atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli(),
            firstDate = day
        )
        assertEquals(RequestDeadlineId.of(RequestKind.SWAP, alice.uid, group, familyId), clock.id)

        EmulatorEnvironment.step("server clocks: the server ends the swap")
        endOnTheServer(clock)
        assertClosedOnBothPhones(clock.id)
        assertEquals(ActivityEntityType.DAY_SWAP, awaitCard(ActivityKind.REQUEST_UNANSWERED, bob).activity?.entityType)
        assertEquals(date, awaitCard(ActivityKind.REQUEST_UNANSWERED, alice).activity?.entityId)
        assertNoAnswerPushed(familyId)

        val overrides = alice.custodyRepository.observeDayOverrides().first()
        assertTrue("a swap is never withdrawn at its end: ${overrides[date]}", overrides[date]?.isPending == true)
        val inbox = RequestInbox.build(
            scope = checkNotNull(RequestScope.of(alice.uid, bob.uid, setOf(bob.uid))),
            changeRequests = emptyList(),
            events = emptyList(),
            overrides = overrides,
            shared = null,
            today = currentDay(),
            clocks = ClocksAt.of(
                listOf(checkNotNull(alice.requestDeadlineRepository.get(clock.id))),
                System.currentTimeMillis()
            )
        )
        assertTrue(
            "Alice's history lists the swap as No answer: ${inbox.history}",
            inbox.history.any { it.outcome == RequestOutcome.NO_ANSWER && it.subject == RequestSubject.DaySwap(day, 1) }
        )
    }

    @Test
    fun theProposerIsPushedAtTheDeadlineAndAtTheEnd() = runBlocking<Unit> {
        val familyId = CustodyKey.of(alice.uid, bob.uid)
        val request = sendRequest(insertSharedEvent("Swimming"))
        val clock = startClock(RequestKind.CHANGE, request.id, capMillis = null)

        EmulatorEnvironment.step("server clocks: the server sweeps just past the deadline")
        EmulatorEnvironment.awaitServerClock(clock.id, clock.answerByMillis + PAST_MS) {
            it["serverToldDeadlineAt"] != null
        }
        val atDeadline = EmulatorEnvironment.awaitQueuedPush(alice.uid, PushPayload.REQUEST_DEADLINE_PASSED)
        assertEquals(familyId, atDeadline[PushPayload.FAMILY_ID])
        assertEquals(bob.name, atDeadline[PushPayload.ACTOR])
        assertFalse(
            "Bob is never pushed about Alice's deadline",
            EmulatorEnvironment.hasQueuedPush(bob.uid, PushPayload.REQUEST_DEADLINE_PASSED)
        )
        assertFalse(
            "a last chance is still possible, so the request is not over",
            EmulatorEnvironment.hasQueuedPush(alice.uid, PushPayload.REQUEST_NO_ANSWER)
        )
        assertNull(checkNotNull(alice.requestDeadlineRepository.get(clock.id)).outcome)

        EmulatorEnvironment.step("server clocks: the server sweeps past the end")
        endOnTheServer(clock)
        assertClosedOnBothPhones(clock.id)
        val atEnd = assertNoAnswerPushed(familyId)
        assertEquals(bob.name, atEnd[PushPayload.ACTOR])
    }

    // ---- requests and clocks --------------------------------------------------------------------

    /**
     * Alice starts the clock of her request [ref] through the production repository, with a deadline
     * [DEADLINE_AHEAD_MS] ahead: the rules refuse one that is not ahead of the server's time.
     */
    private suspend fun startClock(
        kind: RequestKind,
        ref: String,
        capMillis: Long?,
        firstDate: LocalDate? = null
    ): RequestDeadline {
        EmulatorEnvironment.step("server clocks: Alice starts the ${kind.wire} clock")
        return checkNotNull(
            alice.requestDeadlineRepository.start(
                RequestStart(
                    kind = kind,
                    ref = ref,
                    familyId = CustodyKey.of(alice.uid, bob.uid),
                    responderUid = bob.uid,
                    answerByMillis = System.currentTimeMillis() + DEADLINE_AHEAD_MS,
                    capMillis = capMillis,
                    firstDate = firstDate,
                    dayCount = firstDate?.let { 1 }
                )
            )
        ) { "the rules refused the ${kind.wire} clock" }
    }

    /**
     * Sweeps at a moment past [clock]'s end — a day after its deadline, as nothing here gives a last
     * chance or a cap that would shorten it — until the server has closed it.
     */
    private suspend fun endOnTheServer(clock: RequestDeadline) {
        val end = EmulatorEnvironment.awaitServerClock(clock.id, clock.answerByMillis + DAY_MS + PAST_MS) {
            it["serverToldNoAnswerAt"] != null
        }
        assertEquals(RequestClockOutcome.NO_ANSWER.wire, end["outcome"])
        assertNotNull("a closed clock is settled once its step is done", end["serverSettledAt"])
    }

    /** Both phones read the clock [id] as closed with no answer, with the server's closing time. */
    private suspend fun assertClosedOnBothPhones(id: String) {
        for (parent in listOf(alice, bob)) {
            val closed = awaitClock(parent, id) { it.outcome != null }
            assertEquals(RequestClockOutcome.NO_ANSWER, closed.outcome)
            assertNotNull(closed.closedAtMillis)
        }
    }

    /** The `request_no_answer` push to Alice, the proposer, naming the family; never one to Bob. */
    private suspend fun assertNoAnswerPushed(familyId: String): Map<*, *> {
        val push = EmulatorEnvironment.awaitQueuedPush(alice.uid, PushPayload.REQUEST_NO_ANSWER)
        assertEquals(familyId, push[PushPayload.FAMILY_ID])
        assertFalse(EmulatorEnvironment.hasQueuedPush(bob.uid, PushPayload.REQUEST_NO_ANSWER))
        return push
    }

    /** A shared event of Alice's, saved through her repository and therefore already uploaded. */
    private suspend fun insertSharedEvent(title: String): Event {
        val start = LocalDateTime.now().plusDays(EVENT_DAYS_AHEAD).withHour(EVENT_HOUR).withMinute(0)
            .withSecond(0).withNano(0)
        val event = Event(
            id = UUID.randomUUID().toString(),
            title = title,
            startDateTime = start.toKotlinLocalDateTime(),
            endDateTime = start.plusHours(1).toKotlinLocalDateTime(),
            eventType = "appointment",
            parentOwner = alice.database.userDao().getUserById(alice.uid)?.role ?: "mom",
            createdAt = LocalDateTime.now().toKotlinLocalDateTime(),
            updatedAt = LocalDateTime.now().toKotlinLocalDateTime()
        )
        alice.eventRepository.insertEvent(event)
        return checkNotNull(alice.eventRepository.getEventById(event.id))
    }

    /**
     * A request about [event], built as `RequestChangeViewModel` builds one and sent by Alice with
     * no deadline of its own: the test starts its clock with [startClock].
     */
    private suspend fun sendRequest(event: Event): ChangeRequest {
        EmulatorEnvironment.step("server clocks: Alice asks Bob to move ${event.title}")
        val request = ChangeRequest(
            id = UUID.randomUUID().toString(),
            eventId = event.id,
            eventTitle = event.title,
            requestedBy = alice.uid,
            requestedTo = bob.uid,
            currentStartDateTime = event.startDateTime,
            currentEndDateTime = event.endDateTime,
            proposedStartDateTime = event.startDateTime.toJavaLocalDateTime().plusHours(2).toKotlinLocalDateTime(),
            proposedEndDateTime = event.endDateTime?.toJavaLocalDateTime()?.plusHours(2)?.toKotlinLocalDateTime(),
            note = "Could we move it?",
            createdAt = LocalDateTime.now().toKotlinLocalDateTime()
        )
        alice.changeRequestRepository.createChangeRequest(request, answerBy = null)
        assertTrue(
            "the change request reached the server",
            EmulatorEnvironment.documentExists("change_requests/${request.id}")
        )
        return request
    }

    // ---- the pattern and offers -----------------------------------------------------------------

    /** The pair's first schedule, as `TwoParentCustodyTest.agreeFirstPattern` sets it. */
    private suspend fun agreeFirstPattern(): CustodyModel {
        EmulatorEnvironment.step("server clocks: Alice sets the first pattern")
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

    /** Alice offers [date] to [toSlot] as the calendar does, with no deadline; the offer's group id. */
    private suspend fun offerDay(date: String, toSlot: String): String {
        val groupId = UUID.randomUUID().toString()
        val atIso = LocalDateTime.now().toString()
        alice.custodyRepository.applyDayOverrides(
            date = date,
            transform = { current ->
                DayOverrideTransition.offer(current, date, toSlot, alice.uid, atIso, groupId = groupId)
            },
            answerBy = null
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

    /** Waits until [reader]'s copy of the thread between Alice and Bob holds a card of [kind]; that card. */
    private suspend fun awaitCard(kind: ActivityKind, reader: EmulatorParent): Message =
        withTimeout(EmulatorParent.WAIT_MS) {
            reader.messageRepository.observeMessages(ConversationKey.of(alice.uid, bob.uid))
                .first { messages -> messages.any { it.activity?.kind == kind } }
                .first { it.activity?.kind == kind }
        }

    /** The pair's document as [parent]'s shared listener delivers it, once [condition] holds. */
    private suspend fun awaitShared(parent: EmulatorParent, condition: (SharedCustody) -> Boolean): SharedCustody =
        withTimeout(EmulatorParent.WAIT_MS) {
            checkNotNull(parent.custodyRepository.observeShared().first { it != null && condition(it) })
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

    private companion object {
        /** A Monday; the pattern's anchor, as in `TwoParentCustodyTest`. */
        val START: LocalDate = LocalDate(2026, 10, 5)

        /** Today on this phone, as the custody domain's kotlinx-datetime day (MON-24). */
        fun currentDay(): LocalDate = java.time.LocalDate.now().toKotlinLocalDate()

        /** How far ahead a clock's deadline is written: the rules want it after the server's time. */
        const val DEADLINE_AHEAD_MS = 60_000L

        /** A clock with no cap and no last chance ends a day after its deadline (`BUTTON_MILLIS`). */
        const val DAY_MS = 24L * 60 * 60 * 1000

        /** How far past a moment the server's sweep is asked to judge. */
        const val PAST_MS = 1_000L

        /** The swap's day lies beyond its clock's end, so the cap does not shorten the button's day. */
        const val OFFER_DAYS_AHEAD = 10L

        const val EVENT_DAYS_AHEAD = 5L
        const val EVENT_HOUR = 10

        const val POLL_MS = 500L

        /** The split Alice proposes, as slot 1's share. */
        val SIXTY_FORTY = SplitRatio(6_000)
    }
}
