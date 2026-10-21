package com.coparently.app.domain.custody

import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The rules behind offering one day to the other parent.
 *
 * The property that matters most is that a parent cannot decide their own offer. Without it a
 * swap is not an agreement at all — either parent could grant themselves a day and the other
 * would merely be told, which is exactly the state PR #47 existed to end for whole patterns.
 */
class DayOverrideTransitionTest {

    private val mom = "uid-mom"
    private val dad = "uid-dad"
    private val date = "2026-09-05"
    private val now = "2026-08-23T10:00:00"

    @Test
    fun `offering records a pending override for that date`() {
        val next = DayOverrideTransition
            .offer(emptyMap(), date, toParent = "dad", byUid = mom, atIso = now).getOrThrow()

        assertEquals(DayOverrideStatus.PENDING, next.getValue(date).status)
        assertEquals("dad", next.getValue(date).toParent)
        assertEquals(mom, next.getValue(date).requestedBy)
    }

    @Test
    fun `a second offer for the same date replaces the first rather than queueing`() {
        val first = DayOverrideTransition
            .offer(emptyMap(), date, "dad", mom, now).getOrThrow()
        val second = DayOverrideTransition
            .offer(first, date, "mom", dad, "2026-08-23T11:00:00").getOrThrow()

        assertEquals(1, second.size)
        assertEquals(dad, second.getValue(date).requestedBy)
    }

    @Test
    fun `the parent who offered cannot accept their own offer`() {
        val offered = DayOverrideTransition.offer(emptyMap(), date, "dad", mom, now).getOrThrow()

        assertTrue(DayOverrideTransition.accept(offered, date, byUid = mom, atIso = now).isFailure)
    }

    @Test
    fun `the other parent can accept it`() {
        val offered = DayOverrideTransition.offer(emptyMap(), date, "dad", mom, now).getOrThrow()
        val accepted = DayOverrideTransition.accept(offered, date, dad, now).getOrThrow()

        assertEquals(DayOverrideStatus.ACCEPTED, accepted.getValue(date).status)
        assertEquals(dad, accepted.getValue(date).decidedBy)
    }

    @Test
    fun `a decided override cannot be decided again`() {
        val offered = DayOverrideTransition.offer(emptyMap(), date, "dad", mom, now).getOrThrow()
        val accepted = DayOverrideTransition.accept(offered, date, dad, now).getOrThrow()

        assertTrue(DayOverrideTransition.decline(accepted, date, dad, now).isFailure)
    }

    @Test
    fun `deciding a date with no override fails rather than inventing one`() {
        assertTrue(DayOverrideTransition.accept(emptyMap(), date, dad, now).isFailure)
    }

    @Test
    fun `awaitsAnswerFrom is true only for the other parent's still-pending offer`() {
        // The predicate a caller writing a group one day at a time has to apply itself, against
        // the map the write just read. Getting it from a Room mirror instead is what made a
        // part-answered group unfinishable: the mirror can only be behind, and it is behind
        // exactly on the retry after a partial run.
        val offered = DayOverrideTransition.offer(emptyMap(), date, "dad", mom, now).getOrThrow()

        assertTrue(DayOverrideTransition.awaitsAnswerFrom(offered, date, dad))
        assertFalse(DayOverrideTransition.awaitsAnswerFrom(offered, date, mom), "your own offer")
        assertFalse(DayOverrideTransition.awaitsAnswerFrom(offered, "2026-09-12", dad), "no entry")

        val answered = DayOverrideTransition.accept(offered, date, dad, now).getOrThrow()
        assertFalse(DayOverrideTransition.awaitsAnswerFrom(answered, date, dad), "already decided")
    }

    @Test
    fun `a single-day offer carries its group id`() {
        // A one-day offer is a group of one (phase 3a): its clock is named by the group id, as a
        // run's is, so the id has to reach the stored entry.
        val next = DayOverrideTransition
            .offer(emptyMap(), date, "dad", mom, now, groupId = "group-1").getOrThrow()

        assertEquals("group-1", next.getValue(date).groupId)
        // A caller that names no group still writes the entry an older build wrote.
        assertNull(DayOverrideTransition.offer(emptyMap(), date, "dad", mom, now).getOrThrow().getValue(date).groupId)
    }

    @Test
    fun `an offer for one date leaves every other date alone`() {
        val other = "2026-09-12"
        val first = DayOverrideTransition.offer(emptyMap(), other, "mom", dad, now).getOrThrow()
        val second = DayOverrideTransition.offer(first, date, "dad", mom, now).getOrThrow()

        assertEquals(setOf(other, date), second.keys)
        assertEquals(DayOverrideStatus.PENDING, second.getValue(other).status)
    }

    @Test
    fun `offering an answered day again keeps its answer in the new offer's earlier list`() {
        val offered = DayOverrideTransition.offer(emptyMap(), date, "dad", dad, now, groupId = "g1").getOrThrow()
        val accepted = DayOverrideTransition
            .decideGroup(offered, listOf(date), mom, "2026-08-23T11:00:00", accept = true)
            .getOrThrow()

        val change = DayOverrideTransition
            .offerAll(accepted, mapOf(date to "mom"), mom, "2026-08-30T09:00:00", groupId = "g2")
            .getOrThrow()
            .getValue(date)

        assertEquals(DayOverrideStatus.PENDING, change.status)
        assertEquals(
            listOf("d1|dad|$dad|$now|ACCEPTED|$mom|2026-08-23T11:00:00|g1"),
            change.earlier
        )
        // While the change waits, the agreement stands.
        assertEquals("dad", change.agreedToParent)
    }

    @Test
    fun `an answer keeps the earlier list, so a declined change leaves the agreement standing`() {
        val offered = DayOverrideTransition.offer(emptyMap(), date, "dad", dad, now).getOrThrow()
        val accepted = DayOverrideTransition.decideGroup(offered, listOf(date), mom, now, accept = true).getOrThrow()
        val change = DayOverrideTransition.offer(accepted, date, "mom", mom, "2026-08-30T09:00:00").getOrThrow()

        val declined = DayOverrideTransition
            .decideGroup(change, listOf(date), dad, "2026-08-30T10:00:00", accept = false)
            .getOrThrow().getValue(date)

        assertEquals(DayOverrideStatus.DECLINED, declined.status)
        assertEquals(change.getValue(date).earlier, declined.earlier)
        assertEquals("dad", declined.agreedToParent)
    }

    @Test
    fun `replacing an unanswered offer adds nothing to the earlier list`() {
        val first = DayOverrideTransition.offer(emptyMap(), date, "dad", mom, now).getOrThrow()
        val second = DayOverrideTransition.offer(first, date, "mom", dad, "2026-08-23T11:00:00").getOrThrow()

        assertNull(second.getValue(date).earlier)
    }

    @Test
    fun `the parent who offered can withdraw a pending offer, keeping its slot, group and earlier answers`() {
        val offered = DayOverrideTransition.offer(emptyMap(), date, "dad", dad, now, groupId = "g1").getOrThrow()
        val accepted = DayOverrideTransition.decideGroup(offered, listOf(date), mom, now, accept = true).getOrThrow()
        val change = DayOverrideTransition
            .offer(accepted, date, "mom", mom, "2026-08-30T09:00:00", groupId = "g2").getOrThrow()

        val withdrawn = DayOverrideTransition.withdraw(change, date, mom, "2026-08-30T10:00:00")
            .getOrThrow()
            .getValue(date)

        assertEquals(DayOverrideStatus.CANCELLED, withdrawn.status)
        assertEquals(mom, withdrawn.decidedBy)
        assertEquals("2026-08-30T10:00:00", withdrawn.decidedAt)
        assertEquals("mom", withdrawn.toParent)
        assertEquals(mom, withdrawn.requestedBy)
        assertEquals("g2", withdrawn.groupId)
        assertEquals(change.getValue(date).earlier, withdrawn.earlier)
        // A withdrawn change leaves the agreement standing.
        assertEquals("dad", withdrawn.agreedToParent)
    }

    @Test
    fun `a withdrawn first offer gives the day to nobody`() {
        val offered = DayOverrideTransition.offer(emptyMap(), date, "dad", mom, now).getOrThrow()

        val withdrawn = DayOverrideTransition.withdraw(offered, date, mom, now).getOrThrow().getValue(date)

        assertNull(withdrawn.agreedToParent)
        assertFalse(withdrawn.isPending)
    }

    @Test
    fun `only the parent who offered may withdraw, and only while it waits`() {
        val offered = DayOverrideTransition.offer(emptyMap(), date, "dad", mom, now).getOrThrow()
        val accepted = DayOverrideTransition.accept(offered, date, dad, now).getOrThrow()

        assertTrue(DayOverrideTransition.withdraw(offered, date, dad, now).isFailure)
        assertTrue(DayOverrideTransition.withdraw(accepted, date, mom, now).isFailure)
        assertTrue(DayOverrideTransition.withdraw(emptyMap(), date, mom, now).isFailure)
        assertTrue(DayOverrideTransition.mayWithdraw(offered, date, mom))
        assertFalse(DayOverrideTransition.mayWithdraw(offered, date, dad))
        assertFalse(DayOverrideTransition.mayWithdraw(accepted, date, mom))
    }

    @Test
    fun `offering a withdrawn day again carries the withdrawal in the earlier list`() {
        val offered = DayOverrideTransition.offer(emptyMap(), date, "dad", mom, now, groupId = "g1").getOrThrow()
        val withdrawn = DayOverrideTransition.withdraw(offered, date, mom, "2026-08-23T11:00:00").getOrThrow()

        val again = DayOverrideTransition.offer(withdrawn, date, "mom", dad, "2026-08-24T09:00:00").getOrThrow()

        assertEquals(
            listOf("d1|dad|$mom|$now|CANCELLED|$mom|2026-08-23T11:00:00|g1"),
            again.getValue(date).earlier
        )
        assertNull(again.getValue(date).agreedToParent)
    }
}
