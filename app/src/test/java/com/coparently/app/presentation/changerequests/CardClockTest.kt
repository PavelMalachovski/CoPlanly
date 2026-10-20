package com.coparently.app.presentation.changerequests

import com.coparently.app.domain.changerequests.ClockState
import com.coparently.app.domain.changerequests.RequestDeadline
import com.coparently.app.domain.changerequests.RequestKind
import com.coparently.app.domain.changerequests.TimedRequest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [CardClock]: which answers a request's card offers once the clock has passed the deadline
 * (phase 3a, spec §4.1; final review I-2).
 *
 * Past the deadline the next move is the proposer's, so a swap, a change request and an event hide
 * both answers. A custody proposal keeps its Decline for the answering parent: until the
 * proposer's phone withdraws it, the proposal blocks every schedule change the answering parent
 * could make, and a phone that never syncs would block them for good. Accept stays hidden — the
 * proposer has not offered a last chance.
 */
class CardClockTest {

    @Test
    fun `past the deadline the answering parent may still decline a proposal, never accept it`() {
        val card = card(RequestKind.PROPOSAL, ClockRole.RESPONDER, pastDeadline())

        assertEquals(ClockState.NO_ANSWER_LAST_CHANCE_POSSIBLE, card.timed.state)
        assertTrue("Decline stays", card.declineOnly)
        assertFalse("not both hidden", card.hidesAnswers)
    }

    @Test
    fun `past the deadline an expense split keeps its Decline as a custody proposal does`() {
        // A pending split blocks the answering parent's own proposals until the proposer's phone
        // withdraws it, so they may still turn it down.
        val card = card(RequestKind.SPLIT, ClockRole.RESPONDER, pastDeadline())

        assertTrue("Decline stays", card.declineOnly)
        assertFalse("not both hidden", card.hidesAnswers)

        val proposer = card(RequestKind.SPLIT, ClockRole.PROPOSER, pastDeadline())
        assertFalse(proposer.declineOnly)
        assertFalse(proposer.hidesAnswers)
    }

    @Test
    fun `past the deadline a swap, a change request and an event still hide both answers`() {
        listOf(RequestKind.SWAP, RequestKind.CHANGE, RequestKind.EVENT).forEach { kind ->
            val card = card(kind, ClockRole.RESPONDER, pastDeadline())
            assertTrue("$kind hides both", card.hidesAnswers)
            assertFalse("$kind offers no Decline alone", card.declineOnly)
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
