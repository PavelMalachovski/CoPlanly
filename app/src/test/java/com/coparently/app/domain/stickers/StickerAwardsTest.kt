package com.coparently.app.domain.stickers

import com.coparently.app.domain.changerequests.RequestClockOutcome
import com.coparently.app.domain.changerequests.RequestDeadline
import com.coparently.app.domain.changerequests.RequestKind
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneOffset
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * When the app gives a sticker itself: on time once a week, regularity after four weeks in a row
 * with every request answered (owner decisions, October 2026).
 */
class StickerAwardsTest {

    private val zone = ZoneOffset.UTC

    private fun clock(responder: String = "uB", answerBy: Long, outcome: RequestClockOutcome? = null) =
        RequestDeadline(
            id = "swap_uA_g",
            kind = RequestKind.SWAP,
            ref = "g",
            familyId = "uA__uB",
            proposerUid = "uA",
            responderUid = responder,
            answerByMillis = answerBy,
            outcome = outcome
        )

    private fun millisOf(date: LocalDate) = date.atStartOfDay(zone).toInstant().toEpochMilli()

    private fun onTime(uid: String, week: String) = Sticker(
        id = StickerAwards.appStickerId(uid, StickerKind.ON_TIME, week),
        toUid = uid,
        fromUid = null,
        familyId = "",
        kind = StickerKind.ON_TIME
    )

    @Test
    fun `the ISO week is spelled as the rules expect, across a year boundary`() {
        assertEquals("2026-W43", StickerAwards.isoWeek(LocalDate.of(2026, 10, 23)))
        // 1 January 2027 is a Friday: it belongs to the last week of 2026.
        assertEquals("2026-W53", StickerAwards.isoWeek(LocalDate.of(2027, 1, 1)))
        assertEquals("2027-W01", StickerAwards.isoWeek(LocalDate.of(2027, 1, 4)))
    }

    @Test
    fun `an app sticker id names the receiver, the kind and the week`() {
        assertEquals("app_uB_on_time_2026-W43", StickerAwards.appStickerId("uB", StickerKind.ON_TIME, "2026-W43"))
    }

    @Test
    fun `on time means before the deadline, by the parent who was asked`() {
        val c = clock(answerBy = 1_000L)
        assertTrue(StickerAwards.answeredOnTime(c, "uB", 1_000L))
        assertFalse(StickerAwards.answeredOnTime(c, "uB", 1_001L), "an answer in the last chance is late")
        assertFalse(StickerAwards.answeredOnTime(c, "uA", 500L), "the proposer did not answer anything")
    }

    private fun runOf(uid: String, today: LocalDate, weeks: Int = StickerAwards.REGULAR_WEEKS) =
        (0 until weeks).map { onTime(uid, StickerAwards.isoWeek(today.minusWeeks(it.toLong()))) }

    @Test
    fun `regularity needs an on-time sticker in each of four weeks in a row`() {
        val today = LocalDate.of(2026, 10, 23)
        val run = runOf("uB", today)
        assertTrue(StickerAwards.regular(run, emptyList(), "uB", today, zone))
        assertFalse(StickerAwards.regular(run.dropLast(1), emptyList(), "uB", today, zone))
        val older = run.drop(1) + onTime("uB", StickerAwards.isoWeek(today.minusWeeks(4)))
        assertFalse(StickerAwards.regular(older, emptyList(), "uB", today, zone), "the run must end this week")
        assertFalse(StickerAwards.regular(runOf("uA", today), emptyList(), "uB", today, zone), "another parent's")
    }

    @Test
    fun `one run gives one regularity sticker, the next needs four new weeks`() {
        val today = LocalDate.of(2026, 10, 23)
        val given = Sticker(
            id = StickerAwards.appStickerId("uB", StickerKind.REGULAR, StickerAwards.isoWeek(today.minusWeeks(1))),
            toUid = "uB",
            fromUid = null,
            familyId = "",
            kind = StickerKind.REGULAR
        )
        val run = runOf("uB", today, weeks = 5)
        assertFalse(StickerAwards.regular(run + given, emptyList(), "uB", today, zone))
        val later = today.plusWeeks(4)
        val nextRun = runOf("uB", later) + run + given
        assertTrue(StickerAwards.regular(nextRun, emptyList(), "uB", later, zone))
    }

    @Test
    fun `a request left without an answer in the run breaks regularity, another parent's does not`() {
        val today = LocalDate.of(2026, 10, 23)
        val run = runOf("uB", today)
        val missed = clock(answerBy = millisOf(today.minusWeeks(2)), outcome = RequestClockOutcome.NO_ANSWER)
        assertFalse(StickerAwards.regular(run, listOf(missed), "uB", today, zone))
        assertTrue(StickerAwards.regular(run, listOf(missed.copy(responderUid = "uA")), "uB", today, zone))
        val beforeRun = missed.copy(answerByMillis = millisOf(today.minusWeeks(5)))
        assertTrue(StickerAwards.regular(run, listOf(beforeRun), "uB", today, zone))
    }

    @Test
    fun `on-time weeks are read off this parent's own sticker ids`() {
        val stickers = listOf(
            onTime("uB", "2026-W42"),
            onTime("uB", "2026-W43"),
            onTime("uA", "2026-W41"),
            Sticker("s1", "uB", "uA", "uA__uB", StickerKind.THANK_YOU)
        )
        assertEquals(setOf("2026-W42", "2026-W43"), StickerAwards.weeksOf(stickers, "uB", StickerKind.ON_TIME))
    }

    @Test
    fun `wire values read back, and an unknown one is nobody's guess`() {
        StickerKind.entries.forEach { assertEquals(it, StickerKind.fromWire(it.wire)) }
        ParentRole.entries.forEach { assertEquals(it, ParentRole.fromWire(it.wire)) }
        assertNull(StickerKind.fromWire("sarcasm"))
        assertNull(ParentRole.fromWire("father"))
        assertEquals(7, StickerKind.PARENT_KINDS.size)
        assertTrue(StickerKind.PARENT_KINDS.none { it.fromApp })
    }
}
