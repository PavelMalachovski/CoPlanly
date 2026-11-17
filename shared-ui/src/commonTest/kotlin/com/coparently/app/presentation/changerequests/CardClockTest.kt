package com.coparently.app.presentation.changerequests

import com.coparently.app.domain.changerequests.ClockState
import com.coparently.app.domain.changerequests.RequestDeadline
import com.coparently.app.domain.changerequests.RequestKind
import com.coparently.app.domain.changerequests.TimedRequest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * [CardClock]: which answers a request's card offers once the clock has passed the deadline
 * (phase 3a, spec §4.1; final review I-2).
 *
 * Past the deadline the next move is the proposer's, so a swap, a change request and an event hide
 * both answers. A custody proposal keeps its Decline for the answering parent: until the
 * proposer's phone withdraws it, the proposal blocks every schedule change the answering parent
 * could make, and a phone that never syncs would block them for good. Accept stays hidden — the
 * proposer has not offered a last chance.
 *
 * And what its reminders row reads (iPhone phase 4 part C, spec §11 call 1): planned is the phone's
 * count, else the server's plan; sent is the larger of the server's count and an older build's shown
 * count; the next reminder comes from the plan in force.
 */
class CardClockTest {

    @Test
    fun `past the deadline the answering parent may still decline a proposal but never accept it`() {
        val card = card(RequestKind.PROPOSAL, ClockRole.RESPONDER, pastDeadline())

        assertEquals(ClockState.NO_ANSWER_LAST_CHANCE_POSSIBLE, card.timed.state)
        assertTrue(card.declineOnly, "Decline stays")
        assertFalse(card.hidesAnswers, "not both hidden")
    }

    @Test
    fun `past the deadline an expense split keeps its Decline as a custody proposal does`() {
        // A pending split blocks the answering parent's own proposals until the proposer's phone
        // withdraws it, so they may still turn it down.
        val card = card(RequestKind.SPLIT, ClockRole.RESPONDER, pastDeadline())

        assertTrue(card.declineOnly, "Decline stays")
        assertFalse(card.hidesAnswers, "not both hidden")

        val proposer = card(RequestKind.SPLIT, ClockRole.PROPOSER, pastDeadline())
        assertFalse(proposer.declineOnly)
        assertFalse(proposer.hidesAnswers)
    }

    @Test
    fun `past the deadline a swap and a change request and an event still hide both answers`() {
        listOf(RequestKind.SWAP, RequestKind.CHANGE, RequestKind.EVENT).forEach { kind ->
            val card = card(kind, ClockRole.RESPONDER, pastDeadline())
            assertTrue(card.hidesAnswers, "$kind hides both")
            assertFalse(card.declineOnly, "$kind offers no Decline alone")
        }
    }

    @Test
    fun `an open proposal and the proposer's own card offer no decline-only answer`() {
        val open = card(RequestKind.PROPOSAL, ClockRole.RESPONDER, ANSWER_BY - HOUR)
        assertFalse(open.declineOnly)
        assertFalse(open.hidesAnswers)

        val proposer = card(RequestKind.PROPOSAL, ClockRole.PROPOSER, pastDeadline())
        assertFalse(proposer.declineOnly)
        assertFalse(proposer.hidesAnswers)
    }

    @Test
    fun `in a last chance the proposal offers both answers again`() {
        val now = pastDeadline()
        val card = card(RequestKind.PROPOSAL, ClockRole.RESPONDER, now, lastChanceUntil = now + HOUR)

        assertEquals(ClockState.LAST_CHANCE, card.timed.state)
        assertFalse(card.declineOnly)
        assertFalse(card.hidesAnswers)
    }

    @Test
    fun `the reminders row reads the phone's plan and count when a phone delivered`() {
        // An older build's phone counted two shown; the server has sent one: the larger is said.
        val clock = card(RequestKind.CHANGE, ClockRole.PROPOSER, ANSWER_BY - 2 * DAY).timed.clock.copy(
            deliveredAtMillis = ANSWER_BY - 3 * DAY,
            remindersPlanned = 3,
            reminderTimesMillis = listOf(ANSWER_BY - 2 * DAY - HOUR, ANSWER_BY - DAY, ANSWER_BY - HOUR),
            remindersShown = 2,
            serverReminderTimesMillis = listOf(ANSWER_BY - 30 * HOUR),
            serverRemindersSent = 1
        )
        assertEquals(3, clock.plannedReminders)
        assertEquals(2, clock.remindersSent)
        assertEquals(ANSWER_BY - DAY, TimedRequest(clock, ANSWER_BY - 2 * DAY).nextReminderMillis)
    }

    @Test
    fun `the reminders row reads the server's plan and count when no phone delivered`() {
        val clock = card(RequestKind.CHANGE, ClockRole.PROPOSER, ANSWER_BY - 2 * DAY).timed.clock.copy(
            serverReminderTimesMillis = listOf(ANSWER_BY - 2 * DAY - HOUR, ANSWER_BY - DAY),
            serverRemindersSent = 1
        )
        assertEquals(2, clock.plannedReminders)
        assertEquals(1, clock.remindersSent)
        assertEquals(ANSWER_BY - DAY, TimedRequest(clock, ANSWER_BY - 2 * DAY).nextReminderMillis)
    }

    @Test
    fun `nothing planned by anybody shows no reminders row`() {
        val clock = card(RequestKind.CHANGE, ClockRole.PROPOSER, ANSWER_BY - 2 * DAY).timed.clock
        assertEquals(null, clock.plannedReminders)
        assertEquals(0, clock.remindersSent)
        assertEquals(null, TimedRequest(clock, ANSWER_BY - 2 * DAY).nextReminderMillis)
    }

    private fun pastDeadline() = ANSWER_BY + HOUR

    private fun card(kind: RequestKind, role: ClockRole, now: Long, lastChanceUntil: Long? = null) = CardClock(
        timed = TimedRequest(
            RequestDeadline(
                id = "clock",
                kind = kind,
                ref = "ref",
                familyId = "uidA__uidB",
                proposerUid = "uidA",
                responderUid = "uidB",
                answerByMillis = ANSWER_BY,
                createdAtMillis = ANSWER_BY - 3 * DAY,
                lastChanceAtMillis = lastChanceUntil?.let { now },
                lastChanceUntilMillis = lastChanceUntil
            ),
            now
        ),
        role = role,
        otherName = "Alice",
        onSeen = null,
        onGiveLastChance = null
    )

    private companion object {
        const val HOUR = 3_600_000L
        const val DAY = 24 * HOUR
        const val ANSWER_BY = 1_780_000_000_000L
    }
}
