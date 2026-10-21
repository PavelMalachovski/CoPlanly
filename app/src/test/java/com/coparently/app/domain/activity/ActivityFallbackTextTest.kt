package com.coparently.app.domain.activity

import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The sentence an un-upgraded phone sees.
 *
 * It is in the base locale on purpose and must stay predictable: it is the only thing a co-parent
 * whose build predates `MessageType.ACTIVITY` is shown, and an empty `content` there is an empty
 * bubble.
 */
class ActivityFallbackTextTest {

    private fun announcement(kind: ActivityKind, title: String) = ActivityAnnouncement(
        kind = kind,
        entityType = ActivityEntityType.EVENT,
        entityId = "e1",
        title = title
    )

    @Test
    fun `the fallback names what happened and to what`() {
        assertEquals(
            "New event: Football",
            ActivityFallbackText.of(announcement(ActivityKind.EVENT_CREATED, "Football"))
        )
    }

    @Test
    fun `an untitled entity still produces a sentence rather than a dangling colon`() {
        assertEquals(
            "Expense removed",
            ActivityFallbackText.of(announcement(ActivityKind.EXPENSE_DELETED, ""))
        )
    }

    @Test
    fun `the two request cards read as plain text on an older build`() {
        assertEquals(
            "Last chance to answer: Swimming",
            ActivityFallbackText.of(announcement(ActivityKind.REQUEST_LAST_CHANCE, "Swimming"))
        )
        // A proposal's card has no title of its own, and must not end in a dangling colon.
        assertEquals(
            "No answer",
            ActivityFallbackText.of(announcement(ActivityKind.REQUEST_UNANSWERED, ""))
        )
    }

    @Test
    fun `a split proposal states the proposer's own share in the first person when their slot is known`() {
        // The card carries slot 1's share; "I" is the sender, so the sentence reads their slot.
        val proposed = ActivityAnnouncement(
            kind = ActivityKind.SPLIT_PROPOSED,
            entityType = ActivityEntityType.SPLIT_PROPOSAL,
            entityId = "uA__uB",
            title = "",
            amount = "3500"
        )

        assertEquals("Proposed a new expense split: I pay 35 %, you pay 65 %", ActivityFallbackText.of(proposed, "mom"))
        assertEquals("Proposed a new expense split: I pay 65 %, you pay 35 %", ActivityFallbackText.of(proposed, "dad"))
        assertEquals(
            "Proposed a new expense split: I pay 33.33 %, you pay 66.67 %",
            ActivityFallbackText.of(proposed.copy(amount = "3333"), "mom"),
            "a share that is not whole keeps its fraction"
        )
        // Without the slot, or the share, nothing says whose share is whose: the plain sentence.
        assertEquals("Proposed a new expense split", ActivityFallbackText.of(proposed, null))
        assertEquals("Proposed a new expense split", ActivityFallbackText.of(proposed))
        assertEquals("Proposed a new expense split", ActivityFallbackText.of(proposed.copy(amount = null), "mom"))
        assertEquals("Proposed a new expense split", ActivityFallbackText.of(proposed.copy(amount = "12000"), "mom"))
    }

    @Test
    fun `a split's answers and its withdrawal read as plain sentences, whatever the slot`() {
        val expected = mapOf(
            ActivityKind.SPLIT_ACCEPTED to "Accepted the expense split",
            ActivityKind.SPLIT_DECLINED to "Declined the expense split",
            ActivityKind.SPLIT_WITHDRAWN to "Withdrew the expense split proposal"
        )
        expected.forEach { (kind, text) ->
            val card = ActivityAnnouncement(kind, ActivityEntityType.SPLIT_PROPOSAL, "uA__uB", "")
            assertEquals(text, ActivityFallbackText.of(card))
            assertEquals(text, ActivityFallbackText.of(card, "dad"))
        }
    }

    @Test
    fun `a withdrawn swap names its day and a withdrawn custody proposal reads plainly`() {
        assertEquals(
            "Day swap withdrawn: 2026-11-07",
            ActivityFallbackText.of(announcement(ActivityKind.DAY_SWAP_WITHDRAWN, "2026-11-07"))
        )
        assertEquals(
            "Custody proposal withdrawn: uA__uB",
            ActivityFallbackText.of(announcement(ActivityKind.CUSTODY_WITHDRAWN, "uA__uB"))
        )
    }

    @Test
    fun `every kind produces something, so no bubble is ever empty`() {
        ActivityKind.entries.forEach { kind ->
            val text = ActivityFallbackText.of(announcement(kind, "Thing"))
            assertTrue(text.isNotBlank(), "$kind produced nothing")
        }
    }

    @Test
    fun `no two kinds read the same, so the fallback is as specific as the card`() {
        val texts = ActivityKind.entries.map { ActivityFallbackText.of(announcement(it, "Thing")) }

        assertEquals(ActivityKind.entries.size, texts.toSet().size)
    }
}
