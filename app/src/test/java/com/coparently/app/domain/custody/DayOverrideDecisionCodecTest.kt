package com.coparently.app.domain.custody

import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * The wire form of an answer a swapped day had before it was offered again. `firestore.rules`
 * rebuilds the same string from the replaced entry (`dayOverrideAnswer`), so the field order is a
 * contract with the rules, not a detail.
 */
class DayOverrideDecisionCodecTest {

    private val accepted = DayOverride(
        toParent = "dad",
        requestedBy = "uid-dad",
        requestedAt = "2026-08-23T10:00:00",
        status = DayOverrideStatus.ACCEPTED,
        decidedBy = "uid-mom",
        decidedAt = "2026-08-23T11:00:00",
        note = "Lena's birthday",
        groupId = "g1"
    )

    @Test
    fun `an answer is written in the order the rules rebuild it`() {
        assertEquals(
            "d1|dad|uid-dad|2026-08-23T10:00:00|ACCEPTED|uid-mom|2026-08-23T11:00:00|g1",
            DayOverrideDecisionCodec.encode(DayOverrideDecisionCodec.of(accepted))
        )
    }

    @Test
    fun `missing fields are empty and read back as null`() {
        val olderBuild = accepted.copy(decidedAt = null, groupId = null)
        val wire = DayOverrideDecisionCodec.encode(DayOverrideDecisionCodec.of(olderBuild))

        assertEquals("d1|dad|uid-dad|2026-08-23T10:00:00|ACCEPTED|uid-mom||", wire)
        assertEquals(DayOverrideDecisionCodec.of(olderBuild), DayOverrideDecisionCodec.decode(wire))
    }

    @Test
    fun `what this build cannot read decodes to nothing`() {
        listOf(
            "d2|dad|uid-dad|2026-08-23T10:00:00|ACCEPTED|uid-mom||",
            "d1|dad|uid-dad|2026-08-23T10:00:00|ACCEPTED|uid-mom|",
            "d1|dad|uid-dad|2026-08-23T10:00:00|MAYBE|uid-mom||",
            "d1|dad|uid-dad|2026-08-23T10:00:00|PENDING|||",
            "d1||uid-dad|2026-08-23T10:00:00|ACCEPTED|uid-mom||",
            "d1|dad||2026-08-23T10:00:00|ACCEPTED|uid-mom||",
            ""
        ).forEach { assertNull(DayOverrideDecisionCodec.decode(it), it) }
    }

    @Test
    fun `the agreed slot is the entry's own once accepted, else the latest earlier acceptance`() {
        val answer = DayOverrideDecisionCodec.answerOf(accepted)
        val declinedChange = DayOverrideDecisionCodec.answerOf(
            accepted.copy(toParent = "mom", requestedBy = "uid-mom", status = DayOverrideStatus.DECLINED)
        )
        val change = accepted.copy(toParent = "mom", requestedBy = "uid-mom", status = DayOverrideStatus.PENDING)

        assertEquals("dad", accepted.agreedToParent)
        assertEquals("dad", change.copy(earlier = listOf(answer)).agreedToParent)
        assertEquals("dad", change.copy(earlier = listOf(answer, declinedChange, "garbage")).agreedToParent)
        assertEquals("mom", change.copy(status = DayOverrideStatus.ACCEPTED, earlier = listOf(answer)).agreedToParent)
        assertNull(change.agreedToParent)
        assertNull(change.copy(earlier = listOf(declinedChange)).agreedToParent)
    }
}
